package app.openmeasure

import android.util.Log
import app.openmeasure.ar.ArCameraView
import app.openmeasure.ar.HitTestService
import app.openmeasure.measure.geometry.Vec3
import app.openmeasure.measure.hit.HitSource
import com.google.ar.core.Frame
import com.google.ar.core.TrackingState
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** UI snapshot for the T07 spike overlay. */
data class SpikeUiState(
    val tracking: TrackingState = TrackingState.STOPPED,
    val source: HitSource = HitSource.NONE,
    val confidence: Float = 0f,
    val pointA: Vec3? = null,
    val pointB: Vec3? = null,
    val distanceCm: Float? = null,
    val distanceSource: HitSource = HitSource.NONE,
    val distanceConfidence: Float = 0f,
)

/** Runs hit tests on the GL thread (via ArCameraView.onFrame) and publishes state for Compose. */
class SpikeController(private val view: ArCameraView) {
    private val service = HitTestService()
    private val _state = MutableStateFlow(SpikeUiState())
    val state: StateFlow<SpikeUiState> = _state.asStateFlow()

    /** Tap in view px; null x/y => reticle. Consumed on the next GL frame. */
    private class Tap(val x: Float?, val y: Float?)
    private val pendingTap = AtomicReference<Tap?>(null)
    @Volatile private var resetRequested = false
    private var a: Vec3? = null
    private var b: Vec3? = null
    private var dist: Float? = null
    private var distSource = HitSource.NONE
    private var distConf = 0f

    fun attach() { view.onFrame = ::onFrame }
    fun detach() { view.onFrame = null }

    fun tap(x: Float? = null, y: Float? = null) { pendingTap.set(Tap(x, y)) }
    fun reset() { resetRequested = true }

    private fun onFrame(frame: Frame) {
        if (resetRequested) {
            resetRequested = false
            a = null; b = null; dist = null; distSource = HitSource.NONE; distConf = 0f
        }
        val tracking = frame.camera.trackingState
        val w = view.width
        val h = view.height
        val live = service.hitTestCenter(frame, w, h).result

        pendingTap.getAndSet(null)?.let { tap ->
            val hit = if (tap.x != null && tap.y != null) service.hitTest(frame, tap.x, tap.y).result else live
            val pos = hit.position
            if (tracking == TrackingState.TRACKING && hit.isHit && pos != null) {
                if (a == null || b != null) { a = pos; b = null; dist = null }
                else {
                    b = pos
                    val cm = a!!.distanceTo(pos) * 100f
                    dist = cm
                    distSource = hit.source
                    distConf = hit.confidence
                    Log.i("OpenMeasureSpike", "dist=%.1f cm source=%s conf=%.2f".format(cm, hit.source, hit.confidence))
                }
            }
        }
        _state.value = SpikeUiState(tracking, live.source, live.confidence, a, b, dist, distSource, distConf)
    }
}
