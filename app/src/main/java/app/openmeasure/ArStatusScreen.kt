package app.openmeasure

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.openmeasure.ar.ArSessionState
import app.openmeasure.ui.Dimens
import app.openmeasure.ui.HintBanner
import app.openmeasure.ui.OmColors
import app.openmeasure.ui.PillButton

/** Shown instead of the camera while the AR session is not running: spinner, install / error notice, or the friendly camera-permission screen. */
@Composable
fun ArStatusScreen(state: ArSessionState, onRequestPermission: () -> Unit) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize().background(OmColors.Black).windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.Center) {
        when (state) {
            ArSessionState.NeedsPermission -> Column(
                Modifier.widthIn(max = 400.dp).padding(Dimens.margin * 2),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.camera_title), style = MaterialTheme.typography.headlineMedium, color = Color.White, textAlign = TextAlign.Center)
                Text(
                    stringResource(R.string.camera_rationale), style = MaterialTheme.typography.bodyLarge,
                    color = OmColors.TextSecondary, textAlign = TextAlign.Center,
                )
                PillButton(stringResource(R.string.camera_open_settings), { openAppSettings(context) }, Modifier.padding(top = 12.dp), filled = true)
                PillButton(stringResource(R.string.camera_allow), onRequestPermission, container = OmColors.SurfaceHigh)
            }
            ArSessionState.NeedsInstall -> HintBanner(stringResource(R.string.ar_install_services), Modifier.padding(Dimens.margin))
            is ArSessionState.Error -> HintBanner(stringResource(R.string.ar_error, state.message), Modifier.padding(Dimens.margin))
            else -> CircularProgressIndicator(color = OmColors.Accent)
        }
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // No settings app available: nothing to do.
    }
}
