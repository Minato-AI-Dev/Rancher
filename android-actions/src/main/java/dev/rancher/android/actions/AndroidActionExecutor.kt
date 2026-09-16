package dev.rancher.android.actions

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dev.rancher.android.accessibility.AccessibilityBridge
import dev.rancher.android.snapshot.NodeResolution
import dev.rancher.android.snapshot.UiSnapshotEngine
import dev.rancher.core.model.ToolResult
import dev.rancher.core.model.ToolStatus
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

object AndroidActionExecutor {
    private const val TAG = "RancherActions"
    private const val UI_CHANGE_TIMEOUT_MS = 1_800L
    private const val UI_SETTLE_DELAY_MS = 140L

    suspend fun click(snapshotId: String, nodeId: Int): ToolResult {
        val startedAt = System.currentTimeMillis()
        val previousSnapshot = UiSnapshotEngine.currentSnapshot.value
        val previousSnapshotId = previousSnapshot?.id

        if (previousSnapshotId != snapshotId) {
            return result(
                status = ToolStatus.STALE_SNAPSHOT,
                message = "Snapshot $snapshotId is no longer current.",
                previousSnapshotId = previousSnapshotId,
                startedAt = startedAt,
            )
        }

        if (!AccessibilityBridge.isConnected()) {
            return result(
                status = ToolStatus.USER_ACTION_REQUIRED,
                message = "Enable Rancher AccessibilityService first.",
                previousSnapshotId = previousSnapshotId,
                startedAt = startedAt,
            )
        }

        val resolution = UiSnapshotEngine.resolve(snapshotId, nodeId)
        val node = when (resolution) {
            NodeResolution.StaleSnapshot -> return result(
                status = ToolStatus.STALE_SNAPSHOT,
                message = "The current Android UI no longer matches the selected snapshot node.",
                previousSnapshotId = previousSnapshotId,
                startedAt = startedAt,
            )
            NodeResolution.NotFound -> return result(
                status = ToolStatus.NOT_FOUND,
                message = "Node #$nodeId could not be resolved in the current UI.",
                previousSnapshotId = previousSnapshotId,
                startedAt = startedAt,
            )
            is NodeResolution.Found -> resolution.node
        }

        val label = node.text?.toString()
            ?: node.contentDescription?.toString()
            ?: node.viewIdResourceName
            ?: "#${nodeId}"

        val clicked = try {
            node.isEnabled && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } finally {
            node.recycleSafely()
        }

        if (!clicked) {
            Log.w(TAG, "CLICK failed snapshot=$snapshotId node=$nodeId label=$label")
            return result(
                status = ToolStatus.FAILED,
                message = "ACTION_CLICK was rejected for node #$nodeId ($label).",
                previousSnapshotId = previousSnapshotId,
                startedAt = startedAt,
            )
        }

        Log.i(TAG, "CLICK snapshot=$snapshotId node=$nodeId label=$label")

        // Ignore events from Rancher's debug overlay and wait for evidence that the target app
        // changed. If Android does not emit a matching event, we still perform an explicit refresh.
        val targetPackage = previousSnapshot.packageName
        val eventObserved = try {
            withTimeout(UI_CHANGE_TIMEOUT_MS) {
                AccessibilityBridge.events.first { signal ->
                    signal.packageName == targetPackage && signal.eventType in UI_CHANGE_EVENT_TYPES
                }
            }
            true
        } catch (_: TimeoutCancellationException) {
            false
        }

        delay(UI_SETTLE_DELAY_MS)
        val newSnapshot = UiSnapshotEngine.capture()

        return when {
            newSnapshot == null -> result(
                status = if (eventObserved) ToolStatus.FAILED else ToolStatus.TIMEOUT,
                message = "Click executed, but Rancher could not capture a fresh UI snapshot.",
                previousSnapshotId = previousSnapshotId,
                startedAt = startedAt,
            )
            else -> result(
                status = ToolStatus.SUCCESS,
                message = if (eventObserved) {
                    "Click executed and a fresh snapshot was captured."
                } else {
                    "Click executed; no target-app UI event arrived before timeout, so Rancher refreshed explicitly."
                },
                previousSnapshotId = previousSnapshotId,
                newSnapshotId = newSnapshot.id,
                startedAt = startedAt,
            )
        }
    }

    private fun result(
        status: ToolStatus,
        message: String,
        previousSnapshotId: String?,
        newSnapshotId: String? = null,
        startedAt: Long,
    ) = ToolResult(
        status = status,
        message = message,
        previousSnapshotId = previousSnapshotId,
        newSnapshotId = newSnapshotId,
        durationMs = System.currentTimeMillis() - startedAt,
    )

    private val UI_CHANGE_EVENT_TYPES = setOf(
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
        AccessibilityEvent.TYPE_VIEW_CLICKED,
        AccessibilityEvent.TYPE_VIEW_SCROLLED,
    )
}

@Suppress("DEPRECATION")
private fun AccessibilityNodeInfo.recycleSafely() {
    try {
        recycle()
    } catch (_: Throwable) {
        // Compatibility cleanup for older Android versions.
    }
}
