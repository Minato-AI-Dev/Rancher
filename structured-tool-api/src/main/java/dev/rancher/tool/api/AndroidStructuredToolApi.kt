package dev.rancher.tool.api

import dev.rancher.android.accessibility.AccessibilityBridge
import dev.rancher.android.actions.AndroidActionExecutor
import dev.rancher.android.snapshot.UiSnapshotEngine
import dev.rancher.core.model.ToolResult
import dev.rancher.core.model.ToolStatus
import dev.rancher.core.model.UiSnapshot

/**
 * Production implementation of [StructuredToolApi] that delegates to the existing
 * Android Control Engine (`UiSnapshotEngine` and `AndroidActionExecutor`).
 *
 * The constructor lambdas are kept for testability; production callers use the
 * no-argument constructor so the real engine objects are wired in automatically.
 */
class AndroidStructuredToolApi(
    private val isConnected: () -> Boolean = { AccessibilityBridge.isConnected() },
    private val capture: suspend () -> UiSnapshot? = { UiSnapshotEngine.capture() },
    private val clickExecutor: suspend (String, Int) -> ToolResult = { snapshotId, nodeId ->
        AndroidActionExecutor.click(snapshotId, nodeId)
    },
) : StructuredToolApi {

    override suspend fun observe(): ObserveToolResult {
        val startedAt = System.currentTimeMillis()

        // AccessibilityServiceが有効でない場合は、ユーザーに有効化を促す明示的な結果を返す。
        // これにより未接続時に無意味なnullや未処理例外がAPI利用者に漏れることを防ぐ。
        if (!isConnected()) {
            return ObserveToolResult(
                status = ToolStatus.USER_ACTION_REQUIRED,
                message = "Enable Rancher AccessibilityService first.",
                snapshot = null,
                durationMs = System.currentTimeMillis() - startedAt,
            )
        }

        val snapshot = capture()
        return if (snapshot != null) {
            ObserveToolResult(
                status = ToolStatus.SUCCESS,
                message = "Captured ${snapshot.id}.",
                snapshot = snapshot,
                durationMs = System.currentTimeMillis() - startedAt,
            )
        } else {
            ObserveToolResult(
                status = ToolStatus.FAILED,
                message = "Could not capture a fresh UI snapshot.",
                snapshot = null,
                durationMs = System.currentTimeMillis() - startedAt,
            )
        }
    }

    override suspend fun click(snapshotId: String, nodeId: Int): ToolResult {
        // 既存のAndroidActionExecutorへ1回だけ委譲する。API層で代替識別子や
        // 迂回ロジックを追加せず、stale/not-found/fingerprint不一致などの
        // 判定も既存エンジンのまま透過的に返す。
        return clickExecutor(snapshotId, nodeId)
    }
}
