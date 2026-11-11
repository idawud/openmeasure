package app.openmeasure

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import app.openmeasure.settings.SettingsRepository
import app.openmeasure.ui.OmColors
import app.openmeasure.ui.OpenMeasureTheme

class MainActivity : ComponentActivity() {
    private val settingsRepository by lazy { SettingsRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Edge-to-edge over the camera: transparent bars with light icons (the app is dark only).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        super.onCreate(savedInstanceState)
        setContent {
            OpenMeasureTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = OmColors.Black) {
                    AppRoot(settingsRepository = settingsRepository)
                }
            }
        }
    }
}
