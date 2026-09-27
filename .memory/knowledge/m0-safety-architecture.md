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

## 4. capture() の一時的null吸収リトライと既知の残余リスク（2026-09-24追加、2026-09-26レビュー確定）

- **背景・目的**:
  - 実機（MIUI/Android 16）でクリック直後、画面遷移が完了するまでの一瞬 `rootInActiveWindow` が `null` を返すことがあり、`capture()` が黙って `null` を返してしまうと「1操作→1再観測」の原則が実機で静かに破られる。
- **実装メカニズム**:
  - `UiSnapshotEngine.capture()` は `AccessibilityBridge.currentRoot()` の取得を `retryForRoot()`（内部で汎用 `internal suspend fun <T> retry(attempts, delayMs, block)` を使用、3回・100ms間隔、最大約200ms）でリトライする。
  - 全リトライ失敗時のみ `Log.w` で原因追跡可能なログを出して `null` を返す（従来は無言でnull）。
- **既知の残余リスク（対応不要と判断した理由）**:
  - 全リトライ失敗時、`_currentSnapshot` は更新されず旧snapshotが残ったままになる。Codexの品質ゲートレビュー（2026-09-26, コミット`7012fa9`対象）はこれを理由に一度 ESCALATE 判定を出した。
  - Claudeの設計判断: この挙動（失敗時に`_currentSnapshot`を更新しない）は今回の修正で新規に生まれたものではなく、修正前から同一の終端状態である（今回の変更は「失敗と判定するまでの猶予を最大200ms広げた」だけ）。さらに `resolve()` のfingerprint照合（本ファイル「1. Stale Snapshot 保護」参照）が第二の防御層として既に存在し、画面が実際に変化していれば旧snapshotでの誤操作はそこで防止される。よってスコープ外の既存設計特性としてPASS扱いとした。
  - **将来の改善候補**（次にこの領域を触るときに検討）: 全リトライ失敗時に `_currentSnapshot` を明示的に無効化し `resolve()` が即座に `StaleSnapshot` を返すようにする設計、および `retry()` 単体テストだけでなく `capture()` との配線を直接検証する回帰テストの追加。
  - 実機（`fux8bevkxkdidat4`, MIUI/Android 16）でのクリック→自動再観測の再現確認は、USB切断のためこの修正では未実施（ユーザー判断により省略）。次回実機に触れる機会があれば優先的に検証すること。

## 5. serviceInfo の動的再設定によるアクセシビリティ接続安定化

- **背景・目的**:
  - 一部の Android バージョンやエミュレータ（API 36等）において、マニフェストや XML メタデータ定義のみではアクセシビリティサービスのバインドが外れたり、イベントが正しく通知されない事象が発生する。
- **実装メカニズム**:
  - `RancherAccessibilityService.onServiceConnected()` 内で、プログラムから明示的に `serviceInfo` を再設定（`eventTypes`, `feedbackType`, `flags`, `notificationTimeout`）。
  - これにより、エミュレータ起動直後から `TYPE_WINDOW_CONTENT_CHANGED` などのイベントを漏れなく確実に補足できる。
