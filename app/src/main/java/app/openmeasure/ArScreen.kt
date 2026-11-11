package app.openmeasure

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.viewinterop.AndroidView
import app.openmeasure.ar.ArCameraView
import app.openmeasure.ar.ArSessionManager
import app.openmeasure.ar.ArSessionState
import app.openmeasure.settings.SettingsRepository

/** Requests CAMERA, returns a launcher lambda; informs [manager] of the result. */
@Composable
fun rememberCameraPermissionRequester(manager: ArSessionManager, onResult: (Boolean) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        manager.onCameraPermissionResult(granted)
        onResult(granted)
    }
    return { launcher.launch(Manifest.permission.CAMERA) }
}

/**
 * Camera view over the single shared AR session [manager] (owned by AppRoot, so Measure and Height reuse it).
 * Leaving the screen pauses the session; AppRoot closes it. [heightMode] shows the F10 height overlay instead of measuring.
 */
@Composable
fun ArScreen(settingsRepository: SettingsRepository, manager: ArSessionManager, heightMode: Boolean = false) {
    val context = LocalContext.current
    val activity = context as Activity
    val state by manager.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    fun hasPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    val requestPermission = rememberCameraPermissionRequester(manager) { granted ->
        if (granted) manager.onResume(activity, true)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    manager.onResume(activity, hasPermission())
                    if (!hasPermission()) requestPermission()
                }
                Lifecycle.Event.ON_PAUSE -> manager.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            manager.onPause()
        }
    }

    val cameraView = remember { ArCameraView(context, manager) }
    DisposableEffect(lifecycleOwner) {
        val glObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> cameraView.onResume()
                Lifecycle.Event.ON_PAUSE -> cameraView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(glObserver)
        onDispose { lifecycleOwner.lifecycle.removeObserver(glObserver); cameraView.onPause() }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (state == ArSessionState.Running && heightMode) {
            AndroidView(factory = { cameraView }, modifier = Modifier.fillMaxSize())
            HeightOverlay(cameraView, manager, settingsRepository)
        } else if (state == ArSessionState.Running) {
            val measure = remember { MeasureController(cameraView, manager) }
            DisposableEffect(measure) { measure.attach(); onDispose { measure.detach() } }
            AndroidView(factory = { cameraView }, modifier = Modifier.fillMaxSize())
            MeasureOverlay(measure, settingsRepository, cameraView)
        } else {
            val text = when (val s = state) {
                ArSessionState.NeedsInstall -> stringResource(R.string.ar_install_services)
                ArSessionState.NeedsPermission -> stringResource(R.string.ar_permission_required)
                is ArSessionState.Error -> stringResource(R.string.ar_error, s.message)
                else -> stringResource(R.string.ar_idle)
            }
            Text(text)
        }
    }
}
