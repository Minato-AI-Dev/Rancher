# ナレッジ: Rancher M0 安全性アーキテクチャと不変条件

## 1. Stale Snapshot 保護（古い画面情報に対する誤操作防止）

- **背景・目的**:
  - モバイル画面は非同期に切り替わる（ユーザーの操作、ダイアログ表示、バックグラウンド処理の完了など）。
  - AIや自動実行エンジンが「過去のスナップショット」に基づいてタップを要求した場合、別のボタンや危険な操作を誤タップする恐れがある。
- **実装メカニズム**:
  - `UiSnapshot` は一意な ID（例: `snap_000004`）を持ち、各ノード ID はそのスナップショット内でのみ有効。
  - `AndroidActionExecutor.click(snapshotId, nodeId)` は、開始時に引数の `snapshotId` が最新の `currentSnapshot.id` と一致するかを検証。
  - さらに `UiSnapshotEngine.resolve` により、現在の画面ツリー（`AccessibilityNodeInfo`）を辿って対象ノードのフィンガープリント（`viewId`, `text`, `contentDescription`, `bounds`, `className`, `clickable` 等）がキャプチャ時と一致するか厳格に照合。
  - いずれかが不一致の場合は操作を実行せず、`ToolStatus.STALE_SNAPSHOT` を返して即座に遮断する。

## 2. Password Redaction（機密情報のマスキング）

- **背景・目的**:
  - パスワード入力欄などの機密テキストがログ、画面スナップショット、将来のAIプロンプト、デバッグUIへ生データのまま漏洩することを防止する。
- **実装メカニズム**:
  - `UiSnapshotEngine` のノード抽出時、ノードの `isPassword == true` の場合は `sanitizeText` および `sanitizeContentDescription` を通じて無条件に `[REDACTED]` へ置換。
  - 平文テキストはモデル（`UiNode.text`, `UiNode.contentDescription`）やログ出力（Logcat）に一切残らない。

## 3. Fresh Observation（1操作1観測の原則）

- **背景・目的**:
  - 1つの操作によって画面がどのように変化したかを確認せずに次の操作を行うと、操作の成否判定や連続タップによる暴走が発生する。
- **実装メカニズム**:
  - 原則: **One UI-changing action -> one fresh observation**。
  - `AndroidActionExecutor.click` は、クリック実行後にターゲットアプリからの UI 変化イベント（`TYPE_WINDOW_STATE_CHANGED`, `TYPE_WINDOW_CONTENT_CHANGED` 等）を待機（最大1,800ms）。
  - UI 描画の安定（140ms delay）を待った後、自動的に `UiSnapshotEngine.capture()` を呼び出して新しいスナップショット（`previousSnapshotId` ≠ `newSnapshotId`）を生成・提供する。

## 4. serviceInfo の動的再設定によるアクセシビリティ接続安定化

- **背景・目的**:
  - 一部の Android バージョンやエミュレータ（API 36等）において、マニフェストや XML メタデータ定義のみではアクセシビリティサービスのバインドが外れたり、イベントが正しく通知されない事象が発生する。
- **実装メカニズム**:
  - `RancherAccessibilityService.onServiceConnected()` 内で、プログラムから明示的に `serviceInfo` を再設定（`eventTypes`, `feedbackType`, `flags`, `notificationTimeout`）。
  - これにより、エミュレータ起動直後から `TYPE_WINDOW_CONTENT_CHANGED` などのイベントを漏れなく確実に補足できる。

## 5. サービス再接続時の状態リセット（"resync on reconnect"パターン）

- **背景・目的（2026-09-20 実機（Xiaomi/MIUI, Android 16）検証で発見）**:
  - MIUIなど一部のROMは、バックグラウンドで`RancherAccessibilityService`を定期的に破棄・再生成する（通常のOS挙動。`adb shell uiautomator dump`によるUiAutomation経由の一時的な再バインドとは別原因）。
  - service破棄時、システムが追加していた`TYPE_ACCESSIBILITY_OVERLAY`ウィンドウも道連れに破棄されるが、`DebugOverlayController`のようにサービスの生存期間より長く生きるシングルトン状態（`rootView`など）を持つコンポーネントは、この破棄を検知しないと「もう表示されているはず」という誤った内部状態のまま固まってしまう。
  - 実際に`rootView != null`ガードにより、以後`show()`を呼んでも永久に無反応になる不具合が発生した（`DebugOverlayController.kt`、修正コミット`30571b9`）。
- **実装メカニズム（修正後）**:
  - `DebugOverlayController`が`AccessibilityBridge.service`を監視し、`null`になった（＝serviceが破棄された）タイミングで自ら`hide()`を呼び、`rootView`/`windowManager`/`scope`をリセットする。
  - これにより、serviceが再生成された後の次回`show()`呼び出しが正しく新しいoverlayを追加できる。
- **M1以降への教訓**:
  - サービスの生存期間より長く生きる可能性のあるシングルトン/静的状態を持つコンポーネントは、すべて同じ「serviceがnullになったら自分の状態もリセットする」パターン（resync on reconnect）に従うべき。`AccessibilityBridge`・`UiSnapshotEngine`のような既存シングルトンも将来拡張する際はこの前提を意識すること。
  - Emulator（Pixel_8a）だけの検証ではこの種の「バックグラウンドでのサービス強制終了」系の不具合は再現しなかった。実機（特にMIUIなどOEMが独自の省電力/バックグラウンド管理を行うROM）での検証が必要な理由の実例。
