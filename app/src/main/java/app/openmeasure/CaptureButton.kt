package app.openmeasure

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.openmeasure.ar.ArCameraView
import kotlinx.coroutines.launch

/** Captures camera + overlay, saves to Pictures/OpenMeasure and offers Share via the snackbar; shared by the shutter and the sheet. */
class CaptureAction {
    var busy by mutableStateOf(false)
        internal set
    internal var action: (Boolean) -> Unit = {}

    /** [shareNow] opens the share sheet right after saving instead of offering it in a snackbar. */
    fun run(shareNow: Boolean = false) = action(shareNow)
}

@Composable
fun rememberCaptureAction(layer: GraphicsLayer, camera: ArCameraView, haptics: Boolean, snackbar: SnackbarHostState): CaptureAction {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val saved = stringResource(R.string.capture_saved)
    val failed = stringResource(R.string.capture_failed)
    val share = stringResource(R.string.capture_share)
    val capture = remember { CaptureAction() }
    capture.action = action@{ shareNow ->
        if (capture.busy || !camera.hasFrame) return@action
        capture.busy = true
        if (haptics) view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.KEYBOARD_TAP,
        )
        scope.launch {
            val uri = try { CaptureController.captureAndSave(context, camera, layer) } finally { capture.busy = false }
            if (uri == null) {
                snackbar.showSnackbar(failed)
            } else if (shareNow || snackbar.showSnackbar(saved, share) == SnackbarResult.ActionPerformed) {
                context.startActivity(ShareHelper.shareIntent(context, uri))
            }
        }
    }
    return capture
}

/** Small white-ringed circle (shutter): takes a photo with the measurements. */
@Composable
fun CaptureButton(capture: CaptureAction, modifier: Modifier = Modifier) {
    val desc = stringResource(R.string.capture_desc)
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .size(44.dp)
            .alpha(if (capture.busy) 0.4f else 1f)
            .border(BorderStroke(2.dp, Color.White), CircleShape)
            .padding(5.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.9f))
            .clickable(enabled = !capture.busy, role = Role.Button) { capture.run() }
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {}
}
