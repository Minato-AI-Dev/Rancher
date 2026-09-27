package dev.rancher.tool.api

import dev.rancher.core.model.ToolResult
import dev.rancher.core.model.ToolStatus
import dev.rancher.core.model.UiBounds
import dev.rancher.core.model.UiNode
import dev.rancher.core.model.UiSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Red-phase tests for WU-3: verify AndroidStructuredToolApi delegates to the existing
 * Android Control Engine and returns the expected ToolResult/ObserveToolResult shapes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StructuredToolApiTest {

    @Test
    fun observe_connected_returnsSuccessWithSnapshot() = runTest {
        val expectedSnapshot = snapshot("snap_000001")
        val api = AndroidStructuredToolApi(
            isConnected = { true },
            currentSnapshot = { expectedSnapshot },
            capture = { expectedSnapshot },
            clickExecutor = { _, _ -> throw AssertionError("click should not be called") },
        )

        val result = api.observe()

        assertEquals(ToolStatus.SUCCESS, result.status)
        assertNotNull(result.message)
        assertEquals(expectedSnapshot, result.snapshot)
        assertEquals(expectedSnapshot.id, result.snapshot?.id)
        assertTrue(result.durationMs >= 0)
    }

    @Test
    fun observe_notConnected_returnsUserActionRequired() = runTest {
        val api = AndroidStructuredToolApi(
            isConnected = { false },
            currentSnapshot = { null },
            capture = { throw AssertionError("capture should not be called") },
            clickExecutor = { _, _ -> throw AssertionError("click should not be called") },
        )

        val result = api.observe()

        assertEquals(ToolStatus.USER_ACTION_REQUIRED, result.status)
        assertNotNull(result.message)
        assertNull(result.snapshot)
        assertTrue(result.durationMs >= 0)
    }

    @Test
    fun observe_captureReturnsNull_returnsFailed() = runTest {
        val api = AndroidStructuredToolApi(
            isConnected = { true },
            currentSnapshot = { null },
            capture = { null },
            clickExecutor = { _, _ -> throw AssertionError("click should not be called") },
        )

        val result = api.observe()

        assertEquals(ToolStatus.FAILED, result.status)
        assertNotNull(result.message)
        assertNull(result.snapshot)
        assertTrue(result.durationMs >= 0)
    }

    @Test
    fun click_delegatesToActionExecutor() = runTest {
        val expectedResult = ToolResult(
            status = ToolStatus.SUCCESS,
            message = "Clicked.",
            previousSnapshotId = "snap_000001",
            newSnapshotId = "snap_000002",
            durationMs = 100L,
        )
        var delegated = false
        val api = AndroidStructuredToolApi(
            isConnected = { true },
            currentSnapshot = { null },
            capture = { throw AssertionError("capture should not be called") },
            clickExecutor = { snapshotId, nodeId ->
                delegated = true
                assertEquals("snap_000001", snapshotId)
                assertEquals(13, nodeId)
                expectedResult
            },
        )

        val result = api.click("snap_000001", 13)

        assertTrue("click must delegate to action executor", delegated)
        assertEquals(expectedResult, result)
    }

    @Test
    fun click_notFound_isPassedThrough() = runTest {
        val expectedResult = ToolResult(
            status = ToolStatus.NOT_FOUND,
            message = "Node not found.",
            previousSnapshotId = "snap_000001",
            newSnapshotId = null,
            durationMs = 50L,
        )
        val api = AndroidStructuredToolApi(
            isConnected = { true },
            currentSnapshot = { null },
            capture = { throw AssertionError("capture should not be called") },
            clickExecutor = { _, _ -> expectedResult },
        )

        val result = api.click("snap_000001", 999)

        assertEquals(ToolStatus.NOT_FOUND, result.status)
        assertEquals(expectedResult, result)
    }

    @Test
    fun click_staleSnapshot_isPassedThrough() = runTest {
        val expectedResult = ToolResult(
            status = ToolStatus.STALE_SNAPSHOT,
            message = "Stale.",
            previousSnapshotId = "snap_000002",
            newSnapshotId = null,
            durationMs = 50L,
        )
        val api = AndroidStructuredToolApi(
            isConnected = { true },
            currentSnapshot = { null },
            capture = { throw AssertionError("capture should not be called") },
            clickExecutor = { _, _ -> expectedResult },
        )

        val result = api.click("snap_000001", 13)

        assertEquals(ToolStatus.STALE_SNAPSHOT, result.status)
        assertEquals(expectedResult, result)
    }

    @Test
    fun observe_connected_passwordNodeIsRedacted() = runTest {
        val passwordNode = UiNode(
            id = 1,
            text = "[REDACTED]",
            contentDescription = "[REDACTED]",
            viewId = null,
            className = "android.widget.EditText",
            clickable = false,
            longClickable = false,
            editable = true,
            scrollable = false,
            enabled = true,
            selected = false,
            checked = null,
            password = true,
            bounds = UiBounds(0, 0, 100, 50),
            parentId = null,
            childIds = emptyList(),
        )
        val expectedSnapshot = snapshot("snap_000001").copy(nodes = listOf(passwordNode))
        val api = AndroidStructuredToolApi(
            isConnected = { true },
            currentSnapshot = { expectedSnapshot },
            capture = { expectedSnapshot },
            clickExecutor = { _, _ -> throw AssertionError("click should not be called") },
        )

        val result = api.observe()

        assertEquals(ToolStatus.SUCCESS, result.status)
        val observedNode = result.snapshot?.nodes?.firstOrNull()
        assertNotNull(observedNode)
        assertEquals("[REDACTED]", observedNode?.text)
        assertEquals("[REDACTED]", observedNode?.contentDescription)
        assertTrue(observedNode?.password == true)
    }

    @Test
    fun tools_catalogContainsExactlyObserveAndClick() {
        assertEquals(2, StructuredToolApi.tools.size)
        assertNotNull(StructuredToolApi.tools.find { it.name == "observe" })
        assertNotNull(StructuredToolApi.tools.find { it.name == "click" })

        val observe = StructuredToolApi.tools.first { it.name == "observe" }
        assertTrue(observe.outputSchema.containsKey("status"))
        assertTrue(observe.outputSchema.containsKey("message"))
        assertTrue(observe.outputSchema.containsKey("snapshot"))
        assertTrue(observe.outputSchema.containsKey("durationMs"))

        val click = StructuredToolApi.tools.first { it.name == "click" }
        assertTrue(click.inputSchema.containsKey("snapshotId"))
        assertTrue(click.inputSchema.containsKey("nodeId"))
    }

    private fun snapshot(id: String) = UiSnapshot(
        id = id,
        createdAt = 0L,
        packageName = "com.example",
        windowTitle = "Example",
        nodes = emptyList(),
    )
}
