package dev.rancher.android.snapshot

import android.view.accessibility.AccessibilityNodeInfo

sealed interface NodeResolution {
    data class Found(val node: AccessibilityNodeInfo) : NodeResolution
    data object StaleSnapshot : NodeResolution
    data object NotFound : NodeResolution
}
