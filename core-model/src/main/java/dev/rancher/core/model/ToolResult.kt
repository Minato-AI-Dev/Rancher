package dev.rancher.core.model

enum class ToolStatus {
    SUCCESS,
    FAILED,
    NOT_FOUND,
    STALE_SNAPSHOT,
    REQUIRES_CONFIRMATION,
    USER_ACTION_REQUIRED,
    TIMEOUT,
}

data class ToolResult(
    val status: ToolStatus,
    val message: String?,
    val previousSnapshotId: String?,
    val newSnapshotId: String?,
    val durationMs: Long,
)
