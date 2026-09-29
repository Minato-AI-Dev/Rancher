package dev.rancher.android.actions

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dev.rancher.android.accessibility.AccessibilityBridge
import dev.rancher.android.accessibility.AccessibilitySignal
import dev.rancher.android.snapshot.NodeResolution
import dev.rancher.android.snapshot.UiSnapshotEngine
import dev.rancher.core.model.ToolStatus
import dev.rancher.core.model.UiBounds
import dev.rancher.core.model.UiNode
import dev.rancher.core.model.UiSnapshot
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidActionExecutorTest {

    @After
    fun resetTestSeams() {
        AndroidActionExecutor.isConnectedForTesting = null
        AndroidActionExecutor.resolveForTesting = null
        AndroidActionExecutor.performActionForTesting = null
        AndroidActionExecutor.captureForTesting = null
        UiSnapshotEngine.setCurrentSnapshotForTesting(null)
    }

    @Test
    fun testClick_staleSnapshotProtection_returnsStaleStatus() = runTest {
        // Test 7 requirement: Action execution on a stale snapshot must fail safely with STALE_SNAPSHOT
        val currentSnap = snapshot("snap_latest")
        UiSnapshotEngine.setCurrentSnapshotForTesting(currentSnap)

        val result = AndroidActionExecutor.click(
            snapshotId = "snap_old_12345",
            nodeId = 13,
        )

        assertEquals(ToolStatus.STALE_SNAPSHOT, result.status)
        assertEquals("snap_latest", result.previousSnapshotId)
        assertEquals("Snapshot snap_old_12345 is no longer current.", result.message)
    }

    @Test
    fun testClick_nullSnapshot_returnsStaleStatus() = runTest {
        UiSnapshotEngine.setCurrentSnapshotForTesting(null)

        val result = AndroidActionExecutor.click(
            snapshotId = "snap_nonexistent",
            nodeId = 1,
        )

        assertEquals(ToolStatus.STALE_SNAPSHOT, result.status)
    }

    // --- longClick safety contract ---

    @Test
    fun testLongClick_staleSnapshotProtection_returnsStaleStatus() = runTest {
        UiSnapshotEngine.setCurrentSnapshotForTesting(snapshot("snap_latest"))

        val result = AndroidActionExecutor.longClick(
            snapshotId = "snap_old_12345",
            nodeId = 13,
        )

        assertEquals(ToolStatus.STALE_SNAPSHOT, result.status)
        assertEquals("snap_latest", result.previousSnapshotId)
        assertEquals("Snapshot snap_old_12345 is no longer current.", result.message)
    }

    @Test
    fun testLongClick_nullSnapshot_returnsStaleStatus() = runTest {
        UiSnapshotEngine.setCurrentSnapshotForTesting(null)

        val result = AndroidActionExecutor.longClick(
            snapshotId = "snap_nonexistent",
            nodeId = 1,
        )

        assertEquals(ToolStatus.STALE_SNAPSHOT, result.status)
    }

    @Test
    fun testLongClick_notFoundNode_returnsNotFoundStatus() = runTest {
        UiSnapshotEngine.setCurrentSnapshotForTesting(snapshot("snap_000001"))
        AndroidActionExecutor.isConnectedForTesting = { true }
        AndroidActionExecutor.resolveForTesting = { _, _ -> NodeResolution.NotFound }

        val result = AndroidActionExecutor.longClick(
            snapshotId = "snap_000001",
            nodeId = 999,
        )

        assertEquals(ToolStatus.NOT_FOUND, result.status)
        assertEquals("snap_000001", result.previousSnapshotId)
    }

    @Test
    fun testLongClick_success_returnsSuccessWithFreshSnapshot() = runTest {
        val previousSnapshot = snapshot("snap_000001")
        val freshSnapshot = snapshot("snap_000002")
        UiSnapshotEngine.setCurrentSnapshotForTesting(previousSnapshot)
        AndroidActionExecutor.isConnectedForTesting = { true }
        AndroidActionExecutor.resolveForTesting = { _, _ -> NodeResolution.Found(dummyNode()) }
        AndroidActionExecutor.performActionForTesting = { _, action ->
            assertEquals(AccessibilityNodeInfo.ACTION_LONG_CLICK, action)
            true
        }
        AndroidActionExecutor.captureForTesting = { freshSnapshot }

        val result = AndroidActionExecutor.longClick(
            snapshotId = "snap_000001",
            nodeId = 13,
        )

        assertEquals(ToolStatus.SUCCESS, result.status)
        assertEquals("snap_000001", result.previousSnapshotId)
        assertEquals("snap_000002", result.newSnapshotId)
    }

    @Test
    fun testLongClick_freshCaptureFailure_withoutUiEvent_returnsTimeout() = runTest {
        val previousSnapshot = snapshot("snap_000001")
        UiSnapshotEngine.setCurrentSnapshotForTesting(previousSnapshot)
        AndroidActionExecutor.isConnectedForTesting = { true }
        AndroidActionExecutor.resolveForTesting = { _, _ -> NodeResolution.Found(dummyNode()) }
        AndroidActionExecutor.performActionForTesting = { _, _ -> true }
        AndroidActionExecutor.captureForTesting = { null }
        // AccessibilityBridge.events はテスト間で共有されるため、空のFlowを差し込んで
        // 「UI変化イベントが到着しない」状況を再現する。
        AndroidActionExecutor.eventsForTesting = { MutableSharedFlow() }

        val result = AndroidActionExecutor.longClick(
            snapshotId = "snap_000001",
            nodeId = 13,
        )

        assertEquals(ToolStatus.TIMEOUT, result.status)
        assertEquals("snap_000001", result.previousSnapshotId)
        assertNull(result.newSnapshotId)
    }

    @Test
    fun testLongClick_freshCaptureFailure_withUiEvent_returnsFailed() = runTest {
        val previousSnapshot = snapshot("snap_000001")
        val events = MutableSharedFlow<AccessibilitySignal>(replay = 1)
        UiSnapshotEngine.setCurrentSnapshotForTesting(previousSnapshot)
        AndroidActionExecutor.isConnectedForTesting = { true }
        AndroidActionExecutor.resolveForTesting = { _, _ -> NodeResolution.Found(dummyNode()) }
        AndroidActionExecutor.performActionForTesting = { _, _ -> true }
        AndroidActionExecutor.captureForTesting = { null }
        AndroidActionExecutor.eventsForTesting = { events }

        events.emit(
            AccessibilitySignal(
                sequence = 1,
                packageName = previousSnapshot.packageName,
                eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            ),
        )

        val result = AndroidActionExecutor.longClick(
            snapshotId = "snap_000001",
            nodeId = 13,
        )

        assertEquals(ToolStatus.FAILED, result.status)
        assertEquals("snap_000001", result.previousSnapshotId)
        assertNull(result.newSnapshotId)
    }

    // --- scroll safety contract ---

    @Test
    fun testScroll_staleSnapshotProtection_returnsStaleStatus() = runTest {
        UiSnapshotEngine.setCurrentSnapshotForTesting(snapshot("snap_latest"))

        val result = AndroidActionExecutor.scroll(
            snapshotId = "snap_old_12345",
            nodeId = 13,
            direction = "forward",
        )

        assertEquals(ToolStatus.STALE_SNAPSHOT, result.status)
        assertEquals("snap_latest", result.previousSnapshotId)
    }

    @Test
    fun testScroll_nullSnapshot_returnsStaleStatus() = runTest {
        UiSnapshotEngine.setCurrentSnapshotForTesting(null)

        val result = AndroidActionExecutor.scroll(
            snapshotId = "snap_nonexistent",
            nodeId = 1,
            direction = "forward",
        )

        assertEquals(ToolStatus.STALE_SNAPSHOT, result.status)
    }

    @Test
    fun testScroll_notScrollableNode_returnsNotScrollableStatus() = runTest {
        // scrollable=false のノードが含まれたスナップショットを設定
        UiSnapshotEngine.setCurrentSnapshotForTesting(
            snapshot("snap_000001", nodes = listOf(uiNode(id = 13, scrollable = false, text = "Not scrollable"))),
        )
        AndroidActionExecutor.isConnectedForTesting = { true }

        val result = AndroidActionExecutor.scroll(
            snapshotId = "snap_000001",
            nodeId = 13,
            direction = "forward",
        )

        assertEquals(ToolStatus.NOT_SCROLLABLE, result.status)
        assertEquals("snap_000001", result.previousSnapshotId)
    }

    @Test
    fun testScroll_notFoundNode_returnsNotFoundStatus() = runTest {
        UiSnapshotEngine.setCurrentSnapshotForTesting(
            snapshot("snap_000001", nodes = listOf(uiNode(id = 13, scrollable = true))),
        )
        AndroidActionExecutor.isConnectedForTesting = { true }
        AndroidActionExecutor.resolveForTesting = { _, _ -> NodeResolution.NotFound }

        val result = AndroidActionExecutor.scroll(
            snapshotId = "snap_000001",
            nodeId = 999,
            direction = "forward",
        )

        assertEquals(ToolStatus.NOT_FOUND, result.status)
        assertEquals("snap_000001", result.previousSnapshotId)
    }

    @Test
    fun testScroll_successForward_returnsSuccessWithFreshSnapshot() = runTest {
        val previousSnapshot = snapshot(
            "snap_000001",
            nodes = listOf(uiNode(id = 13, scrollable = true)),
        )
        val freshSnapshot = snapshot("snap_000002")
        UiSnapshotEngine.setCurrentSnapshotForTesting(previousSnapshot)
        AndroidActionExecutor.isConnectedForTesting = { true }
        AndroidActionExecutor.resolveForTesting = { _, _ -> NodeResolution.Found(dummyNode()) }
        AndroidActionExecutor.performActionForTesting = { _, action ->
            assertEquals(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, action)
            true
        }
        AndroidActionExecutor.captureForTesting = { freshSnapshot }

        val result = AndroidActionExecutor.scroll(
            snapshotId = "snap_000001",
            nodeId = 13,
            direction = "forward",
        )

        assertEquals(ToolStatus.SUCCESS, result.status)
        assertEquals("snap_000001", result.previousSnapshotId)
        assertEquals("snap_000002", result.newSnapshotId)
    }

    @Test
    fun testScroll_successBackward_returnsSuccessWithFreshSnapshot() = runTest {
        val previousSnapshot = snapshot(
            "snap_000001",
            nodes = listOf(uiNode(id = 13, scrollable = true)),
        )
        val freshSnapshot = snapshot("snap_000002")
        UiSnapshotEngine.setCurrentSnapshotForTesting(previousSnapshot)
        AndroidActionExecutor.isConnectedForTesting = { true }
        AndroidActionExecutor.resolveForTesting = { _, _ -> NodeResolution.Found(dummyNode()) }
        AndroidActionExecutor.performActionForTesting = { _, action ->
            assertEquals(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, action)
            true
        }
        AndroidActionExecutor.captureForTesting = { freshSnapshot }

        val result = AndroidActionExecutor.scroll(
            snapshotId = "snap_000001",
            nodeId = 13,
            direction = "backward",
        )

        assertEquals(ToolStatus.SUCCESS, result.status)
        assertEquals("snap_000001", result.previousSnapshotId)
        assertEquals("snap_000002", result.newSnapshotId)
    }

    @Test
    fun testScroll_freshCaptureFailure_withoutUiEvent_returnsTimeout() = runTest {
        val previousSnapshot = snapshot("snap_000001")
        UiSnapshotEngine.setCurrentSnapshotForTesting(previousSnapshot)
        AndroidActionExecutor.isConnectedForTesting = { true }
        AndroidActionExecutor.resolveForTesting = { _, _ -> NodeResolution.Found(dummyNode()) }
        AndroidActionExecutor.performActionForTesting = { _, _ -> true }
        AndroidActionExecutor.captureForTesting = { null }
        AndroidActionExecutor.eventsForTesting = { MutableSharedFlow() }

        val result = AndroidActionExecutor.scroll(
            snapshotId = "snap_000001",
            nodeId = 13,
            direction = "forward",
        )

        assertEquals(ToolStatus.TIMEOUT, result.status)
        assertEquals("snap_000001", result.previousSnapshotId)
        assertNull(result.newSnapshotId)
    }

    @Test
    fun testScroll_freshCaptureFailure_withUiEvent_returnsFailed() = runTest {
        val previousSnapshot = snapshot("snap_000001")
        val events = MutableSharedFlow<AccessibilitySignal>(replay = 1)
        UiSnapshotEngine.setCurrentSnapshotForTesting(previousSnapshot)
        AndroidActionExecutor.isConnectedForTesting = { true }
        AndroidActionExecutor.resolveForTesting = { _, _ -> NodeResolution.Found(dummyNode()) }
        AndroidActionExecutor.performActionForTesting = { _, _ -> true }
        AndroidActionExecutor.captureForTesting = { null }
        AndroidActionExecutor.eventsForTesting = { events }

        events.emit(
            AccessibilitySignal(
                sequence = 1,
                packageName = previousSnapshot.packageName,
                eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            ),
        )

        val result = AndroidActionExecutor.scroll(
            snapshotId = "snap_000001",
            nodeId = 13,
            direction = "forward",
        )

        assertEquals(ToolStatus.FAILED, result.status)
        assertEquals("snap_000001", result.previousSnapshotId)
        assertNull(result.newSnapshotId)
    }

    private fun snapshot(id: String, nodes: List<UiNode> = emptyList()) = UiSnapshot(
        id = id,
        createdAt = System.currentTimeMillis(),
        packageName = "com.android.settings",
        windowTitle = "Settings",
        nodes = nodes,
    )

    private fun uiNode(
        id: Int,
        scrollable: Boolean = false,
        text: String? = null,
    ) = UiNode(
        id = id,
        text = text,
        contentDescription = null,
        viewId = null,
        className = "android.widget.TextView",
        clickable = false,
        longClickable = false,
        editable = false,
        scrollable = scrollable,
        enabled = true,
        selected = false,
        checked = null,
        password = false,
        bounds = UiBounds(0, 0, 100, 50),
        parentId = null,
        childIds = emptyList(),
    )

    private fun dummyNode(): AccessibilityNodeInfo {
        // JVM単体テスト用のダミー。returnDefaultValues=true で未mockのAndroidメソッドが
        // デフォルト値を返すようになるが、インスタンス生成には空コンストラクタを使う。
        return AccessibilityNodeInfo()
    }
}
