package dev.rancher.debug.harness

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.rancher.android.accessibility.AccessibilityBridge
import dev.rancher.android.actions.AndroidActionExecutor
import dev.rancher.android.snapshot.UiSnapshotEngine
import dev.rancher.core.model.ToolResult
import dev.rancher.core.model.UiNode
import kotlinx.coroutines.launch

@Composable
fun RancherDevHarnessScreen(
    onStartSettingsOverlayDemo: () -> Unit,
    onStopOverlay: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val service by AccessibilityBridge.service.collectAsState()
    val activePackage by AccessibilityBridge.activePackage.collectAsState()
    val snapshot by UiSnapshotEngine.currentSnapshot.collectAsState()
    var lastResult by remember { mutableStateOf<ToolResult?>(null) }

    LaunchedEffect(service) {
        if (service != null) {
            UiSnapshotEngine.capture()
        }
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Rancher Dev Harness",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = if (service == null) {
                    "AccessibilityService: DISCONNECTED"
                } else {
                    "AccessibilityService: CONNECTED"
                },
                fontFamily = FontFamily.Monospace,
            )

            Text(
                text = "Active package: ${activePackage ?: "<none>"}",
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = "Snapshot: ${snapshot?.id ?: "<none>"} (${snapshot?.packageName ?: "<none>"})",
                fontFamily = FontFamily.Monospace,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { openAccessibilitySettings(context) }) {
                    Text("Accessibility Settings")
                }
                Button(
                    enabled = service != null,
                    onClick = onStartSettingsOverlayDemo,
                ) {
                    Text("M0 Settings demo")
                }
                OutlinedButton(onClick = onStopOverlay) {
                    Text("Hide overlay")
                }
            }

            Text(
                text = "For the real Settings demo, use “M0 Settings demo”. It keeps Settings active and shows a developer accessibility overlay with Refresh/CLICK controls.",
                style = MaterialTheme.typography.bodySmall,
            )

            Button(
                enabled = service != null,
                onClick = {
                    scope.launch {
                        lastResult = null
                        UiSnapshotEngine.capture()
                    }
                },
            ) {
                Text("Refresh current window")
            }

            lastResult?.let { result ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Last action: ${result.status}", fontWeight = FontWeight.SemiBold)
                        result.message?.let { Text(it) }
                        Text(
                            "${result.previousSnapshotId ?: "-"} → ${result.newSnapshotId ?: "-"} (${result.durationMs} ms)",
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }

            HorizontalDivider()

            if (snapshot == null) {
                Text("No semantic snapshot yet. Enable the service, then run the M0 Settings demo.")
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(snapshot!!.nodes, key = { it.id }) { node ->
                        NodeCard(
                            node = node,
                            snapshotId = snapshot!!.id,
                            onResult = { lastResult = it },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NodeCard(
    node: UiNode,
    snapshotId: String,
    onResult: (ToolResult) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var busy by remember(node.id, snapshotId) { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = "#${node.id} ${node.label ?: "<unlabeled>"}",
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = buildString {
                    append("class=${node.className ?: "-"}\n")
                    append("clickable=${node.clickable} enabled=${node.enabled} editable=${node.editable} scrollable=${node.scrollable}\n")
                    append("bounds=[${node.bounds.left},${node.bounds.top}][${node.bounds.right},${node.bounds.bottom}]")
                },
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
            )

            if (node.clickable) {
                Spacer(Modifier.height(8.dp))
                Button(
                    enabled = node.enabled && !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            try {
                                onResult(AndroidActionExecutor.click(snapshotId, node.id))
                            } finally {
                                busy = false
                            }
                        }
                    },
                ) {
                    Text(if (busy) "CLICKING…" else "CLICK")
                }
            }
        }
    }
}

private fun openAccessibilitySettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
    )
}
