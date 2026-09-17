package dev.rancher.android.snapshot

import dev.rancher.core.model.UiBounds
import dev.rancher.core.model.UiNode
import dev.rancher.core.model.UiSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiSnapshotEngineTest {

    @Test
    fun testPasswordRedaction_replacesPasswordWithRedactedText() {
        // Test 8 requirement: password node text must be [REDACTED]
        val redactedText = UiSnapshotEngine.sanitizeText(isPassword = true, rawText = "SuperSecretPassword123")
        assertEquals("[REDACTED]", redactedText)

        val redactedDesc = UiSnapshotEngine.sanitizeContentDescription(isPassword = true, rawDesc = "Password field description")
        assertEquals("[REDACTED]", redactedDesc)
    }

    @Test
    fun testPasswordRedaction_preservesNonPasswordText() {
        val normalText = UiSnapshotEngine.sanitizeText(isPassword = false, rawText = "Connected devices")
        assertEquals("Connected devices", normalText)

        val normalDesc = UiSnapshotEngine.sanitizeContentDescription(isPassword = false, rawDesc = "Bluetooth, pairing")
        assertEquals("Bluetooth, pairing", normalDesc)
    }

    @Test
    fun testPasswordRedaction_inUiNodeModel() {
        // Verify building a UiNode with password flag set
        val passwordNode = UiNode(
            id = 1,
            text = UiSnapshotEngine.sanitizeText(isPassword = true, rawText = "MySecret123"),
            contentDescription = UiSnapshotEngine.sanitizeContentDescription(isPassword = true, rawDesc = "Enter PIN"),
            viewId = "com.android.settings:id/password",
            className = "android.widget.EditText",
            clickable = true,
            longClickable = false,
            editable = true,
            scrollable = false,
            enabled = true,
            selected = false,
            checked = null,
            password = true,
            bounds = UiBounds(0, 0, 100, 50),
            parentId = null,
            childIds = emptyList(),
        )

        assertEquals("[REDACTED]", passwordNode.text)
        assertEquals("[REDACTED]", passwordNode.contentDescription)
        assertTrue(passwordNode.password)
    }

    @Test
    fun testStaleSnapshotResolution_returnsStaleWhenSnapshotMismatch() {
        // Test 7 requirement: resolution with stale snapshotId returns StaleSnapshot
        val currentSnap = UiSnapshot(
            id = "snap_current",
            createdAt = System.currentTimeMillis(),
            packageName = "com.android.settings",
            windowTitle = "Settings",
            nodes = emptyList(),
        )
        UiSnapshotEngine.setCurrentSnapshotForTesting(currentSnap)

        val resolution = UiSnapshotEngine.resolve(snapshotId = "snap_old", nodeId = 1)
        assertEquals(NodeResolution.StaleSnapshot, resolution)
    }
}