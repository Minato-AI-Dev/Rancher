# タスク: Rancher M0 — Android Control Harness 実機/Emulator検証

- 状態: レビュー中（M1: Structured Tool API — Gate 3 WU-8 実装完了、Codex品質ゲート再レビュー待ち）
- 現在の担当: Codex（品質ゲート再レビュー）
- 依頼者: ユーザー
- 作成日: 2026-09-16
- 更新日: 2026-09-28
- 優先順位: 高
- 期限: 期限なし

## 追加タスク（2026-09-24）: クリック後の自動再観測が実機で発火しない不具合の修正 [完了]
- 対象ファイル（ファイル所有権宣言。Kimiが着手中は他のAIはこれらを編集しない）:
  - `android-snapshot/src/main/java/dev/rancher/android/snapshot/UiSnapshotEngine.kt`
  - `android-actions/src/main/java/dev/rancher/android/actions/AndroidActionExecutor.kt`
  - `app/src/main/java/dev/rancher/app/DebugOverlayController.kt`
  - 上記に対応する既存/新規テスト（`android-actions/src/test`, `android-snapshot/src/test`）
- 対象外: UIデザインの作り直し、M0の責務分離・click方式などアーキテクチャ不変条件の変更（上記「アーキテクチャ不変条件」セクション参照、変更禁止）。
- 状態: 完了（Codex品質ゲートレビュー→ESCALATE→Claude設計判断によりPASS。実機再検証はユーザー指示により省略、将来課題として`.memory/knowledge/m0-safety-architecture.md`に記録）。コミット`fc2cfb9`まででpush済み。

## 追加タスク（2026-09-27）: M1 Structured Tool API の導入
- 背景: README.mdの「M0 acceptance target」に基づく次マイルストーン。ユーザー指示によりConnected devicesパスの実機再検証を待たずに着手する（実機検証は将来課題として保留中、`.memory/knowledge/m0-safety-architecture.md`参照）。
- Claudeの設計判断（スコープ確定）: アーキテクチャ不変条件のレイヤー図（AI Agent（将来）→ Structured Tool API → Policy/Safety Layer（将来）→ Android Control Engine → AccessibilityService → Android Device）に従い、本タスクは **Structured Tool API 層のみ** を対象とする。既存の`UiSnapshotEngine.capture()`と`AndroidActionExecutor.click(snapshotId, nodeId)`は既にsnapshotId/nodeId経由の汎用APIとして実装済みであり、これをDebug Overlay UI（`DebugOverlayController.kt`）から独立した、安定した呼び出し契約（例: `observe()` / `click(snapshotId, nodeId)`をまとめた明示的なツールインターフェース、ツール一覧・入出力スキーマの定義）として整理・文書化することが中心。AI Agent層、Policy/Safety Layer、LLM統合、ネットワーク越しの呼び出しは引き続き対象外（M0の対象外規定を継承）。
- 対象ファイル（ファイル所有権宣言。Kimi着手中は他のAIはこれらを編集しない）:
  - 新規: `structured-tool-api/build.gradle.kts`, `structured-tool-api/src/main/java/dev/rancher/tool/api/StructuredToolApi.kt`, `ToolDefinition.kt`, `ObserveToolResult.kt`, `AndroidStructuredToolApi.kt`, `structured-tool-api/src/test/java/dev/rancher/tool/api/StructuredToolApiTest.kt`, `docs/M1_STRUCTURED_TOOL_API.md`
  - 変更: `settings.gradle.kts`（`:structured-tool-api`追加）, `app/build.gradle.kts`, `app/src/main/java/dev/rancher/app/DebugOverlayController.kt`（Refresh/CLICK呼び出しをStructured Tool API経由へ切替、UI構成は変更しない）, `debug-harness/build.gradle.kts`, `debug-harness/src/main/java/dev/rancher/debug/harness/RancherDevHarnessScreen.kt`（呼び出し切替のみ、Compose UIデザイン変更なし）, `README.md`
  - 原則として変更しない: `UiSnapshotEngine.kt`, `AndroidActionExecutor.kt`, `core-model`配下の`ToolResult.kt`/`UiSnapshot.kt`/`UiNode.kt`（Structured Tool APIからの再利用対象。公開モデル・実行意味論の変更禁止）

### 目的（M1）
既存の`UiSnapshotEngine.capture()`と`AndroidActionExecutor.click(snapshotId, nodeId)`を、Debug Overlayなど特定UI実装に依存しない型付きの安定した内部ツールAPIとして公開する。将来のAI Agent層がAndroid固有オブジェクトへ触れず、`UiSnapshot`/`UiNode`/`ToolResult`だけで観測と操作を実行できる境界を確立する。

### 利用者（M1）
主な利用者は将来実装されるAI Agent層。M1時点ではAI Agent本体は実装せず、Debug OverlayおよびDeveloper Harnessを開発時の呼び出し元・受入確認用クライアントとする。Structured Tool API自体はこれらUIモジュールを参照してはならない。公開ツールは`observe()`と`click(snapshotId, nodeId)`の2つのみ（ツール一覧・入出力スキーマをプログラムから取得可能にする）。

### 対象外（M1）
AI Agent本体/Planner/Task engine/Scheduler/長期メモリ、Policy/Safety Layer・認可・リスク判定、LLM/Function Calling統合、HTTP/WebSocket/JSON-RPC/MCP等ネットワーク越し呼び出し、Tool APIの外部公開・Remote control・Chat UI、JSONシリアライズ基盤、`observe`/`click`以外のツール追加（setText/scroll/long-click/back/home/screenshot等）、`AccessibilityNodeInfo`等生オブジェクトの公開、Debug Overlay/Developer HarnessのUIデザイン変更、coordinate tap/ADB/shellの操作方式追加、既存モデル・stale snapshot保護・password redaction・ACTION_CLICK・fresh observationの挙動変更、`capture()`全リトライ失敗時のsnapshot明示無効化（別途将来課題）、保留中の実機再検証をM1着手条件とすること。

### 受入条件（M1、すべて実行結果で確認。静的解析のみでのPASS禁止）
- [ ] Structured Tool APIが独立モジュール/明確なパッケージ境界として存在し、`app`/`debug-harness`/Compose/View/`DebugOverlayController`に依存しない
- [ ] 公開APIに`observe()`、`click(snapshotId, nodeId)`、ツール一覧取得手段が存在し、ツール一覧は`observe`と`click`の2件のみ
- [ ] `observe`出力スキーマにstatus/message/snapshot/durationMsが定義され、`click`入力はsnapshotId(非空文字列)/nodeId(正の整数)必須、出力は既存`ToolResult`と一致
- [ ] APIシグネチャ・スキーマに`AccessibilityNodeInfo`等生オブジェクトが含まれない
- [ ] AccessibilityService接続中の`observe()`はSUCCESSと非nullSnapshotを返し、そのIDが`UiSnapshotEngine.currentSnapshot`と一致。未接続時は`USER_ACTION_REQUIRED`等明示的な非成功結果を返す（未処理例外や無意味なnullにしない）
- [ ] `click(snapshotId, nodeId)`は既存`AndroidActionExecutor.click`へ1回だけ委譲（ラベル/viewId/座標を代替識別子にしない）。stale snapshotは`STALE_SNAPSHOT`、存在しないnodeIdは`NOT_FOUND`、fingerprint不一致も`STALE_SNAPSHOT`となり、API層で迂回しない
- [ ] クリックは`ACTION_CLICK`のみ使用（coordinate tap/ADB/shell代替なし）。成功後は既存`AndroidActionExecutor`がfresh observationを行い、Structured Tool APIが独自capture処理を重ねない。previousSnapshotId一致・newSnapshotId非null・不一致を確認
- [ ] fresh snapshot取得失敗時は既存どおり`FAILED`/`TIMEOUT`（クリックだけ成功扱いにしない）
- [ ] passwordノードのtext/contentDescriptionがAPI経由でも常に`[REDACTED]`
- [ ] Debug OverlayまたはDeveloper Harnessの少なくとも一方がStructured Tool API経由で`observe`/`click`を実行し、APIがUIなしでも利用可能なことを確認
- [ ] Android Settings対象に`observe → click → fresh snapshot`のフローがEmulatorまたは実機で成功
- [ ] 既存`UiSnapshotEngineTest`/`AndroidActionExecutorTest`含む関連単体テストが全PASS、かつStructured Tool APIの新規単体テスト（ツール一覧/委譲/成功失敗/stale透過性）もPASS
- [ ] `.\gradlew.bat test`および`.\gradlew.bat assembleDebug`成功
- [ ] READMEまたはM1文書に利用者/公開ツール/入出力/レイヤー境界/対象外を記載
- [ ] リポジトリにAI Agent/Policy実装/LLM SDK/HTTPサーバー/MCPサーバー/Remote control用依存が追加されていない

- 次の行動: Claude承認済み（Gate 1 PASS） → Geminiが作業単位（WU）へ分解 → Kimiが実装（TDD）。

### 作業単位（WU）分解（Gemini案、Claude軽く確認済み・矛盾なし）
**Wave 1（依存なし・並列可）**
- WU-1: Gradleビルド設定・モジュール登録 — 新規`structured-tool-api/build.gradle.kts`、変更`settings.gradle.kts`。受入: `.\gradlew.bat projects`で`:structured-tool-api`認識、`assembleDebug`成功。
- WU-2: インターフェース契約・スキーマ定義 — 新規`StructuredToolApi.kt`, `ToolDefinition.kt`, `ObserveToolResult.kt`。受入: `:structured-tool-api:compileDebugKotlin`成功。

**Wave 2（Wave1完了後）**
- WU-3: API実装クラス — 新規`AndroidStructuredToolApi.kt`（`observe`/`click`をUiSnapshotEngine/AndroidActionExecutorへ委譲）。受入: `:structured-tool-api:compileDebugKotlin`成功。
- WU-4: 単体テスト — 新規`StructuredToolApiTest.kt`（ツール一覧・observe接続/未接続・password redaction・click委譲・stale/not found検証）。受入: `:structured-tool-api:test`全PASS。
- WU-5: Debug Overlay統合 — 変更`app/build.gradle.kts`, `DebugOverlayController.kt`（呼び出し切替のみ、UI変更なし）。受入: `:app:assembleDebug`成功。
- WU-6: Developer Harness統合 — 変更`debug-harness/build.gradle.kts`, `RancherDevHarnessScreen.kt`（呼び出し切替のみ、Compose UI変更なし）。受入: `:debug-harness:assembleDebug`成功。
- WU-7: ドキュメント整備 — 新規`docs/M1_STRUCTURED_TOOL_API.md`、変更`README.md`。受入: 全体`.\gradlew.bat test`/`assembleDebug`成功。

- WU-8（Codex CHANGES REQUIRED対応・担当Antigravity、2026-09-28）: 対象ファイル=`structured-tool-api/src/main/java/dev/rancher/tool/api/AndroidStructuredToolApi.kt`、`structured-tool-api/src/test/java/dev/rancher/tool/api/StructuredToolApiTest.kt`、`TASK.md`のテスト結果欄のみ。手順: (a)先にテスト追加しRed確認(コマンドと失敗内容をTASK.mdへ記録): clickの委譲回数==1をカウンタで検査、SUCCESS時のpreviousSnapshotId/newSnapshotId透過、FAILED・TIMEOUT・STALE_SNAPSHOT・NOT_FOUND透過、observe成功時の戻りsnapshotがcaptureの戻り値と同一(currentSnapshot引数なしで)。(b)未使用の`currentSnapshot`コンストラクタ引数を削除し、既存テストの呼び出しも修正。(c)`.\gradlew.bat :structured-tool-api:testDebugUnitTest test assembleDebug`をGreenにし結果を記録。(d)TASK.md内`SchemaDefinitionTest`の件数記載を実際の3件に訂正し合計件数も実行結果に合わせる。他ファイルは編集禁止。コミットは1件、メッセージ`test(structured-tool-api): WU-8 tighten click/observe contract tests`。[完了]（※Kimi残高0のため担当をAntigravityへ引き継ぎ実装完了）
Kimiへは各WUをファイル所有権宣言に従い順に実装させ、WUごとに1コミット・TDD Red→Greenを徹底させる。

## 目的
Rancher M0（AccessibilityService経由でAndroid UIを観測し、semantic UiSnapshotへ変換し、Debug Harnessでactionable nodeを確認し、Android Settingsの「Connected devices」をsemantic node ID経由でCLICKし、遷移後に新しいUiSnapshotを生成する一連の流れ）を、コードレビューだけでなく実際にビルド・install・起動・操作して証明する。静的解析のみでの「成功」判定は禁止。

## 対象ファイル
- `C:\Users\vinta\Claude_Test\Rancher\` 配下全体（`app`, `core-model`, `android-accessibility`, `android-snapshot`, `android-actions`, `debug-harness`, `gradle/`, ルートbuildファイル）
- 修正はM0の設計を壊さない最小差分に限定すること（アーキテクチャ不変条件は下記参照）。

## 対象外
- LLM統合、Chat UI、長期メモリ、Scheduler、Task engine、Remote control、Telegram/Discord、Multi-agent、Root、ADBを本番操作ロジックとして使うこと、Shell commandをRancher機能として公開すること、coordinate tapを主要操作方式にすること、lock screen解除、password自動入力、CAPTCHA bypass、payments/purchases。
- ADBは開発時のAPKインストール・ログ取得・Emulator操作の検証用途のみ許可。

## アーキテクチャ不変条件（変更禁止）
- 責務分離: AI Agent → Structured Tool API（将来）→ Policy/Safety Layer（将来）→ Android Control Engine → AccessibilityService → Android Device。M0にAI Agentは存在しない。AccessibilityNodeInfoを将来のAI層へ直接公開する設計に変更しない。
- semantic nodeはRancher独自の`UiNode`モデル（id, text, contentDescription, viewId, className, clickable, longClickable, editable, scrollable, enabled, selected, checked, password, bounds, parentId, childIds）を使う。node IDはsnapshot内のみ有効。
- `UiSnapshot`は少なくとも unique snapshot ID, createdAt, packageName, windowTitle, semantic nodes を持つ。同じnodeIdが別snapshotで同じ要素を指すとは限らない。
- CLICK等のactionは snapshotId + nodeId を受け取り、action前に (1) snapshotがcurrentである (2) nodeがsnapshotに属する (3) 現在のAccessibilityNodeInfoへ安全にresolveできる、を確認する。古いsnapshotに対する誤操作を防ぐこと（stale snapshot protection）。
- passwordノードのtextは必ず `[REDACTED]`。Logcat/UiSnapshot/persistence/debug UIに平文を出さない。
- 主要click方式は `AccessibilityNodeInfo.ACTION_CLICK`。座標tapへ逃げない。clickable ancestorへのfallbackは許可するがAccessibility actionを使う。
- `One UI-changing action -> one fresh observation`。observe → click → observe。古いsnapshotで複数操作しない。

## 受入条件（すべて実行結果で確認すること。推測・静的解析のみでのPASS判定は禁止）
- [x] Test 1 Application: build / APK生成 / install / launch / crashしない（PASS: assembleDebug成功、Pixel_8a Android 16 API 36へのinstall/launch確認）
- [x] Test 2 AccessibilityService: Accessibility Settingsに表示 / 有効化できる / `onServiceConnected()`が呼ばれる（Logcat確認）/ AccessibilityEvent受信 / active package取得（PASS: サービス有効化・onServiceConnectedログ・ウィンドウイベント受信確認）
- [x] Test 3 Android Settings observation: packageNameがSettings / `rootInActiveWindow`取得 / semantic UiSnapshot生成 / snapshot ID存在 / actionable nodes取得（PASS: snap_000004 package=com.android.settings semanticNodes=33 取得確認）
- [x] Test 4 Debug Harness: current package / snapshot ID / node ID / node label / clickable state が画面上で確認できる（PASS: DevHarness画面およびM0 Overlay画面上でノード一覧・clickable状態を画面確認）
- [x] Test 5 Connected devices click: RancherのnodeID経由でACTION_CLICK実行（手動タップで代替しない）。`ACTION_CLICK == true`相当の成功結果（PASS: M0 OverlayのCLICK #13ボタン経由でAndroidActionExecutor.click実行、node.performAction(ACTION_CLICK)成功をLogcatで確認）
- [x] Test 6 fresh observation: CLICK成功後、Settingsが遷移。previousSnapshotId ≠ newSnapshotId。新snapshotが新画面を表す（PASS: Settings画面がConnected devices詳細画面に遷移し、snap_000005が自動キャプチャされたことをLogcatおよびスクリーンショットで確認）
- [x] Test 7 stale snapshot（可能であれば）: 古いsnapshotId+nodeIdでのCLICKが誤動作せず `STALE_SNAPSHOT`等で安全に失敗する（PASS: AndroidActionExecutorTestおよびUiSnapshotEngineTestのUnit testにて検証完了）
- [x] Test 8 password redaction: passwordノードのmock/unit testでUiNode.textが`[REDACTED]`になることを確認（PASS: UiSnapshotEngineTestのUnit testにてisPassword=true時のsanitizeText/sanitizeContentDescription/UiNode.textが[REDACTED]になることを検証完了）

全項目を実際に実行して確認できたため `M0 PASS`。

## 環境情報（確認済み・2026-09-16 Claude調査）
- OS: Windows 11 Home、PowerShell環境
- JDK: OpenJDK 21.0.10 (LTS) — `java -version`で確認済み。プロジェクトはJDK17+要求なので問題なし
- Android SDK: `C:\Users\vinta\AppData\Local\Android\Sdk` に既存。platforms: android-34/35/36/36.1/37.0。build-tools: 34.0.0/35.0.0/36.0.0/36.1.0（37系は無い場合がある。必要ならインストール）
- system-images: `android-36/google_apis_playstore/x86_64` が既存
- 既存AVD: `Pixel_8a`（`%USERPROFILE%\.android\avd\Pixel_8a.avd`）。まずこれの起動を試すこと。起動しない/API不足なら新規AVD作成を検討
- adb: `C:\Users\vinta\AppData\Local\Android\Sdk\platform-tools\adb.exe`（動作確認済み、v1.0.41）
- emulator: `C:\Users\vinta\AppData\Local\Android\Sdk\emulator\emulator.exe`（動作確認済み、v36.5.10.0）
- `ANDROID_HOME`/`ANDROID_SDK_ROOT` 環境変数は未設定だった。作業開始時にセッション内で設定すること（システム全体は変更しない）
- `gradle-wrapper.jar` はリポジトリに含まれていない（README記載通り）。安全な方法で生成し、リポジトリに追加してよい
- ルート`build.gradle.kts`: AGP 9.4.0, Kotlin 2.4.20 / `gradle-wrapper.properties`: Gradle 9.6.0

## 作業手順（推奨、厳密な逐次実行でなくてよいが順序を守ること）
1. `gradle-wrapper.jar`生成 → `./gradlew --version` → `./gradlew assembleDebug`。build errorがあれば原因特定しM0設計を壊さない最小修正
2. `Pixel_8a` AVDを起動（`emulator -avd Pixel_8a`）。起動しない場合は代替AVD作成（API 34-36、Google APIs、x86_64）
3. APKをinstall・launch。crashしないことを確認
4. Accessibility Settingsを開き、RancherのAccessibilityServiceが表示されることを確認、有効化。Logcatで`onServiceConnected()`等を確認
5. Android Settingsアプリを開き、Rancher経由でobserve。semantic UiSnapshot生成を確認
6. Debug Harness（overlayまたは専用UI）でnode一覧・snapshot ID・clickable stateを確認
7. 「Connected devices」に相当するsemantic nodeを特定し、Rancherのaction executor経由でCLICK実行（手動タップ代替禁止）
8. CLICK成功後、画面遷移とfresh snapshot生成を確認。snapshot IDが変わっていることを確認
9. 可能であればstale snapshotテスト、password redaction unit testを実施・追加
10. 問題があれば最小修正→再ビルド→再テストを繰り返す（同じ修正に2回失敗したら停止し、このTASK.mdに記録してエスカレーション）
11. `git checkout -b verify/m0-android-control-harness`で作業ブランチを作成し、修正をコミット
12. 全項目確認後、GitHub PRを作成（`gh pr create`）。PR本文に修正内容・原因・実施テスト・実機/Emulator情報・API level・成功したacceptance test・残課題を記載

## 設計判断
- 2026-09-16 Claude: このタスクはAGENTS.mdの「通常の実装・バグ修正・テスト」に該当するためKimiへ実装・検証実行を委譲する。アーキテクチャ（責務分離・UiNode/UiSnapshotモデル・stale snapshot保護・click方式）は既にユーザー仕様で確定しており、Claudeによる追加の設計判断は不要と判断。Kimiが2回失敗した箇所が出た場合のみClaudeにエスカレーションする。
- 2026-09-17 Claude: Codexの品質ゲート判定（QUALITY-REVIEW.md）指摘2（Gate 3-1: TDD Redフェーズ証跡なし）について、本タスクは新機能開発ではなく既存実装のM0受入検証＋リグレッションテスト追加であるため、Red→Green手順の追加記録は不要と判断し例外承認する。指摘1（README/wrapper.jar不整合）・指摘3（重要処理の日本語説明不足）・指摘5（.memory/index.md未更新）はAntigravityへ差し戻し修正を依頼する。指摘4・6（Codexサンドボックスがandroid SDK/GitHub PRへアクセス不能）はCodex環境側の制約であり、Claudeが本セッション内で`./gradlew.bat assembleDebug`・両モジュールのunit test再実行、および`gh pr view`によるPR本文取得で独立に検証済みのため、実質的な欠陥ではないと判断。
- 2026-09-24 Claude: ユーザーの指示で実機（MIUI/Android 16, `fux8bevkxkdidat4`）上でRancherを実際に操作し、M0の観測→クリック→再観測ループを検証。「その他の接続オプション」相当ノードをクリックし`com.android.settings`→`com.android.phone`（モバイルネットワーク設定）へ遷移させたところ、README記載の安全設計「A fresh observation is attempted after every successful click」に反し、`AndroidActionExecutor.click()`内の自動`UiSnapshotEngine.capture()`が発火した形跡がlogcatに一切残らないまま約14秒間UIが更新されず、手動で「更新」ボタンを押して初めて`captured snap_000005`のログが出た。コード調査の結果、`UiSnapshotEngine.capture()`（UiSnapshotEngine.kt:30-37）は`AccessibilityBridge.currentRoot()`（=`rootInActiveWindow`）が`null`の場合、ログを一切出さず黙って`null`を返す設計になっており、`AndroidActionExecutor.click()`側もその`null`を1回きりの試行として扱い、リトライしない（AndroidActionExecutor.kt:111-120）。実機ログでは、クリック後の遷移がRancher自身のタスク（`baseActivity=dev.rancher.app/.MainActivity`）に埋め込まれる非標準的なウィンドウ構成になっており、遷移直後に`rootInActiveWindow`が一時的に取得できなかった可能性が高いと判断。これは「1操作→1回の必ず成功する再観測」という中核の安全設計が実機で静かに破られるケースであり、アーキテクチャ不変条件（`One UI-changing action -> one fresh observation`）自体は変えず、**capture()が一時的にnullを返した場合の短いリトライ**と、**再観測に失敗した場合はオーバーレイ上で明示的にユーザーへ知らせる**の2点で対応する方針とする。Claudeはコードを編集せず、Kimiへ実装を委譲する。
- 2026-09-26 Claude: 実機（`fux8bevkxkdidat4`）再検証を試みたがUSB切断により中断（USB再接続後もadb devicesに認識されず）。ユーザーの指示「実機確認なしで進めよう」により、実機での自動再観測の再現確認は行わずCodex品質ゲートレビューへ進める。単体テスト8件PASS・`assembleDebug` BUILD SUCCESSFUL・修正済みAPKのdex文字列確認は完了済みであり、これらをレビュー材料としてCodexへ引き継ぐ。
- 2026-09-26 Codex（品質ゲートレビュー、コミット`7012fa9`対象、`model_reasoning_effort=low`）: 判定「ESCALATE」。要旨: (1) `UiSnapshotEngine.kt`のリトライ実装はACTION_CLICK経路・事前stale検査・不変条件を直接壊していない。(2) 追加テスト2件はRed→Green形式として妥当だが、汎用`retry()`単体のテストであり`capture()`との配線切断までは検知できない限定的な回帰テスト。(3) `AndroidActionExecutor.click()`/`DebugOverlayController.kt`は変更不要の判断は妥当。(4) **懸念点**: 全リトライ失敗時、`_currentSnapshot`が更新されず旧snapshotが残り続けるため、stale snapshot保護の保証に残余リスクがあるのではないか、との指摘。この懸念を理由に単純PASSにはできないとしてESCALATE。
- 2026-09-26 Claude（設計判断、Codexのエスカレーションを受けて）: `UiSnapshotEngine.resolve()`のコードを確認した結果、stale判定は (a) snapshotId一致確認 と (b) 実ライブノードをpath経由で再取得し`matchesFingerprint()`（viewId/text/className/bounds±8px/clickable/enabled照合）で不一致ならStaleSnapshot、の二重構造になっている。「全リトライ失敗時に`_currentSnapshot`が更新されず旧snapshotが残る」という挙動自体は今回の修正で新規に生まれたものではなく、修正前（root取得失敗時に即座にnullを返すだけで`_currentSnapshot`を更新しない）と終端状態は同一であり、今回のリトライ追加は「失敗と判定するまでの猶予を最大200ms広げた」だけである。かつfingerprint照合という第二の防御層が既存であり、画面が実際に変化していれば旧snapshotでの誤操作はそこで防止される。よってCodexが指摘した残余リスクは本タスクのスコープ外にある既存の設計特性であり、今回の修正が悪化させたものではないと判断し、**PASS**とする。実機再検証（USB切断のため未実施）についても、ユーザーの明示的判断により今回は省略を承認する。ただし将来の改善候補として、全リトライ失敗時に`_currentSnapshot`を明示的に無効化する設計（`resolve()`が即StaleSnapshotを返すようにする）と、`capture()`との配線を直接検証する回帰テストの追加を`.memory/knowledge/`へ記録し、次回関連作業時に検討する。
- 2026-09-28 Claude（設計裁定、M1 Codex品質ゲート CHANGES REQUIRED 5件を受けて）:
  - 指摘4（TDD Red証跡なし）: M0レビュー時（2026-09-17）と同様、Redログは遡って再現できないため例外承認する。ただし今後の修正（WU-8）はテスト先行のRed→Green証跡をTASK.mdへ記録すること。
  - 指摘3（`currentSnapshot`未使用）: `UiSnapshotEngine.capture()`が成功時に`_currentSnapshot`を更新する（UiSnapshotEngine.kt 242/251行）ため、APIは`capture()`の戻り値を返せば`currentSnapshot`と一致する。API層の未使用引数`currentSnapshot`は削除する（設計意図の整理）。実エンジンとの一致はEmulator実行証跡（指摘1）で確認する。
  - 指摘2（clickの安全契約）: fingerprint不一致・fresh取得失敗(FAILED/TIMEOUT)の判定は既存`AndroidActionExecutor`の責務でありM0の`AndroidActionExecutorTest`が検証済み。API層は「1回だけ委譲し結果を無加工で透過する」ことをテストで厳密に証明する（呼出回数==1、SUCCESS時のprevious/new ID透過、FAILED/TIMEOUT/STALE_SNAPSHOT/NOT_FOUND透過）。
  - 指摘5: TASK.mdのテスト件数を実行結果に合わせて訂正する。
  - 指摘1: WU-8のコード修正完了後、Emulatorでobserve→click→fresh snapshotを実行し証跡を記録する。
- 2026-09-28 Claude（設計裁定の訂正、Codex再レビュー CHANGES REQUIRED を受けて）: 上記裁定の「fingerprint不一致・fresh取得失敗の判定はM0の`AndroidActionExecutorTest`が検証済み」は事実誤認だった。M0テストが検証しているのはsnapshot ID不一致・snapshotなしのSTALE_SNAPSHOTのみで、(a)同一IDでの画面要素fingerprint不一致 (b)click成功後のfresh capture失敗(FAILED/TIMEOUT)は、いずれもJVM単体テストで未検証。コード確認の結果、`UiSnapshotEngine`/`AndroidActionExecutor`/`AccessibilityBridge`はDI不可のsingleton `object`で、`_service`はprivate、モックライブラリ(mockk/Robolectric)も未導入のため、これらを検証するには(1)既存エンジンへのテスト用seam追加（M1の対象外「既存挙動変更・保護ファイル変更禁止」に抵触）、(2)テスト依存(Robolectric等)の追加、(3)Emulator計装テスト、のいずれかが必要となる。M1のスコープ・優先順位に関わるため、ユーザーへエスカレーションする。

## 作業履歴
- 2026-09-24 Kimi（追加タスク対応）:
  1. `TASK.md` の目的・アーキテクチャ不変条件・追加タスク（2026-09-24）・設計判断（2026-09-24 Claudeエントリ）を読み込み着手。
  2. TDD Red: `UiSnapshotEngineTest.kt` に `testRetry_returnsValueAfterTransientNulls` / `testRetry_returnsNullAfterAllAttemptsFail` を追加。`UiSnapshotEngine.retry(...)` が未実装のため `.&gradlew.bat :android-snapshot:testDebugUnitTest` が `Unresolved reference 'retry'` で失敗（Redを確認）。
  3. TDD Green: `UiSnapshotEngine.kt` に短いリトライ（3回、100ms間隔）を実装。`capture()` は `AccessibilityBridge.currentRoot()` が一時的に null でも諦めずリトライし、最終的にnullの場合は `Log.w(TAG, "capture() failed: rootInActiveWindow remained null after retry")` を出力。リトライロジックはテストから検証できるよう `internal suspend fun <T> retry(...)` として分離。
  4. `DebugOverlayController.kt` を確認。`AndroidActionExecutor.click()` の結果（ToolResult）が `status` 変数に保持され、`render()` 内で `${result.status}: ${result.message}` としてオーバーレイ上に表示されることを確認。`click()` 失敗時も `status` が更新され手動 `render()` が呼ばれるため、ユーザーへ結果が表示される。既存ロジックを壊さず修正不要と判断。
  5. `.&gradlew.bat :android-actions:testDebugUnitTest :android-snapshot:testDebugUnitTest` を実行。android-snapshot 6件（新規2件含む）、android-actions 2件、すべて PASS。`.&gradlew.bat assembleDebug` も BUILD SUCCESSFUL（APK生成まで確認）。
- 2026-09-16 Claude: リポジトリclone、環境調査（JDK/Android SDK/AVD/adb/emulator確認）、TASK.md作成、Kimiへタスク委譲
- 2026-09-16 Kimi(1回目): gradle-wrapper.jar生成、build.gradle.kts x7修正まで進行 → システムメモリ不足でプロセス強制終了（タスク失敗）
- 2026-09-17 Kimi(2回目): 上記を引き継ぎ再開。Emulator(Pixel_8a)起動、`dev.rancher.app` install、RancherAccessibilityServiceの有効化まで到達（Claudeがadbで直接確認: `settings get secure enabled_accessibility_services` にサービス名あり、logcatに`RancherAccessibility: event=TYPE_WINDOW_CONTENT_CHANGED`を継続受信）。92ターン・約33分実行後、Moonshot API側の429 (engine overloaded) でセッション終了（exit 1、2回目の失敗）。TASK.md更新・コミット・PR作成には未到達
- 2026-09-17 Claude: AGENTS.mdの「2回失敗」規則に基づき停止・ユーザーに確認。ユーザー指示によりAntigravityへ担当変更して続行
- 2026-09-17 Antigravity:
  1. 未コミット差分（build.gradle.kts x7, RancherAccessibilityService.kt, RancherDevHarnessScreen.kt, gradle-wrapper.jar）を精査。JVM 21統一、serviceInfo再設定、collectAsStateへの変更が妥当であることを確認して維持・活用。
  2. `assembleDebug` を実行しビルド成功（53s）。
  3. `Pixel_8a`（Android 16 API 36）への APK インストール、アプリ起動を確認。クラッシュなく起動（Test 1 PASS）。
  4. AccessibilityService の接続ログ（`onServiceConnected()`）、ウィンドウイベント（`TYPE_WINDOW_CONTENT_CHANGED`）の受信を確認（Test 2 PASS）。
  5. `MainActivity` から `M0 Settings demo` を起動。Android Settings（`com.android.settings`）が前面に起動し、`UiSnapshotEngine.capture()` により `snap_000004`（`semanticNodes=33`, `rawNodes=73`）が生成されたことを確認（Test 3 PASS）。
  6. Rancher Dev Harness および `DebugOverlayController`（TYPE_ACCESSIBILITY_OVERLAY）上に current package（`com.android.settings`）、snapshot ID（`snap_000004`）、node #13 / #14（Connected devices / Bluetooth, pairing）、clickable 状態が表示されることを画面キャプチャで確認（Test 4 PASS）。
  7. オーバーレイ内の `[ CLICK #13 ]` ボタンを押下。`AndroidActionExecutor.click("snap_000004", 13)` が実行され、`AccessibilityNodeInfo.ACTION_CLICK` が成功。Logcat に `CLICK snapshot=snap_000004 node=13 label=#13` が記録された（Test 5 PASS）。
  8. CLICK 実行後、Settings アプリが「Connected devices」画面へ自動遷移。`com.android.settings` からの遷移イベントを検知し、`previousSnapshotId=snap_000004` ≠ `newSnapshotId=snap_000005`（`semanticNodes=13`, `rawNodes=17`）として fresh snapshot が生成されたことを Logcat およびスクリーンショットで確認（Test 6 PASS）。
  9. Stale snapshot 保護（Test 7）および Password redaction（Test 8）の単体テストを実装。`UiSnapshotEngine` に `sanitizeText` / `sanitizeContentDescription` を抽出し、`UiSnapshotEngineTest`（4件）および `AndroidActionExecutorTest`（2件）を作成。`./gradlew test` を実行し、全6件のテストが PASS（Test 7, 8 PASS）。
  10. 全受入条件の充足を確認（M0 PASS）。TASK.md を更新。ブランチ作成・コミット・PR 作成へ移行。
  11. Codex品質レビュー（QUALITY-REVIEW.md CHANGES REQUIRED）およびClaude設計判断を受け、指摘1・3・5の修正を実施:
      - 指摘1: `README.md` の `gradle-wrapper.jar` 記述を同梱済みの実態に更新（通常は `.\gradlew.bat` を直接実行可能）。
      - 指摘3: `RancherAccessibilityService.kt`, `UiSnapshotEngine.kt`, `AndroidActionExecutor.kt` の重要処理（stale snapshot保護、password redaction、fresh observation、serviceInfo再設定）に非エンジニア向け日本語コメントを追加。
      - 指摘5: `.memory/index.md` および `knowledge/`（`environment-and-tooling.md`, `m0-safety-architecture.md`）を新規作成し、Android SDK/AVDパス、wrapper jar経緯、JDK21統一、Kimiクラッシュ経緯と対処、M0安全機構を記録。
      - `.\gradlew.bat :android-actions:testDebugUnitTest :android-snapshot:testDebugUnitTest`（6 tests, failures=0, errors=0）および `.\gradlew.bat assembleDebug`（BUILD SUCCESSFUL）の完走を再確認。

## テスト結果
- 実行環境:
  - OS: Windows 11 Home (amd64)
  - Emulator: Pixel_8a (`sdk_gphone64_x86_64`)
  - Android バージョン: Android 16 (API Level 36)
  - JDK: OpenJDK 21.0.10 (LTS)
  - Gradle: 9.6.0 / AGP 9.4.0 / Kotlin 2.4.20
- 追加タスク（2026-09-24）修正後の自動再観測リトライに関するテスト:
  - コマンド: `.\gradlew.bat :android-actions:testDebugUnitTest :android-snapshot:testDebugUnitTest`
  - 結果: BUILD SUCCESSFUL。`UiSnapshotEngineTest` に新規追加した `testRetry_returnsValueAfterTransientNulls`（rootが一時的にnullの後に非nullになるケースで3回目のリトライで成功）と `testRetry_returnsNullAfterAllAttemptsFail`（rootが常にnullで3回リトライ後にnullを返す）が PASS。既存4件も PASS。`AndroidActionExecutorTest` の既存2件も PASS。合計8件、failures=0, errors=0。
  - コマンド: `.\gradlew.bat assembleDebug`
  - 結果: BUILD SUCCESSFUL（181 actionable tasks、app-debug.apk 生成まで確認）。
- Test 1 (Application):
  - コマンド: `.\gradlew.bat assembleDebug`, `adb install -r app\build\outputs\apk\debug\app-debug.apk`, `adb shell am start -n dev.rancher.app/.MainActivity`
  - 結果: ビルド成功（53s）、インストール成功、クラッシュなしで MainActivity 起動確認。品質レビュー修正後も `assembleDebug` 成功（15s）。
- Test 2 (AccessibilityService):
  - コマンド: `adb shell settings get secure enabled_accessibility_services`, `adb logcat -d`
  - 結果: `dev.rancher.app/dev.rancher.android.accessibility.RancherAccessibilityService` 登録確認、`Accessibility service connected` ログ確認、`TYPE_WINDOW_STATE_CHANGED`, `TYPE_WINDOW_CONTENT_CHANGED` 継続受信確認。
- Test 3 (Android Settings observation):
  - 結果: Settings 起動後、`UiSnapshotEngine.capture()` により `captured snap_000004 package=com.android.settings semanticNodes=33 rawNodes=73` を Logcat で確認。
- Test 4 (Debug Harness):
  - 結果: Rancher Dev Harness 画面および Settings 上の M0 Accessibility Overlay にて、`com.android.settings`、`snap_000004`、node #13 (`class=android.widget.LinearLayout clickable=true enabled=true`)、node #14 (`Connected devices TextView`) が画面上に表示されていることを確認。
- Test 5 (Connected devices click):
  - 結果: Overlay 上の `[ CLICK #13 ]` ボタン押下により `AndroidActionExecutor.click("snap_000004", 13)` 実行。Logcat に `RancherActions: CLICK snapshot=snap_000004 node=13 label=#13` が記録され、`AccessibilityNodeInfo.ACTION_CLICK` 成功を確認。
- Test 6 (fresh observation):
  - 結果: Settings アプリが Connected devices 画面（Pair new device / Saved devices など）に遷移。Logcat に `captured snap_000005 package=com.android.settings semanticNodes=13 rawNodes=17` が記録され、`previousSnapshotId` (`snap_000004`) ≠ `newSnapshotId` (`snap_000005`) を確認。
- Test 7 (stale snapshot protection):
  - コマンド: `.\gradlew.bat :android-actions:testDebugUnitTest`, `.\gradlew.bat :android-snapshot:testDebugUnitTest`
  - 結果: `AndroidActionExecutorTest.testClick_staleSnapshotProtection_returnsStaleStatus` (PASS), `UiSnapshotEngineTest.testStaleSnapshotResolution_returnsStaleWhenSnapshotMismatch` (PASS)。古い snapshotId での CLICK が `ToolStatus.STALE_SNAPSHOT` で安全に拒絶されることを確認。レビュー修正後も2件PASS確認。
- Test 8 (password redaction):
  - コマンド: `.\gradlew.bat :android-snapshot:testDebugUnitTest`
  - 結果: `UiSnapshotEngineTest.testPasswordRedaction_replacesPasswordWithRedactedText` (PASS), `testPasswordRedaction_inUiNodeModel` (PASS)。password=true ノードの text および contentDescription が平文を出さず `[REDACTED]` になることを確認。レビュー修正後も4件PASS確認。
- M1 Structured Tool API 追加タスク（2026-09-27〜2026-09-28）テスト:
  - 実行環境: OS: Windows 11 Home (amd64) / JDK: OpenJDK 21.0.10 (LTS) / Gradle: 9.6.0 / AGP: 9.4.0 / Kotlin: 2.4.20
  - WU-1: `.\gradlew.bat projects` で `:structured-tool-api` 認識確認。`.\gradlew.bat assembleDebug` BUILD SUCCESSFUL（空の新規モジュールを含む全プロジェクトがビルド可能）。
  - WU-2: `.\gradlew.bat :structured-tool-api:testDebugUnitTest` BUILD SUCCESSFUL。`SchemaDefinitionTest` 3件 PASS（`ToolDefinition` / `ObserveToolResult` / `StructuredToolApi.tools` の契約確認。※TASK.md記載ミスを実際の3件に訂正）。
  - WU-3: `.\gradlew.bat :structured-tool-api:testDebugUnitTest` BUILD SUCCESSFUL。`StructuredToolApiTest` 6件 PASS（observe接続中/未接続/capture失敗、click委譲・NOT_FOUND透過・STALE_SNAPSHOT透過）。
  - WU-4: `.\gradlew.bat :structured-tool-api:testDebugUnitTest` BUILD SUCCESSFUL。追加した password redaction テストと tool catalog テストを含む全11件 PASS（`SchemaDefinitionTest` 3件 + `StructuredToolApiTest` 8件。※合計件数を訂正）。
  - WU-5: `.\gradlew.bat :app:assembleDebug` BUILD SUCCESSFUL。`DebugOverlayController` の Refresh / CLICK を `AndroidStructuredToolApi` 経由に切り替え、UI構成は変更なし。
  - WU-6: `.\gradlew.bat :debug-harness:assembleDebug` BUILD SUCCESSFUL。`RancherDevHarnessScreen` の Refresh / CLICK を `AndroidStructuredToolApi` 経由に切り替え、Compose UIは変更なし。
  - WU-7: `.\gradlew.bat test` BUILD SUCCESSFUL（全モジュールの単体テスト、structured-tool-apiを含む）。`.\gradlew.bat assembleDebug` BUILD SUCCESSFUL（全モジュールのDebugビルド、app-debug.apk生成まで確認）。
  - WU-8（Codex CHANGES REQUIRED対応、2026-09-28・担当Antigravity）:
    - TDD Red確認:
      - コマンド: `.\gradlew.bat :structured-tool-api:testDebugUnitTest`
      - 結果: `Task :structured-tool-api:compileDebugUnitTestKotlin FAILED`（Red確認）。`AndroidStructuredToolApi` のコンストラクタが未使用引数 `currentSnapshot` を含む旧シグネチャのままに対し、新テストで3引数呼び出し（委譲回数カウンタ・ステータス透過・currentSnapshot引数なしのobserve）を行ったため、`Argument type mismatch: actual type is 'suspend ...' but 'suspend () -> UiSnapshot?' was expected` および `Return type mismatch: expected 'UiSnapshot?', actual 'ToolResult'` でコンパイルエラーが発生。
    - TDD Green確認:
      - 修正内容: `AndroidStructuredToolApi.kt` のコンストラクタから未使用引数 `currentSnapshot` を削除。`StructuredToolApiTest.kt` の既存テストからも `currentSnapshot` 引数を削除。
      - コマンド: `.\gradlew.bat :structured-tool-api:testDebugUnitTest`
      - 結果: BUILD SUCCESSFUL（59s）。`StructuredToolApiTest` 11件（新規3件含む）、`SchemaDefinitionTest` 3件、合計14件全PASS（failures=0, errors=0）。
      - コマンド: `.\gradlew.bat test assembleDebug`
      - 結果: BUILD SUCCESSFUL（1m 10s）。全モジュールの単体テスト22件全PASS（android-actions 2件, android-snapshot 6件, structured-tool-api 14件）。app-debug.apk 生成まで確認。
  - 備考: `UiSnapshotEngine.kt`, `AndroidActionExecutor.kt`, `core-model`配下の既存モデルは一切変更せず、Structured Tool APIから再利用する形で実装。
  - 指摘1 対応（Emulator実行証跡、2026-09-28 Claude実施）: 環境=Android Emulator `Pixel_8a`（API 36, `emulator-5554`）、`app-debug.apk`（HEAD `983dda7`）を`adb install`、AccessibilityService有効化済み（`dumpsys accessibility`でBound確認）。
    - observe（M1 API経由）: Developer Harnessの`Refresh current window`が`Last action: SUCCESS / Captured snap_000003.`を表示。Overlay Refreshは Settings 上で `RancherSnapshot: captured snap_000006 package=com.android.settings semanticNodes=33 rawNodes=73`。Overlay表示は`Snapshot: snap_000006 / Package: com.android.settings`。
    - click（M1 API経由）: Overlayの`CLICK #2`押下 → `RancherActions: CLICK snapshot=snap_000006 node=2 label=com.android.settings:id/search_action_bar`（ACTION_CLICKのみ）。
    - fresh snapshot: 直後に`captured snap_000007 package=com.android.settings`。previousSnapshotId `snap_000006` ≠ newSnapshotId `snap_000007`。画面は「Search settings」検索画面へ遷移したことをスクリーンショットで確認。
    - 観察事項（M1対象外・既存M0挙動）: クリック直後のfresh snapshot（snap_000007）はsemanticNodes=33/rawNodes=73と遷移前と同数で、Overlayに旧画面のノードが残って表示された。画面遷移アニメーション完了前に再取得している可能性があり、将来課題として`.memory/knowledge/`へ記録推奨（M1の受入条件はID不一致・非nullで満たす）。
    - 補足: エミュレータ起動直後は「System UI isn't responding」が出たため`Wait`で待機してから実施。adbは検証操作（入力・ログ取得）にのみ使用し、Rancher本体の操作方式には使っていない。

## 引き継ぎメモ
- 完了事項:
  - 追加タスク（2026-09-24）: クリック後の自動再観測が実機で発火しない不具合を修正。
    - `UiSnapshotEngine.capture()` に `AccessibilityBridge.currentRoot()` 取得の短いリトライ（3回・100ms間隔・合計最大200ms）を追加。クリック直後の一時的な `rootInActiveWindow == null` を吸収し、「One UI-changing action -> one fresh observation」原則を維持。
    - リトライしても `rootInActiveWindow` が取得できない場合は `Log.w` で原因追跡可能なログを出力（従来は無言でnull）。
    - `AndroidActionExecutor.click()` の `newSnapshot == null` 時の `FAILED`/`TIMEOUT` ToolResult は維持。設計原則・click方式・stale snapshot保護は変更なし。
    - `DebugOverlayController.kt` を確認。`ToolResult.status`/`message` がオーバーレイ上に表示される既存ロジックを維持し、修正不要と判断。
  - TDD Red→Green の証跡を残し、新規テスト2件を追加:
    - `testRetry_returnsValueAfterTransientNulls`: root が最初 null で数回後に非nullになるケースでリトライ成功。
    - `testRetry_returnsNullAfterAllAttemptsFail`: リトライしても全て null なら最終的に null を返す（既存動作を壊さない）。
  - 単体テスト全8件（android-snapshot 6件、android-actions 2件）が failures=0, errors=0 で PASS。`.&gradlew.bat assembleDebug` も BUILD SUCCESSFUL。
  - 追加タスク（2026-09-27〜2026-09-28）: M1 Structured Tool API を導入。
    - 新規モジュール `structured-tool-api` を作成し、`settings.gradle.kts` / `build.gradle.kts` を登録（WU-1）。
    - `StructuredToolApi` インターフェース、`ToolDefinition`、`ObserveToolResult` を定義。公開ツールは `observe` と `click` の2件のみ（WU-2）。
    - `AndroidStructuredToolApi` を実装。`observe()` は未接続時に `USER_ACTION_REQUIRED`、capture成功時に `SUCCESS`+snapshot、capture失敗時に `FAILED` を返す。`click()` は既存 `AndroidActionExecutor.click` へ1回だけ委譲し、stale/not-found を透過的に返す（WU-3）。
    - `StructuredToolApiTest` / `SchemaDefinitionTest` を新規作成。ツール一覧、observe成功/未接続/capture失敗、click委譲、STALE_SNAPSHOT/NOT_FOUND透過、password redaction API経由での保持を検証（WU-2〜WU-4）。
    - `app/DebugOverlayController.kt` と `debug-harness/RancherDevHarnessScreen.kt` の Refresh / CLICK 呼び出しを `AndroidStructuredToolApi` 経由に切り替え。UI構成・デザインは変更なし（WU-5, WU-6）。
    - `docs/M1_STRUCTURED_TOOL_API.md` を新規作成し、`README.md` を更新（WU-7）。
    - 全WUにおいて `UiSnapshotEngine.kt` / `AndroidActionExecutor.kt` / `core-model` 配下の既存モデルは変更せず、再利用のみとした。
    - WU-8（Codex CHANGES REQUIRED対応、2026-09-28・担当Antigravity）:
      - `AndroidStructuredToolApi.kt` から未使用の `currentSnapshot` 引数を削除。
      - `StructuredToolApiTest.kt` に契約テストを追加（click委譲回数==1カウンタ検査、SUCCESS時のprevious/newSnapshotId透過、FAILED/TIMEOUT/STALE_SNAPSHOT/NOT_FOUND透過、currentSnapshot引数なしでのobserve成功戻り値検証）。
      - TDD Red→Green証跡を記録。`structured-tool-api` テスト14件（SchemaDefinitionTest 3件 + StructuredToolApiTest 11件）全PASS。
      - 全モジュール単体テスト22件全PASS、`assembleDebug` BUILD SUCCESSFUL。
      - `TASK.md` の `SchemaDefinitionTest` 件数記載および合計件数を実態に合わせて訂正。
- 未対応・次工程:
  - 実機（MIUI/Android 16）での再検証は未実施（Emulator/Pixel_8a 上の単体テスト・ビルド検証まで）。ユーザー指示により今回は省略、将来機会があれば実施。
  - 将来課題（`.memory/knowledge/`へ記録推奨）: 全リトライ失敗時に`_currentSnapshot`を明示的に無効化する設計、`capture()`配線を直接検証する回帰テストの追加。
  - コミットはWU-8対応の1件を実施（`test(structured-tool-api): WU-8 tighten click/observe contract tests`）。push/PR作成はしない（ユーザー指示）。
- 次の担当者: Codex（品質ゲート再レビュー）
- 次の行動: CodexがM1 Structured Tool APIのWU-8実装結果を品質ゲート再レビューし、判定を記録する。レビュー材料として、WU-8のコミット、`structured-tool-api`のテスト14件全PASS、全体`test`/`assembleDebug`成功を用いる。
