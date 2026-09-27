package dev.rancher.tool.api

import dev.rancher.core.model.ToolResult

/**
 * Stable, typed internal boundary between AI Agent callers and the Android Control Engine.
 *
 * Callers interact with the device only through `observe()` and `click(snapshotId, nodeId)`;
 * no `AccessibilityNodeInfo`, Compose nodes, or View instances leak through this layer.
 */
interface StructuredToolApi {

    suspend fun observe(): ObserveToolResult

    suspend fun click(snapshotId: String, nodeId: Int): ToolResult

    companion object {
        /**
         * Programmatic catalog of all tools exposed by this API. M1 exposes exactly
         * `observe` and `click`; no other tools are registered.
         */
        val tools: List<ToolDefinition> = listOf(
            ToolDefinition(
                name = "observe",
                description = "Capture the current Android UI as a semantic UiSnapshot.",
                inputSchema = emptyMap(),
                outputSchema = mapOf(
                    "status" to "ToolStatus (SUCCESS, USER_ACTION_REQUIRED, FAILED, TIMEOUT)",
                    "message" to "String?",
                    "snapshot" to "UiSnapshot?",
                    "durationMs" to "Long",
                ),
            ),
            ToolDefinition(
                name = "click",
                description = "Perform ACTION_CLICK on the node identified by snapshotId and nodeId.",
                inputSchema = mapOf(
                    "snapshotId" to "String (non-empty)",
                    "nodeId" to "Int (positive)",
                ),
                outputSchema = mapOf(
                    "status" to "ToolStatus",
                    "message" to "String?",
                    "previousSnapshotId" to "String?",
                    "newSnapshotId" to "String?",
                    "durationMs" to "Long",
                ),
            ),
        )
    }
}
