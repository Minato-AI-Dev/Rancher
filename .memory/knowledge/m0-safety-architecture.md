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
