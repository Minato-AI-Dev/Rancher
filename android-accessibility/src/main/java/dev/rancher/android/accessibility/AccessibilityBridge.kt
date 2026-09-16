package dev.rancher.android.accessibility

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

data class AccessibilitySignal(
    val sequence: Long,
    val packageName: String?,
    val eventType: Int,
)

/**
 * Internal Android bridge. Raw AccessibilityNodeInfo values are intentionally kept in this
 * Android-only module and are never exposed to Rancher's semantic model or Compose harness.
 */
object AccessibilityBridge {
    private const val TAG = "RancherAccessibility"

    private val _service = MutableStateFlow<RancherAccessibilityService?>(null)
    val service: StateFlow<RancherAccessibilityService?> = _service.asStateFlow()

    private val _activePackage = MutableStateFlow<String?>(null)
    val activePackage: StateFlow<String?> = _activePackage.asStateFlow()

    private val _events = MutableSharedFlow<AccessibilitySignal>(extraBufferCapacity = 64)
    val events: SharedFlow<AccessibilitySignal> = _events.asSharedFlow()

    private val eventCounter = AtomicLong(0)

    internal fun onServiceConnected(service: RancherAccessibilityService) {
        _service.value = service
        Log.i(TAG, "Accessibility service connected")
    }

    internal fun onServiceDestroyed(service: RancherAccessibilityService) {
        if (_service.value === service) {
            _service.value = null
            _activePackage.value = null
        }
        Log.i(TAG, "Accessibility service destroyed")
    }

    internal fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString()
        if (!packageName.isNullOrBlank()) {
            _activePackage.value = packageName
        }
        Log.d(
            TAG,
            "event=${AccessibilityEvent.eventTypeToString(event.eventType)} package=${packageName ?: "<unknown>"}",
        )
        _events.tryEmit(
            AccessibilitySignal(
                sequence = eventCounter.incrementAndGet(),
                packageName = packageName,
                eventType = event.eventType,
            ),
        )
    }

    fun currentRoot(): AccessibilityNodeInfo? = _service.value?.rootInActiveWindow

    fun isConnected(): Boolean = _service.value != null
}
