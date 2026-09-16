package dev.rancher.android.snapshot

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import dev.rancher.android.accessibility.AccessibilityBridge
import dev.rancher.core.model.UiBounds
import dev.rancher.core.model.UiNode
import dev.rancher.core.model.UiSnapshot
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

object UiSnapshotEngine {
    private const val TAG = "RancherSnapshot"
    private const val MAX_TRAVERSED_NODES = 3_000
    private const val REDACTED = "[REDACTED]"

    private val nextSnapshotNumber = AtomicLong(0)
    private val _currentSnapshot = MutableStateFlow<UiSnapshot?>(null)
    val currentSnapshot: StateFlow<UiSnapshot?> = _currentSnapshot.asStateFlow()

    private val lock = Any()
    private var currentHandles: Map<Int, NodeHandle> = emptyMap()

    suspend fun capture(): UiSnapshot? = withContext(Dispatchers.Default) {
        val root = AccessibilityBridge.currentRoot() ?: return@withContext null
        try {
            buildSnapshot(root)
        } finally {
            root.recycleSafely()
        }
    }

    fun resolve(snapshotId: String, nodeId: Int): NodeResolution {
        val snapshot: UiSnapshot
        val handle: NodeHandle
        synchronized(lock) {
            snapshot = _currentSnapshot.value ?: return NodeResolution.StaleSnapshot
            if (snapshot.id != snapshotId) return NodeResolution.StaleSnapshot
            handle = currentHandles[nodeId] ?: return NodeResolution.NotFound
        }

        val root = AccessibilityBridge.currentRoot() ?: return NodeResolution.NotFound
        var current: AccessibilityNodeInfo = root

        try {
            for (childIndex in handle.path) {
                val next = current.getChild(childIndex) ?: return NodeResolution.NotFound.also {
                    current.recycleSafely()
                }
                if (current !== root) current.recycleSafely()
                current = next
            }

            if (!matchesFingerprint(current, handle.fingerprint)) {
                current.recycleSafely()
                return NodeResolution.StaleSnapshot
            }

            // Caller owns and must recycle this node.
            return NodeResolution.Found(current)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to resolve semantic node", t)
            if (current !== root) current.recycleSafely()
            return NodeResolution.NotFound
        } finally {
            if (current !== root) root.recycleSafely()
        }
    }

    private fun buildSnapshot(root: AccessibilityNodeInfo): UiSnapshot {
        val candidates = mutableListOf<RawNode>()
        var visited = 0

        fun visit(node: AccessibilityNodeInfo, path: List<Int>, parentRawIndex: Int?) {
            if (visited++ >= MAX_TRAVERSED_NODES) return

            val rect = Rect()
            node.getBoundsInScreen(rect)
            val bounds = rect.toUiBounds()
            val visible = node.isVisibleToUser && bounds.width > 0 && bounds.height > 0

            val rawIndex = candidates.size
            val raw = RawNode(
                rawIndex = rawIndex,
                parentRawIndex = parentRawIndex,
                path = path,
                text = safeText(node),
                contentDescription = safeContentDescription(node),
                viewId = node.viewIdResourceName,
                className = node.className?.toString(),
                clickable = node.isClickable,
                longClickable = node.isLongClickable,
                editable = node.isEditable,
                scrollable = node.isScrollable,
                enabled = node.isEnabled,
                selected = node.isSelected,
                checked = if (node.isCheckable) node.isChecked else null,
                password = node.isPassword,
                bounds = bounds,
                visible = visible,
            )
            candidates += raw

            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                try {
                    visit(child, path + index, rawIndex)
                } finally {
                    child.recycleSafely()
                }
            }
        }

        visit(root, emptyList(), null)

        val usefulRawIndices = candidates
            .asSequence()
            .filter { it.visible && it.isSemanticallyUseful() }
            .map { it.rawIndex }
            .toSet()

        val semanticIdByRawIndex = linkedMapOf<Int, Int>()
        usefulRawIndices.sorted().forEachIndexed { index, rawIndex ->
            semanticIdByRawIndex[rawIndex] = index + 1
        }

        fun nearestSemanticParent(raw: RawNode): Int? {
            var cursor = raw.parentRawIndex
            while (cursor != null) {
                semanticIdByRawIndex[cursor]?.let { return it }
                cursor = candidates.getOrNull(cursor)?.parentRawIndex
            }
            return null
        }

        val childrenByParent = mutableMapOf<Int, MutableList<Int>>()
        val nodes = usefulRawIndices
            .sorted()
            .map { rawIndex ->
                val raw = candidates[rawIndex]
                val id = semanticIdByRawIndex.getValue(rawIndex)
                val parentId = nearestSemanticParent(raw)
                if (parentId != null) childrenByParent.getOrPut(parentId) { mutableListOf() } += id

                UiNode(
                    id = id,
                    text = raw.text,
                    contentDescription = raw.contentDescription,
                    viewId = raw.viewId,
                    className = raw.className,
                    clickable = raw.clickable,
                    longClickable = raw.longClickable,
                    editable = raw.editable,
                    scrollable = raw.scrollable,
                    enabled = raw.enabled,
                    selected = raw.selected,
                    checked = raw.checked,
                    password = raw.password,
                    bounds = raw.bounds,
                    parentId = parentId,
                    childIds = emptyList(),
                )
            }
            .map { it.copy(childIds = childrenByParent[it.id].orEmpty()) }

        val handles = usefulRawIndices.sorted().associate { rawIndex ->
            val raw = candidates[rawIndex]
            semanticIdByRawIndex.getValue(rawIndex) to NodeHandle(
                path = raw.path,
                fingerprint = NodeFingerprint(
                    viewId = raw.viewId,
                    className = raw.className,
                    text = raw.text,
                    contentDescription = raw.contentDescription,
                    bounds = raw.bounds,
                    clickable = raw.clickable,
                    enabled = raw.enabled,
                ),
            )
        }

        val packageName = root.packageName?.toString()
            ?: AccessibilityBridge.activePackage.value
            ?: "<unknown>"

        val title = nodes.firstOrNull { node ->
            node.className?.lowercase(Locale.US)?.contains("toolbar") == true && !node.label.isNullOrBlank()
        }?.label

        val snapshot = UiSnapshot(
            id = "snap_${nextSnapshotNumber.incrementAndGet().toString().padStart(6, '0')}",
            createdAt = System.currentTimeMillis(),
            packageName = packageName,
            windowTitle = title,
            nodes = nodes,
        )

        synchronized(lock) {
            currentHandles = handles
            _currentSnapshot.value = snapshot
        }

        Log.i(TAG, "captured ${snapshot.id} package=${snapshot.packageName} semanticNodes=${nodes.size} rawNodes=${candidates.size}")
        return snapshot
    }

    private fun safeText(node: AccessibilityNodeInfo): String? =
        if (node.isPassword) REDACTED else node.text?.toString()?.normalizeLabel()

    private fun safeContentDescription(node: AccessibilityNodeInfo): String? =
        if (node.isPassword) REDACTED else node.contentDescription?.toString()?.normalizeLabel()

    private fun String.normalizeLabel(): String? =
        trim().replace(Regex("\\s+"), " ").takeIf { it.isNotBlank() }?.take(500)

    private fun RawNode.isSemanticallyUseful(): Boolean =
        clickable ||
            longClickable ||
            editable ||
            scrollable ||
            selected ||
            checked != null ||
            !text.isNullOrBlank() ||
            !contentDescription.isNullOrBlank() ||
            !viewId.isNullOrBlank()

    private fun matchesFingerprint(node: AccessibilityNodeInfo, expected: NodeFingerprint): Boolean {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val actualBounds = rect.toUiBounds()
        val actualText = safeText(node)
        val actualDescription = safeContentDescription(node)

        val identityMatches = when {
            !expected.viewId.isNullOrBlank() -> node.viewIdResourceName == expected.viewId
            !expected.text.isNullOrBlank() -> actualText == expected.text
            !expected.contentDescription.isNullOrBlank() -> actualDescription == expected.contentDescription
            else -> node.className?.toString() == expected.className && actualBounds == expected.bounds
        }

        return identityMatches &&
            node.className?.toString() == expected.className &&
            node.isClickable == expected.clickable &&
            node.isEnabled == expected.enabled &&
            boundsCloseEnough(actualBounds, expected.bounds)
    }

    private fun boundsCloseEnough(actual: UiBounds, expected: UiBounds): Boolean {
        val tolerance = 8
        return kotlin.math.abs(actual.left - expected.left) <= tolerance &&
            kotlin.math.abs(actual.top - expected.top) <= tolerance &&
            kotlin.math.abs(actual.right - expected.right) <= tolerance &&
            kotlin.math.abs(actual.bottom - expected.bottom) <= tolerance
    }

    private fun Rect.toUiBounds() = UiBounds(left, top, right, bottom)

    private data class RawNode(
        val rawIndex: Int,
        val parentRawIndex: Int?,
        val path: List<Int>,
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
        val visible: Boolean,
    )
}

@Suppress("DEPRECATION")
private fun AccessibilityNodeInfo.recycleSafely() {
    try {
        recycle()
    } catch (_: Throwable) {
        // recycle() is deprecated on newer APIs because framework-managed instances are pooled safely.
        // Calling it remains harmless on older devices and keeps M0 compatible back to minSdk 26.
    }
}
