# ナレッジ: 環境構築・ツールチェーン知見 (Rancher M0検証)

## 1. Android SDK / AVD 既存環境の場所と設定

- **Android SDK ルート**: `C:\Users\vinta\AppData\Local\Android\Sdk`
  - platforms: `android-34`, `android-35`, `android-36`, `android-36.1`, `android-37.0`
  - build-tools: `34.0.0`, `35.0.0`, `36.0.0`, `36.1.0`
  - system-images: `android-36/google_apis_playstore/x86_64`
- **既存 AVD**: `Pixel_8a`（`%USERPROFILE%\.android\avd\Pixel_8a.avd`）
  - Android 16 (API 36, x86_64)
- **関連ツールパス**:
  - `adb`: `C:\Users\vinta\AppData\Local\Android\Sdk\platform-tools\adb.exe`
  - `emulator`: `C:\Users\vinta\AppData\Local\Android\Sdk\emulator\emulator.exe`
- **環境変数の扱い**:
  - Windowsシステム全体に `ANDROID_HOME` / `ANDROID_SDK_ROOT` が設定されていない場合がある。
  - 必要に応じて PowerShell セッション内で `$env:ANDROID_HOME = "C:\Users\vinta\AppData\Local\Android\Sdk"` を指定して実行する（システム全体は汚染しない）。

## 2. gradle-wrapper.jar 欠如への対処

- **背景**:
  - 初期リポジトリおよびソース配布物にはバイナリの `gradle/wrapper/gradle-wrapper.jar` が含まれておらず、初期状態のまま `./gradlew` を叩くと実行に失敗していた。
- **対処と現状**:
  - 検証初期フェーズで安全な環境から Gradle 9.6.0 の `gradle-wrapper.jar` を生成・配置し、リポジトリに正式に同梱した。
  - 現在は初回チェックアウト後、追加の wrapper 生成なしで `./gradlew`（Windowsでは `.\gradlew.bat`）を直接実行可能。

## 3. JDK 21 (JVM Toolchain) 統一の必要性

- **背景**:
  - AGP (Android Gradle Plugin) 9.4.0 および Kotlin 2.4.20 を利用する環境において、各サブモジュールの JVM target 設定が Java 17 と Java 21 で混在していた。
  - ホスト環境の JDK は OpenJDK 21.0.10 (LTS) であり、一部モジュールが古い target 設定のままだとコンパイル時やツール連携時に不整合エラーが発生する。
- **対処**:
  - ルートおよび全サブモジュール（`app`, `core-model`, `android-accessibility`, `android-snapshot`, `android-actions`, `debug-harness`）の `build.gradle.kts` において、`jvmToolchain(21)` または `JavaVersion.VERSION_21` / `jvmTarget = "21"` に完全統一。
  - これにより警告や不整合なしで `assembleDebug` および unit test が再現性高く完走するようになった。

## 4. メモリ不足による Kimi クラッシュの経緯と対処

- **経緯**:
  - **1回目 (2026-09-16)**: Kimiセッション実行中、Gradleビルド実行とEmulator起動が重なり、Windowsホストのシステムメモリ不足によってKimiプロセスがOSから強制終了された。
  - **2回目 (2026-09-17)**: 再開後、Pixel_8a起動とAPKインストール・アクセシビリティサービス接続まで到達したが、92ターン（約33分）にわたる過大なセッションコンテキスト肥大化とMoonshot API側での429 (engine overloaded) が発生しセッションが中断。
- **原因の分析**:
  - 巨大なコンテキスト長を維持したまま長時間の試行錯誤を1つのセッションに詰め込んだこと。
  - Gradle Daemon や Emulator など高メモリ消費プロセスが重なる中でのリソース管理不足。
- **対処と再発防止策**:
  - **タスクの適正分割と短期完了**: セッションを無制限に引き伸ばさず、明確な単位（ビルド確認 → 実機接続 → テスト実装）ごとにコミット・ログ記録を行う。
  - **Gradleプロセスのメモリ制御**: 必要に応じて `--no-daemon` の活用や不要プロセスのクリーンアップを行う。
  - **複数エージェント体制での引き継ぎ**: AGENTS.mdの規則に基づき、2回失敗した時点でClaudeが状況を精査し、第二実装レーン（Antigravity）へ明確な引継ぎスコープを指示して安全に完了させた。
