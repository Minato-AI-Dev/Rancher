package dev.rancher.android.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class RancherAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityBridge.onServiceConnected(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        AccessibilityBridge.onAccessibilityEvent(event)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        AccessibilityBridge.onServiceDestroyed(this)
        super.onDestroy()
    }
}
