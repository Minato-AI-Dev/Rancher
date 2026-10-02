package dev.rancher.android.actions

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dev.rancher.android.accessibility.AccessibilityBridge
import dev.rancher.android.accessibility.AccessibilitySignal
import dev.rancher.android.snapshot.NodeResolution
import dev.rancher.android.snapshot.UiSnapshotEngine
import dev.rancher.core.model.ToolResult
import dev.rancher.core.model.ToolStatus
import dev.rancher.core.model.UiSnapshot
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

object AndroidActionExecutor {
    private const val TAG = "RancherActions"
    private const val UI_CHANGE_TIMEOUT_MS = 1_800L
    private const val UI_SETTLE_DELAY_MS = 140L

    // 単体テスト用の注入ポイント（本番ではnull）。JVMテストでAndroidフレームワークに依存しない
    // 安全契約・fresh observationの検証を行うために必要（M1の既知課題を踏まえたseam）。
    internal var isConnectedForTesting: (() -> Boolean)? = null
    internal var resolveForTesting: ((String, Int) -> NodeResolution)? = null
    internal var performActionForTesting: ((AccessibilityNodeInfo, Int) -> Boolean)? = null
    internal var captureForTesting: (() -> UiSnapshot?)? = null
    internal var eventsForTesting: (() -> Flow<AccessibilitySignal>)? = null
    // 単体テスト用: performGlobalAction(BACK/HOME) を差し替える注入ポイント
    internal var performGlobalActionForTesting: ((Int) -> Boolean)? = null

    private fun isConnectedBridge(): Boolean =
        isConnectedForTesting?.invoke() ?: AccessibilityBridge.isConnected()

    private fun resolveBridge(snapshotId: String, nodeId: Int): NodeResolution =
        resolveForTesting?.invoke(snapshotId, nodeId) ?: UiSnapshotEngine.resolve(snapshotId, nodeId)

    private fun performActionBridge(node: AccessibilityNodeInfo, action: Int): Boolean =
        performActionForTesting?.invoke(node, action) ?: (node.isEnabled && node.performAction(action))

    private suspend fun captureBridge(): UiSnapshot? =
        captureForTesting?.invoke() ?: UiSnapshotEngine.capture()

    private fun uiChangeEvents(): Flow<AccessibilitySignal> =
        eventsForTesting?.invoke() ?: AccessibilityBridge.events

    // AccessibilityBridge へのグローバル操作窓口を android-actions 層で提供
    // （指定どおり `AccessibilityBridge.performGlobalAction(...)` の形で呼び出せる）
    private fun AccessibilityBridge.performGlobalAction(action: Int): Boolean =
        service.value?.performGlobalAction(action) ?: false

    private fun performGlobalActionBridge(action: Int): Boolean =
        performGlobalActionForTesting?.invoke(action) ?: AccessibilityBridge.performGlobalAction(action)

    // 【安全性・誤操作防止: 古い画面情報（Stale Snapshot）に対する操作の即時遮断】
    // 操作対象として指定された snapshotId が最新画面（currentSnapshot）と異なる場合や、
    // 画面遷移によって対象ノードが現在の画面と一致しない場合は、
    // 古い画面情報に基づいて意図しない別のボタンを押してしまう危険を防ぐため、
    // 操作を実行せずに STALE_SNAPSHOT エラーで安全に終了します。
    suspend fun click(snapshotId: String, nodeId: Int): ToolResult {
        val startedAt = System.currentTimeMillis()
        val previousSnapshot = UiSnapshotEngine.currentSnapshot.value
        val previousSnapshotId = previousSnapshot?.id

        // 指定されたスナップショットIDが最新でない場合は直ちに拒絶
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
            // 画面上の要素が変化・不一致の場合は古い画面情報として拒絶
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

        // 【安全性・信頼性確保: 操作後の新しい画面状態の再取得（Fresh Observation）】
        // 「1つの操作を行ったら必ず新しい画面を再観測する（One action -> one fresh observation）」原則に従います。
        // クリック実行後に対象アプリのUI変化イベント（画面遷移や内容変更）を待機し、
        // 画面が落ち着いた段階で最新のスナップショット（UiSnapshot）を新しく生成します。
        // これにより、古い画面認識を引きずったまま次の操作を行ってしまう連鎖的な誤操作を防止します。
        //
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

    // 【安全性・誤操作防止: 長押し（longClick）もclickと同じStale Snapshot保護を適用】
    // 指定されたsnapshotId/nodeIdが最新画面と一致しない、または画面要素のfingerprintが一致しない場合は
    // 操作を実行せずSTALE_SNAPSHOTで安全に終了します。
    suspend fun longClick(snapshotId: String, nodeId: Int): ToolResult {
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

        if (!isConnectedBridge()) {
            return result(
                status = ToolStatus.USER_ACTION_REQUIRED,
                message = "Enable Rancher AccessibilityService first.",
                previousSnapshotId = previousSnapshotId,
                startedAt = startedAt,
            )
        }

        val resolution = resolveBridge(snapshotId, nodeId)
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

        val longClicked = try {
            performActionBridge(node, AccessibilityNodeInfo.ACTION_LONG_CLICK)
        } finally {
            node.recycleSafely()
        }

        if (!longClicked) {
            Log.w(TAG, "LONG_CLICK failed snapshot=$snapshotId node=$nodeId label=$label")
            return result(
                status = ToolStatus.FAILED,
                message = "ACTION_LONG_CLICK was rejected for node #$nodeId ($label).",
                previousSnapshotId = previousSnapshotId,
                startedAt = startedAt,
            )
        }

        Log.i(TAG, "LONG_CLICK snapshot=$snapshotId node=$nodeId label=$label")
        return observeAfterAction(
            snapshotId = snapshotId,
            nodeId = nodeId,
            label = label,
            previousSnapshot = previousSnapshot,
            previousSnapshotId = previousSnapshotId,
            startedAt = startedAt,
            actionName = "Long click",
        )
    }

    // 【安全性・誤操作防止: スクロール（scroll）もclickと同じStale Snapshot保護を適用】
    // さらに、対象ノードがscrollableでない場合はスクロールを実行せずNOT_SCROLLABLEで安全に拒絶します。
    suspend fun scroll(snapshotId: String, nodeId: Int, direction: String): ToolResult {
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

        if (!isConnectedBridge()) {
            return result(
                status = ToolStatus.USER_ACTION_REQUIRED,
                message = "Enable Rancher AccessibilityService first.",
                previousSnapshotId = previousSnapshotId,
                startedAt = startedAt,
            )
        }

        val targetNode = previousSnapshot.nodes.find { it.id == nodeId }
        if (targetNode != null && !targetNode.scrollable) {
            return result(
                status = ToolStatus.NOT_SCROLLABLE,
                message = "Node #$nodeId is not scrollable.",
                previousSnapshotId = previousSnapshotId,
                startedAt = startedAt,
            )
        }

        val scrollAction = when (direction.lowercase()) {
            "forward" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            "backward" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            else -> return result(
                status = ToolStatus.FAILED,
                message = "Invalid scroll direction '$direction'. Use 'forward' or 'backward'.",
                previousSnapshotId = previousSnapshotId,
                startedAt = startedAt,
            )
        }

        val resolution = resolveBridge(snapshotId, nodeId)
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

        val scrolled = try {
            performActionBridge(node, scrollAction)
        } finally {
            node.recycleSafely()
        }

        if (!scrolled) {
            Log.w(TAG, "SCROLL failed snapshot=$snapshotId node=$nodeId direction=$direction label=$label")
            return result(
                status = ToolStatus.FAILED,
                message = "${scrollActionName(direction)} was rejected for node #$nodeId ($label).",
                previousSnapshotId = previousSnapshotId,
                startedAt = startedAt,
            )
        }

        Log.i(TAG, "SCROLL snapshot=$snapshotId node=$nodeId direction=$direction label=$label")
        return observeAfterAction(
            snapshotId = snapshotId,
            nodeId = nodeId,
            label = label,
            previousSnapshot = previousSnapshot,
            previousSnapshotId = previousSnapshotId,
            startedAt = startedAt,
            actionName = scrollActionName(direction),
        )
    }

    // 【安全性・誤操作防止: グローバル操作（back/home）はノードに紐づかない】
    // snapshotId/nodeIdを取らず、システム全体の戻る/ホーム操作を実行します。
    // ただし「1操作→1回の必ず成功する再観測」原則は維持し、実行後に最新UIを再取得します。
    suspend fun back(): ToolResult {
        val startedAt = System.currentTimeMillis()
        val previousSnapshot = UiSnapshotEngine.currentSnapshot.value
        val previousSnapshotId = previousSnapshot?.id

        if (!isConnectedBridge()) {
            return result(
                status = ToolStatus.USER_ACTION_REQUIRED,
                message = "Enable Rancher AccessibilityService first.",
                previousSnapshotId = previousSnapshotId,
                startedAt = startedAt,
            )
        }

        val performed = performGlobalActionBridge(AccessibilityService.GLOBAL_ACTION_BACK)
        if (!performed) {
            Log.w(TAG, "BACK failed")
            return result(
                status = ToolStatus.FAILED,
                message = "GLOBAL_ACTION_BACK was rejected.",
                previousSnapshotId = previousSnapshotId,
                startedAt = startedAt,
            )
        }

        Log.i(TAG, "BACK executed")

        return if (previousSnapshot != null) {
            observeAfterGlobalAction(
                previousSnapshot = previousSnapshot,
                previousSnapshotId = previousSnapshotId!!,
                startedAt = startedAt,
                actionName = "Back",
            )
        } else {
            val newSnapshot = captureBridge()
            result(
                status = if (newSnapshot != null) ToolStatus.SUCCESS else ToolStatus.TIMEOUT,
                message = if (newSnapshot != null) {
                    "Back executed and a fresh snapshot was captured."
                } else {
                    "Back executed, but Rancher could not capture a fresh UI snapshot."
                },
                previousSnapshotId = previousSnapshotId,
                newSnapshotId = newSnapshot?.id,
                startedAt = startedAt,
            )
        }
    }

    suspend fun home(): ToolResult {
        val startedAt = System.currentTimeMillis()
        val previousSnapshot = UiSnapshotEngine.currentSnapshot.value
        val previousSnapshotId = previousSnapshot?.id

        if (!isConnectedBridge()) {
            return result(
                status = ToolStatus.USER_ACTION_REQUIRED,
                message = "Enable Rancher AccessibilityService first.",
                previousSnapshotId = previousSnapshotId,
                startedAt = startedAt,
            )
        }

        val performed = performGlobalActionBridge(AccessibilityService.GLOBAL_ACTION_HOME)
        if (!performed) {
            Log.w(TAG, "HOME failed")
            return result(
                status = ToolStatus.FAILED,
                message = "GLOBAL_ACTION_HOME was rejected.",
                previousSnapshotId = previousSnapshotId,
                startedAt = startedAt,
            )
        }

        Log.i(TAG, "HOME executed")

        return if (previousSnapshot != null) {
            observeAfterGlobalAction(
                previousSnapshot = previousSnapshot,
                previousSnapshotId = previousSnapshotId!!,
                startedAt = startedAt,
                actionName = "Home",
            )
        } else {
            val newSnapshot = captureBridge()
            result(
                status = if (newSnapshot != null) ToolStatus.SUCCESS else ToolStatus.TIMEOUT,
                message = if (newSnapshot != null) {
                    "Home executed and a fresh snapshot was captured."
                } else {
                    "Home executed, but Rancher could not capture a fresh UI snapshot."
                },
                previousSnapshotId = previousSnapshotId,
                newSnapshotId = newSnapshot?.id,
                startedAt = startedAt,
            )
        }
    }

    // 【安全性・信頼性確保: 操作後の新しい画面状態の再取得（Fresh Observation）】
    // click/longClick/scroll いずれも共通で、「1操作→1回の必ず成功する再観測」を行います。
    // テスト用の events/capture seam を使えるよう、longClick/scroll からはこちらを呼びます。
    private suspend fun observeAfterAction(
        snapshotId: String,
        nodeId: Int,
        label: String,
        previousSnapshot: UiSnapshot,
        previousSnapshotId: String,
        startedAt: Long,
        actionName: String,
    ): ToolResult {
        val targetPackage = previousSnapshot.packageName
        val eventObserved = try {
            withTimeout(UI_CHANGE_TIMEOUT_MS) {
                uiChangeEvents().first { signal ->
                    signal.packageName == targetPackage && signal.eventType in UI_CHANGE_EVENT_TYPES
                }
            }
            true
        } catch (_: TimeoutCancellationException) {
            false
        }

        delay(UI_SETTLE_DELAY_MS)
        val newSnapshot = captureBridge()

        return when {
            newSnapshot == null -> result(
                status = if (eventObserved) ToolStatus.FAILED else ToolStatus.TIMEOUT,
                message = "$actionName executed, but Rancher could not capture a fresh UI snapshot.",
                previousSnapshotId = previousSnapshotId,
                startedAt = startedAt,
            )
            else -> result(
                status = ToolStatus.SUCCESS,
                message = if (eventObserved) {
                    "$actionName executed and a fresh snapshot was captured."
                } else {
                    "$actionName executed; no target-app UI event arrived before timeout, so Rancher refreshed explicitly."
                },
                previousSnapshotId = previousSnapshotId,
                newSnapshotId = newSnapshot.id,
                startedAt = startedAt,
            )
        }
    }

    // back/home 用の fresh observation。ノードに紐づかないグローバル操作でも
    // 「1操作→1回の必ず成功する再観測」原則を維持する。
    private suspend fun observeAfterGlobalAction(
        previousSnapshot: UiSnapshot,
        previousSnapshotId: String,
        startedAt: Long,
        actionName: String,
    ): ToolResult {
        val targetPackage = previousSnapshot.packageName
        val eventObserved = try {
            withTimeout(UI_CHANGE_TIMEOUT_MS) {
                uiChangeEvents().first { signal ->
                    signal.packageName == targetPackage && signal.eventType in UI_CHANGE_EVENT_TYPES
                }
            }
            true
        } catch (_: TimeoutCancellationException) {
            false
        }

        delay(UI_SETTLE_DELAY_MS)
        val newSnapshot = captureBridge()

        return when {
            newSnapshot == null -> result(
                status = if (eventObserved) ToolStatus.FAILED else ToolStatus.TIMEOUT,
                message = "$actionName executed, but Rancher could not capture a fresh UI snapshot.",
                previousSnapshotId = previousSnapshotId,
                startedAt = startedAt,
            )
            else -> result(
                status = ToolStatus.SUCCESS,
                message = if (eventObserved) {
                    "$actionName executed and a fresh snapshot was captured."
                } else {
                    "$actionName executed; no target-app UI event arrived before timeout, so Rancher refreshed explicitly."
                },
                previousSnapshotId = previousSnapshotId,
                newSnapshotId = newSnapshot.id,
                startedAt = startedAt,
            )
        }
    }

    private fun scrollActionName(direction: String) = when (direction.lowercase()) {
        "forward" -> "ACTION_SCROLL_FORWARD"
        "backward" -> "ACTION_SCROLL_BACKWARD"
        else -> "SCROLL"
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
