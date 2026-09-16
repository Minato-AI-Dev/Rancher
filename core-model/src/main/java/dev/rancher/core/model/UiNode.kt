package dev.rancher.core.model

data class UiNode(
    val id: Int,
    val text: String?,
    val contentDescription: String?,
    val viewId: String?,
    val className: String?,
    val clickable: Boolean,
    val longClickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
    val enabled: Boolean,
    val selected: Boolean,
    val checked: Boolean?,
    val password: Boolean,
    val bounds: UiBounds,
    val parentId: Int?,
    val childIds: List<Int>,
) {
    val label: String?
        get() = text?.takeIf { it.isNotBlank() }
            ?: contentDescription?.takeIf { it.isNotBlank() }
            ?: viewId?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
}
