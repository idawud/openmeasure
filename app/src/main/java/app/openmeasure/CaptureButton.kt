package app.openmeasure

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.openmeasure.ar.ArCameraView
import kotlinx.coroutines.launch

/** Shutter button: captures camera + overlay, saves to Pictures/OpenMeasure, offers Share. */
@Composable
fun CaptureButton(layer: GraphicsLayer, camera: ArCameraView, haptics: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var busy by remember { mutableStateOf(false) }
    val desc = stringResource(R.string.capture_desc)
    val saved = stringResource(R.string.capture_saved)
    val failed = stringResource(R.string.capture_failed)
    val share = stringResource(R.string.capture_share)
    Box(modifier) {
        Button(
            onClick = {
                if (busy || !camera.hasFrame) return@Button
                busy = true
                if (haptics) view.performHapticFeedback(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.KEYBOARD_TAP,
                )
                scope.launch {
                    val uri = try { CaptureController.captureAndSave(context, camera, layer) } finally { busy = false }
                    if (uri == null) {
                        snackbar.showSnackbar(failed)
                    } else if (snackbar.showSnackbar(saved, share) == SnackbarResult.ActionPerformed) {
                        context.startActivity(ShareHelper.shareIntent(context, uri))
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).semantics { contentDescription = desc },
        ) { Text(stringResource(R.string.capture_label)) }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomEnd))
    }
}
