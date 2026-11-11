package app.openmeasure

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.openmeasure.ar.ArCameraView
import app.openmeasure.data.HistoryItem
import app.openmeasure.data.HistoryRepository
import app.openmeasure.ui.OmColors
import app.openmeasure.ui.PillButton
import kotlinx.coroutines.launch

/** Save-to-history action shared by the Save pill and the detail sheet. */
class HistorySaver {
    var busy by mutableStateOf(false)
        internal set
    internal var action: () -> Unit = {}
    fun save() = action()
}

/**
 * Builds a [HistorySaver]. With [photo] (Measure screen) it also captures the camera + overlay photo and stores its Uri; photo
 * failure still saves the measurement. Without it (Height) the item from [build] is stored as is.
 */
@Composable
fun rememberHistorySaver(
    snackbar: SnackbarHostState,
    photo: Pair<ArCameraView, GraphicsLayer>? = null,
    build: (photoUri: String?) -> HistoryItem?,
): HistorySaver {
    val context = LocalContext.current
    val repo = remember { HistoryRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val saver = remember { HistorySaver() }
    val ok = stringResource(R.string.history_saved)
    val failed = stringResource(R.string.history_save_failed)
    SideEffect {
        saver.action = action@{
            if (saver.busy) return@action
            saver.busy = true
            scope.launch {
                val message = try {
                    val uri = photo?.takeIf { (cam, _) -> cam.hasFrame }?.let { (cam, layer) ->
                        CaptureController.captureAndSave(context, cam, layer)
                    }
                    val item = build(uri?.toString())
                    if (item == null) failed else { repo.save(item); ok }
                } catch (e: Exception) {
                    failed
                } finally {
                    saver.busy = false
                }
                snackbar.showSnackbar(message)
            }
        }
    }
    return saver
}

/** "Save" pill; see [rememberHistorySaver]. */
@Composable
fun HistorySaveButton(
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    photo: Pair<ArCameraView, GraphicsLayer>? = null,
    container: Color = OmColors.OverlayLabel,
    build: (photoUri: String?) -> HistoryItem?,
) {
    val saver = rememberHistorySaver(snackbar, photo, build)
    PillButton(
        stringResource(R.string.history_save), saver::save, modifier,
        enabled = enabled && !saver.busy, description = stringResource(R.string.history_save_desc), container = container,
    )
}
