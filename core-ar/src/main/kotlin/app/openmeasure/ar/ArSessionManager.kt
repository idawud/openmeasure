package app.openmeasure.ar

import android.app.Activity
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Lifecycle state of the AR session. */
sealed interface ArSessionState {
    data object Idle : ArSessionState
    data object NeedsInstall : ArSessionState
    data object NeedsPermission : ArSessionState
    data object Running : ArSessionState
    data class Error(val message: String) : ArSessionState
}

/**
 * Owns the ARCore [Session]. Call [onResume]/[onPause] from the host lifecycle,
 * [onCameraPermissionResult] after the permission prompt, and [close] on teardown.
 */
class ArSessionManager {
    private val _state = MutableStateFlow<ArSessionState>(ArSessionState.Idle)
    val state: StateFlow<ArSessionState> = _state.asStateFlow()

    var session: Session? = null
        private set

    var isDepthSupported: Boolean = false
        private set

    private var installRequested = false
    private var cameraGranted = false

    fun onCameraPermissionResult(granted: Boolean) {
        cameraGranted = granted
        if (!granted) _state.value = ArSessionState.NeedsPermission
    }

    fun onResume(activity: Activity, hasCameraPermission: Boolean) {
        cameraGranted = hasCameraPermission
        if (!cameraGranted) {
            _state.value = ArSessionState.NeedsPermission
            return
        }
        try {
            if (session == null) {
                val status = ArCoreApk.getInstance().requestInstall(activity, !installRequested)
                if (status == ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
                    installRequested = true
                    _state.value = ArSessionState.NeedsInstall
                    return
                }
                session = Session(activity).also(::configure)
            }
            session?.resume()
            _state.value = ArSessionState.Running
        } catch (e: Exception) {
            session?.close()
            session = null
            _state.value = ArSessionState.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    fun onPause() {
        session?.pause()
        if (_state.value == ArSessionState.Running) _state.value = ArSessionState.Idle
    }

    fun close() {
        session?.close()
        session = null
        _state.value = ArSessionState.Idle
    }

    private fun configure(s: Session) {
        isDepthSupported = s.isDepthModeSupported(Config.DepthMode.AUTOMATIC)
        s.configure(
            s.config.apply {
                planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                depthMode = if (isDepthSupported) Config.DepthMode.AUTOMATIC else Config.DepthMode.DISABLED
                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            },
        )
    }
}
