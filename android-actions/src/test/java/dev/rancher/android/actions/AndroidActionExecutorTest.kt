package dev.rancher.android.actions

import dev.rancher.android.snapshot.UiSnapshotEngine
import dev.rancher.core.model.ToolStatus
import dev.rancher.core.model.UiSnapshot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidActionExecutorTest {

    @Test
    fun testClick_staleSnapshotProtection_returnsStaleStatus() = runTest {
        // Test 7 requirement: Action execution on a stale snapshot must fail safely with STALE_SNAPSHOT
        val currentSnap = UiSnapshot(
            id = "snap_latest",
            createdAt = System.currentTimeMillis(),
            packageName = "com.android.settings",
            windowTitle = "Settings",
            nodes = emptyList(),
        )
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
}