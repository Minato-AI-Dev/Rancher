# タスク: Rancher M0 — Android Control Harness 実機/Emulator検証

- 状態: レビュー中（自動再観測バグの修正実装・単体テスト完了。Codex品質ゲートレビュー待ち）
- 現在の担当: Kimi（実装完了）→ Codex（品質ゲートレビュー）
- 依頼者: ユーザー
- 作成日: 2026-09-16
- 更新日: 2026-09-24
- 優先順位: 高
- 期限: 期限なし

## 追加タスク（2026-09-24）: クリック後の自動再観測が実機で発火しない不具合の修正
- 対象ファイル（ファイル所有権宣言。Kimiが着手中は他のAIはこれらを編集しない）:
  - `android-snapshot/src/main/java/dev/rancher/android/snapshot/UiSnapshotEngine.kt`
  - `android-actions/src/main/java/dev/rancher/android/actions/AndroidActionExecutor.kt`
  - `app/src/main/java/dev/rancher/app/DebugOverlayController.kt`
  - 上記に対応する既存/新規テスト（`android-actions/src/test`, `android-snapshot/src/test`）
- 対象外: UIデザインの作り直し、M0の責務分離・click方式などアーキテクチャ不変条件の変更（上記「アーキテクチャ不変条件」セクション参照、変更禁止）。

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
- 未対応・次工程:
  - 実機（MIUI/Android 16）での再検証は未実施（Emulator/Pixel_8a 上の単体テスト・ビルド検証まで）。
  - コミットは未実施（各自の判断で可）。push/PR作成はしない（ユーザー指示）。
- 次の担当者: Codex（品質ゲートレビュー）
- 次の行動: `QUALITY-REVIEW.md` または Gate 3/Gate 4 相当のレビューを実施し、変更ファイル（`UiSnapshotEngine.kt`, `UiSnapshotEngineTest.kt`）と TDD 証跡・テスト結果を確認の上 PASS/CHANGES REQUIRED/ESCALATE を記録。必要に応じてユーザーへ実機再検証を依頼。
