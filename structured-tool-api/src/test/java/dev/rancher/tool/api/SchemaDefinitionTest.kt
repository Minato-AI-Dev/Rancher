package dev.rancher.tool.api

import dev.rancher.core.model.ToolStatus
import dev.rancher.core.model.UiSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Red-phase tests for WU-2: verify the Structured Tool API contract and schema types exist
 * with the expected shape before the production code is implemented.
 */
class SchemaDefinitionTest {

    @Test
    fun toolDefinition_hasExpectedFields() {
        val definition = ToolDefinition(
            name = "observe",
            description = "Capture the current Android UI as a semantic snapshot.",
            inputSchema = emptyMap(),
            outputSchema = mapOf(
                "status" to "ToolStatus",
                "message" to "String?",
                "snapshot" to "UiSnapshot?",
                "durationMs" to "Long",
            ),
        )

        assertEquals("observe", definition.name)
        assertNotNull(definition.description)
        assertNotNull(definition.inputSchema)
        assertNotNull(definition.outputSchema)
    }

    @Test
    fun observeToolResult_hasExpectedSchemaFields() {
        val snapshot = UiSnapshot(
            id = "snap_000001",
            createdAt = 0L,
            packageName = "com.example",
            windowTitle = "Example",
            nodes = emptyList(),
        )
        val result = ObserveToolResult(
            status = ToolStatus.SUCCESS,
            message = "Captured.",
            snapshot = snapshot,
            durationMs = 42L,
        )

        assertEquals(ToolStatus.SUCCESS, result.status)
        assertEquals("Captured.", result.message)
        assertEquals(snapshot, result.snapshot)
        assertEquals(42L, result.durationMs)
    }

    @Test
    fun structuredToolApi_contractHasSixTools() {
        // The public API exposes exactly six tools: observe, click, longClick, scroll, back, and home.
        assertEquals(6, StructuredToolApi.tools.size)
        assertNotNull(StructuredToolApi.tools.find { it.name == "observe" })
        assertNotNull(StructuredToolApi.tools.find { it.name == "click" })
        assertNotNull(StructuredToolApi.tools.find { it.name == "longClick" })
        assertNotNull(StructuredToolApi.tools.find { it.name == "scroll" })
        assertNotNull(StructuredToolApi.tools.find { it.name == "back" })
        assertNotNull(StructuredToolApi.tools.find { it.name == "home" })
    }

    @Test
    fun longClickToolDefinition_hasExpectedSchema() {
        val definition = StructuredToolApi.tools.find { it.name == "longClick" }
            ?: throw AssertionError("longClick tool not found")

        assertEquals(
            "Perform ACTION_LONG_CLICK on the node identified by snapshotId and nodeId.",
            definition.description,
        )
        assertEquals(
            mapOf(
                "snapshotId" to "String (non-empty)",
                "nodeId" to "Int (positive)",
            ),
            definition.inputSchema,
        )
        assertEquals(
            mapOf(
                "status" to "ToolStatus",
                "message" to "String?",
                "previousSnapshotId" to "String?",
                "newSnapshotId" to "String?",
                "durationMs" to "Long",
            ),
            definition.outputSchema,
        )
    }

    @Test
    fun scrollToolDefinition_hasExpectedSchema() {
        val definition = StructuredToolApi.tools.find { it.name == "scroll" }
            ?: throw AssertionError("scroll tool not found")

        assertEquals(
            "Scroll the node identified by snapshotId and nodeId in the given direction.",
            definition.description,
        )
        assertEquals(
            mapOf(
                "snapshotId" to "String (non-empty)",
                "nodeId" to "Int (positive)",
                "direction" to "String ('forward' | 'backward')",
            ),
            definition.inputSchema,
        )
        assertEquals(
            mapOf(
                "status" to "ToolStatus",
                "message" to "String?",
                "previousSnapshotId" to "String?",
                "newSnapshotId" to "String?",
                "durationMs" to "Long",
            ),
            definition.outputSchema,
        )
    }

    @Test
    fun backToolDefinition_hasExpectedSchema() {
        val definition = StructuredToolApi.tools.find { it.name == "back" }
            ?: throw AssertionError("back tool not found")

        assertEquals(
            "Press the system BACK button via performGlobalAction(GLOBAL_ACTION_BACK).",
            definition.description,
        )
        assertEquals(emptyMap<String, String>(), definition.inputSchema)
        assertEquals(
            mapOf(
                "status" to "ToolStatus",
                "message" to "String?",
                "previousSnapshotId" to "String?",
                "newSnapshotId" to "String?",
                "durationMs" to "Long",
            ),
            definition.outputSchema,
        )
    }

    @Test
    fun homeToolDefinition_hasExpectedSchema() {
        val definition = StructuredToolApi.tools.find { it.name == "home" }
            ?: throw AssertionError("home tool not found")

        assertEquals(
            "Press the system HOME button via performGlobalAction(GLOBAL_ACTION_HOME).",
            definition.description,
        )
        assertEquals(emptyMap<String, String>(), definition.inputSchema)
        assertEquals(
            mapOf(
                "status" to "ToolStatus",
                "message" to "String?",
                "previousSnapshotId" to "String?",
                "newSnapshotId" to "String?",
                "durationMs" to "Long",
            ),
            definition.outputSchema,
        )
    }
}
