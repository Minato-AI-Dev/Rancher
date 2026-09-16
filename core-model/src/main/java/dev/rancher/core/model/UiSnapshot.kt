package dev.rancher.core.model

data class UiSnapshot(
    val id: String,
    val createdAt: Long,
    val packageName: String,
    val windowTitle: String?,
    val nodes: List<UiNode>,
)
