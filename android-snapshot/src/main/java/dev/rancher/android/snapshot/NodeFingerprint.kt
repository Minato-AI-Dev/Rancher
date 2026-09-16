package dev.rancher.android.snapshot

import dev.rancher.core.model.UiBounds

internal data class NodeFingerprint(
    val viewId: String?,
    val className: String?,
    val text: String?,
    val contentDescription: String?,
    val bounds: UiBounds,
    val clickable: Boolean,
    val enabled: Boolean,
)

internal data class NodeHandle(
    val path: List<Int>,
    val fingerprint: NodeFingerprint,
)
