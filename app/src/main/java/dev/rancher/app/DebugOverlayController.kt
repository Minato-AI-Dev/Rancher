package dev.rancher.app

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.rancher.android.actions.AndroidActionExecutor
import dev.rancher.android.snapshot.UiSnapshotEngine
import dev.rancher.core.model.ToolResult
import dev.rancher.core.model.UiNode
import dev.rancher.core.model.UiSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Developer-only accessibility overlay used for the M0 Settings acceptance demo.
 *
 * A normal Rancher Activity would take focus away from Android Settings. TYPE_ACCESSIBILITY_OVERLAY
 * keeps the target application visible/active while still giving the developer Refresh/CLICK
 * controls. This is not the future Rancher product UI or kill switch.
 */
object DebugOverlayController {
    private var windowManager: WindowManager? = null
    private var rootView: View? = null
    private var scope: CoroutineScope? = null
    private var collector: Job? = null
    private var status: ToolResult? = null

    fun show(service: AccessibilityService) {
        if (rootView != null) return

        val wm = service.getSystemService(WindowManager::class.java)
        val overlayScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val panel = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(service, 10), dp(service, 10), dp(service, 10), dp(service, 10))
            setBackgroundColor(Color.argb(244, 255, 255, 255))
            elevation = dp(service, 8).toFloat()
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            dp(service, 330),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM
        }

        windowManager = wm
        rootView = panel
        scope = overlayScope
        wm.addView(panel, params)

        render(service, panel, UiSnapshotEngine.currentSnapshot.value)
        collector = overlayScope.launch {
            UiSnapshotEngine.currentSnapshot.collectLatest { snapshot ->
                render(service, panel, snapshot)
            }
        }

        overlayScope.launch {
            UiSnapshotEngine.capture()
        }
    }

    fun hide() {
        collector?.cancel()
        collector = null
        rootView?.let { view ->
            runCatching { windowManager?.removeView(view) }
        }
        rootView = null
        windowManager = null
        status = null
        scope?.cancel()
        scope = null
    }

    private fun render(service: AccessibilityService, panel: LinearLayout, snapshot: UiSnapshot?) {
        panel.removeAllViews()

        panel.addView(text(service, "Rancher M0 Overlay", 17f, bold = true))
        panel.addView(
            text(
                service,
                "Snapshot: ${snapshot?.id ?: "<none>"}\nPackage: ${snapshot?.packageName ?: "<none>"}",
                12f,
            ),
        )

        val controls = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        controls.addView(Button(service).apply {
            text = "Refresh"
            setOnClickListener {
                scope?.launch { UiSnapshotEngine.capture() }
            }
        })
        controls.addView(Button(service).apply {
            text = "Close"
            setOnClickListener { hide() }
        })
        panel.addView(controls)

        status?.let { result ->
            panel.addView(
                text(
                    service,
                    "${result.status}: ${result.message.orEmpty()}\n${result.previousSnapshotId ?: "-"} → ${result.newSnapshotId ?: "-"}",
                    11f,
                ),
            )
        }

        if (snapshot == null) {
            panel.addView(text(service, "Tap Refresh while Android Settings is visible.", 13f))
            return
        }

        val list = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
        }

        snapshot.nodes.take(100).forEach { node ->
            list.addView(nodeRow(service, snapshot.id, node))
        }

        val scroll = ScrollView(service).apply {
            addView(list)
            isFillViewport = true
        }
        panel.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )
    }

    private fun nodeRow(
        service: AccessibilityService,
        snapshotId: String,
        node: UiNode,
    ): View {
        return LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(service, 6), 0, dp(service, 6))

            addView(
                text(
                    service,
                    "#${node.id} ${node.label ?: "<unlabeled>"}",
                    13f,
                    bold = node.clickable,
                ),
            )
            addView(
                text(
                    service,
                    "clickable=${node.clickable} enabled=${node.enabled}\n${node.className ?: "-"}",
                    10f,
                ),
            )

            if (node.clickable) {
                addView(Button(service).apply {
                    text = "CLICK #${node.id}"
                    isEnabled = node.enabled
                    setOnClickListener {
                        isEnabled = false
                        scope?.launch {
                            status = AndroidActionExecutor.click(snapshotId, node.id)
                            render(service, rootView as? LinearLayout ?: return@launch, UiSnapshotEngine.currentSnapshot.value)
                        }
                    }
                })
            }
        }
    }

    private fun text(
        service: AccessibilityService,
        value: String,
        sizeSp: Float,
        bold: Boolean = false,
    ) = TextView(service).apply {
        text = value
        textSize = sizeSp
        setTextColor(Color.BLACK)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun dp(service: AccessibilityService, value: Int): Int =
        (value * service.resources.displayMetrics.density).toInt()
}
