package dev.rancher.tool.api

import dev.rancher.core.model.ToolStatus
import dev.rancher.core.model.UiSnapshot

/**
 * Structured result returned by the `observe()` tool.
 *
 * Mirrors the existing `ToolResult` shape but carries the full `UiSnapshot`
 * instead of only snapshot IDs, because observation's primary deliverable
 * is the captured screen model itself.
 */
data class ObserveToolResult(
    val status: ToolStatus,
    val message: String?,
    val snapshot: UiSnapshot?,
    val durationMs: Long,
)
