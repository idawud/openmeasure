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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.openmeasure.ar.ArSessionManager
import app.openmeasure.ar.ArSessionState

/** Requests CAMERA, returns a launcher lambda; informs [manager] of the result. */
@Composable
fun rememberCameraPermissionRequester(manager: ArSessionManager, onResult: (Boolean) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        manager.onCameraPermissionResult(granted)
        onResult(granted)
    }
    return { launcher.launch(Manifest.permission.CAMERA) }
}

/** Shows AR session state as text only (camera rendering arrives in T05). */
@Composable
fun ArScreen() {
    val context = LocalContext.current
    val activity = context as Activity
    val manager = remember { ArSessionManager() }
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
            manager.close()
        }
    }

    val text = when (val s = state) {
        ArSessionState.Idle -> "AR idle"
        ArSessionState.NeedsInstall -> "Install Google Play Services for AR"
        ArSessionState.NeedsPermission -> "Camera permission required"
        ArSessionState.Running -> "AR running (depth: ${manager.isDepthSupported})"
        is ArSessionState.Error -> "AR error: ${s.message}"
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text) }
}
