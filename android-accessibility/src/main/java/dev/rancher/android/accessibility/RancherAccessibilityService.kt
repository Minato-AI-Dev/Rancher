package dev.rancher.android.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent

class RancherAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        // 【安全性・安定化処理: serviceInfoの明示的再設定】
        // エミュレータ等の環境ではXML設定だけだと接続が不安定になることがあるため、
        // サービス開始時に画面変化やクリック検知などの監視設定をコードから明示的に再適用し、
        // Androidシステムから確実にUIイベントを受け取れる状態にします。
        //
        // Re-apply service info so the framework re-binds the configuration explicitly.
        // This reduces flakiness on some emulator images where the XML metadata alone is
        // not enough to keep the service consistently connected.
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_CLICKED or
                AccessibilityEvent.TYPE_VIEW_SCROLLED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 50
        }
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
