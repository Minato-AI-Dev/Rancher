# Rancher 永続知見インデックス (.memory/index.md)

このディレクトリは、Rancherプロジェクトにおけるアーキテクチャ設計判断、検証で得た技術知見、環境構築手順などの再利用可能な知見を永続的に記録・蓄積する場所である（`AGENTS.md` Gate 5-2 準拠）。

---

## 1. Rancher M0 検証で得た主要知見サマリー

### ① Android SDK / AVD 既存環境の場所
- **SDK ルート**: `C:\Users\vinta\AppData\Local\Android\Sdk`
- **既存 AVD**: `Pixel_8a` (`%USERPROFILE%\.android\avd\Pixel_8a.avd`, Android 16 / API 36, x86_64)
- **主要ツール**:
  - `adb`: `C:\Users\vinta\AppData\Local\Android\Sdk\platform-tools\adb.exe`
  - `emulator`: `C:\Users\vinta\AppData\Local\Android\Sdk\emulator\emulator.exe`
- **注意点**: Windows全体で `ANDROID_HOME` が未定義の場合があるため、PowerShell作業セッション内で `$env:ANDROID_HOME = "C:\Users\vinta\AppData\Local\Android\Sdk"` を設定して作業する。
- 詳細は [knowledge/environment-and-tooling.md](knowledge/environment-and-tooling.md) を参照。

### ② gradle-wrapper.jar 欠如への対処
- **経緯**: 初期リポジトリに `gradle/wrapper/gradle-wrapper.jar` が同梱されておらずビルド不可となっていた。
- **対処**: Gradle 9.6.0 の wrapper jar を生成してリポジトリに正式コミット済み。
- **現状**: クリーンチェックアウト後、追加手順なしで直接 `.\gradlew.bat` (または `./gradlew`) を実行可能。

### ③ JDK 21 (JVM Toolchain) 統一の必要性
- **経緯**: AGP 9.4.0 / Kotlin 2.4.20 環境下で、各サブモジュールの JVM target 設定が 17 と 21 で混在しビルド不整合が発生した。
- **対処**: 全モジュール（`app`, `core-model`, `android-accessibility`, `android-snapshot`, `android-actions`, `debug-harness`）の `build.gradle.kts` を Java 21 (`jvmToolchain(21)` / `jvmTarget = "21"`) に統一。安定ビルドを確立。

### ④ メモリ不足による Kimi クラッシュの経緯と対処
- **経緯**:
  - 1回目 (2026-09-16): GradleビルドとEmulator起動が重なり、ホストマシンのメモリ枯渇によりプロセス強制終了。
  - 2回目 (2026-09-17): 92ターン（33分）のコンテキスト肥大化とMoonshot API 429過負荷でセッション中断。
- **再発防止策**:
  - 1回のセッションで全工程を抱え込まず、適正なサブタスク単位でコミット・記録を行う。
  - Gradle Daemonやメモリ高消費プロセスの定期的な整理・管理。
  - AGENTS.mdの「2回失敗」ルールに従い、速やかにClaudeおよび並列担当（Antigravity）へ引き継ぎを行って解決。

---

## 2. M0 コアアーキテクチャと安全性不変条件

- **Stale Snapshot 保護**:
  - ノード操作時に snapshot ID および画面上の要素フィンガープリントを検証し、画面遷移後の古い要素に対する誤操作（stale click）を `STALE_SNAPSHOT` で遮断。
- **Password Redaction**:
  - `isPassword == true` のノードではテキストや説明文を無条件で `[REDACTED]` に置換。平文ログ出力・データ漏洩を防止。
- **Fresh Observation**:
  - 「One action -> one fresh observation」の徹底。クリック実行後に対象アプリのUI変化イベントを待機し、画面安定後に必ず新しい `UiSnapshot` を生成。
- **serviceInfo 動的再設定**:
  - エミュレータ等の接続不安定を防止するため、`onServiceConnected` 時にコードから監視設定（イベント種別、フラグ）を明示的に再適用。
- 詳細は [knowledge/m0-safety-architecture.md](knowledge/m0-safety-architecture.md) を参照。

---

## 3. ナレッジファイル構成

- [knowledge/environment-and-tooling.md](knowledge/environment-and-tooling.md): SDK、AVD、Gradle、Java、実行環境知見
- [knowledge/m0-safety-architecture.md](knowledge/m0-safety-architecture.md): セーフティ機構、不変条件、アクセシビリティ知見（§5に実機/MIUI特有の知見あり）

---

## 4. 実機（Xiaomi/MIUI）検証で得た追加知見（2026-09-20, PR #3）

- **検証環境**: Xiaomi実機（モデル`25080RABDR`、コードネーム`lapis`）、Android 16 / API 36、arm64-v8a、MIUI。
- **重要な発見**: MIUIはバックグラウンドで`RancherAccessibilityService`を定期的に破棄・再生成する。この際、serviceより長く生きるシングルトン状態（`DebugOverlayController.rootView`）が破棄を検知せず、overlayデモが永久に無反応になるバグを発見・修正した。詳細は[knowledge/m0-safety-architecture.md §5](knowledge/m0-safety-architecture.md)を参照。
- **OEM差異**: 標準Android/Pixelの「Connected devices」に相当する項目が、このROMでは「Bluetooth」/「Interconnectivity」という異なるラベル・グルーピングで表示される。semantic nodeの特定はラベル文字列ではなく実際の画面構造を都度確認する必要がある。
- **PR状況（2026-09-22時点）**: 上記修正はPR #3（`verify/m0-android-control-harness-real-device`ブランチ、コミット`30571b9`）としてOPENだが**未マージ**。Codexの品質ゲート再判定が未実施。マージ前提の作業をこの上に積まないこと。
