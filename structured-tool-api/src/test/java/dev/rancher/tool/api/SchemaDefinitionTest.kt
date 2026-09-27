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
    fun structuredToolApi_contractHasTwoTools() {
        // The public API exposes exactly two tools: observe and click.
        assertEquals(2, StructuredToolApi.tools.size)
        assertNotNull(StructuredToolApi.tools.find { it.name == "observe" })
        assertNotNull(StructuredToolApi.tools.find { it.name == "click" })
    }
}
