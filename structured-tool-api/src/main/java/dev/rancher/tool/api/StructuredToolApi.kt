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

    suspend fun longClick(snapshotId: String, nodeId: Int): ToolResult

    suspend fun scroll(snapshotId: String, nodeId: Int, direction: String): ToolResult

    suspend fun back(): ToolResult

    suspend fun home(): ToolResult

    companion object {
        /**
         * Programmatic catalog of all tools exposed by this API. M2 exposes
         * `observe`, `click`, `longClick`, `scroll`, `back`, and `home`.
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
            ToolDefinition(
                name = "longClick",
                description = "Perform ACTION_LONG_CLICK on the node identified by snapshotId and nodeId.",
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
            ToolDefinition(
                name = "scroll",
                description = "Scroll the node identified by snapshotId and nodeId in the given direction.",
                inputSchema = mapOf(
                    "snapshotId" to "String (non-empty)",
                    "nodeId" to "Int (positive)",
                    "direction" to "String ('forward' | 'backward')",
                ),
                outputSchema = mapOf(
                    "status" to "ToolStatus",
                    "message" to "String?",
                    "previousSnapshotId" to "String?",
                    "newSnapshotId" to "String?",
                    "durationMs" to "Long",
                ),
            ),
            ToolDefinition(
                name = "back",
                description = "Press the system BACK button via performGlobalAction(GLOBAL_ACTION_BACK).",
                inputSchema = emptyMap(),
                outputSchema = mapOf(
                    "status" to "ToolStatus",
                    "message" to "String?",
                    "previousSnapshotId" to "String?",
                    "newSnapshotId" to "String?",
                    "durationMs" to "Long",
                ),
            ),
            ToolDefinition(
                name = "home",
                description = "Press the system HOME button via performGlobalAction(GLOBAL_ACTION_HOME).",
                inputSchema = emptyMap(),
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
