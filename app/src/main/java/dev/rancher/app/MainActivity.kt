package dev.rancher.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import dev.rancher.android.accessibility.AccessibilityBridge
import dev.rancher.debug.harness.RancherDevHarnessScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    RancherDevHarnessScreen(
                        onStartSettingsOverlayDemo = ::startSettingsOverlayDemo,
                        onStopOverlay = DebugOverlayController::hide,
                    )
                }
            }
        }
    }

    private fun startSettingsOverlayDemo() {
        val service = AccessibilityBridge.service.value ?: return
        DebugOverlayController.show(service)
        startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}
