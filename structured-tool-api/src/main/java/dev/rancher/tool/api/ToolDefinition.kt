package dev.rancher.tool.api

/**
 * Machine-readable description of a tool exposed by the Structured Tool API.
 *
 * Schemas are intentionally lightweight maps (`fieldName -> type/description`)
 * so callers can introspect the API without pulling in a JSON serialization library.
 */
data class ToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: Map<String, String>,
    val outputSchema: Map<String, String>,
)
