# 既存エンジンのJVMテスト不足（M1で既知の課題として承認、2026-09-28）

- `UiSnapshotEngine` / `AndroidActionExecutor` / `AccessibilityBridge` はDI不可のsingleton `object`（`_service`はprivate、mockk/Robolectric未導入）。
- 未検証の2分岐: (a) 同一snapshotIdで画面要素のfingerprintだけ変わった場合のSTALE_SNAPSHOT、(b) click成功後のfresh capture失敗（イベントありFAILED／なしTIMEOUT）。
- 対応案: エンジンへのテスト用seam追加 / Robolectric等の追加 / Emulator計装テスト。次マイルストーンで選択する。
- 副次課題: クリック直後のfresh snapshotが遷移前ノード(33件)のまま返ることがある（Emulator確認）。UI settle待ちの見直し候補。
