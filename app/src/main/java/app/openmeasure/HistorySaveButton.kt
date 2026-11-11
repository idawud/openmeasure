package app.openmeasure

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.openmeasure.ar.ArCameraView
import app.openmeasure.data.HistoryItem
import app.openmeasure.data.HistoryRepository
import kotlinx.coroutines.launch

/**
 * Save-to-history action. With a [candidate] (Measure screen) it also captures the camera + overlay photo and stores
 * its Uri; photo failure still saves the measurement. Without a camera/layer (Height) [itemWithoutPhoto] is stored as is.
 */
@Composable
fun HistorySaveButton(
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    photo: Pair<ArCameraView, GraphicsLayer>? = null,
    build: (photoUri: String?) -> HistoryItem?,
) {
    val context = LocalContext.current
    val repo = remember { HistoryRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val desc = stringResource(R.string.history_save_desc)
    val ok = stringResource(R.string.history_saved)
    val failed = stringResource(R.string.history_save_failed)
    Button(
        onClick = {
            if (busy) return@Button
            busy = true
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
                    busy = false
                }
                snackbar.showSnackbar(message)
            }
        },
        enabled = enabled && !busy,
        modifier = modifier.heightIn(min = 48.dp).widthIn(min = 48.dp).semantics { contentDescription = desc },
    ) { Text(stringResource(R.string.history_save)) }
}
