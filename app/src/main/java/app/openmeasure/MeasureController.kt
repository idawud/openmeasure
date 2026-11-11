package app.openmeasure

import app.openmeasure.ar.ArCameraView
import app.openmeasure.ar.ArHit
import app.openmeasure.ar.ArSessionManager
import app.openmeasure.ar.HitTestService
import app.openmeasure.measure.coaching.CoachingState
import app.openmeasure.measure.coaching.CoachingTracker
import app.openmeasure.measure.confidence.EndpointObservation
import app.openmeasure.measure.confidence.TrackingStatus
import app.openmeasure.measure.geometry.Vec3
import app.openmeasure.measure.hit.HitResult
import app.openmeasure.measure.label.LabelContent
import app.openmeasure.measure.label.MeasureLabel
import app.openmeasure.measure.model.PointId
import app.openmeasure.measure.projection.ScreenPoint
import app.openmeasure.measure.projection.ScreenProjection
import app.openmeasure.measure.projection.ScreenSegment
import app.openmeasure.measure.session.MeasurementSession
import com.google.ar.core.Anchor
import com.google.ar.core.Frame
import com.google.ar.core.TrackingState
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Per-frame UI snapshot of the point-to-point measurement (screen-space, view pixels). */
data class MeasureUiState(
    val coaching: CoachingState = CoachingState.MOVE_SLOWLY,
    /** Placed points projected to the screen (0..2); null entries are behind the camera. */
    val dots: List<ScreenPoint?> = emptyList(),
    /** Line between start and end (or start and reticle while placing). */
    val segment: ScreenSegment? = null,
    /** True while the end is the live reticle position rather than a placed point. */
    val live: Boolean = false,
    /** Null until at least one point is placed. Numbers only inside [LabelContent.Length]. */
    val label: LabelContent? = null,
    val pointCount: Int = 0,
)

/**
 * Point-to-point controller (F1). Runs on the GL thread via [ArCameraView.onFrame]; each placed point is backed
 * by an ARCore [Anchor] whose pose is fed into [MeasurementSession.updatePoints] every frame.
 */
class MeasureController(private val view: ArCameraView, private val manager: ArSessionManager) {
    private class Endpoint(val id: PointId, val anchor: Anchor, val hit: HitResult)

    private val service = HitTestService()
    private val coachingTracker = CoachingTracker()
    private val session = MeasurementSession()
    private val endpoints = ArrayList<Endpoint>(2)
    private val pendingPlace = AtomicBoolean(false)
    private val pendingReset = AtomicBoolean(false)
    private val viewM = FloatArray(16)
    private val projM = FloatArray(16)

    private val _state = MutableStateFlow(MeasureUiState())
    val state: StateFlow<MeasureUiState> = _state.asStateFlow()

    fun attach() { view.onFrame = ::onFrame }
    fun detach() { view.onFrame = null }

    /** Drop the next point at the reticle on the next frame (ignored unless coaching is READY). */
    fun place() { pendingPlace.set(true) }
    fun reset() { pendingReset.set(true) }

    private fun clearAll() {
        endpoints.forEach { it.anchor.detach() }
        endpoints.clear()
        session.clear()
    }

    private fun onFrame(frame: Frame) {
        if (pendingReset.getAndSet(false)) clearAll()
        val tracking = frame.camera.trackingState
        val status = when (tracking) {
            TrackingState.TRACKING -> TrackingStatus.TRACKING
            TrackingState.PAUSED -> TrackingStatus.PAUSED
            else -> TrackingStatus.STOPPED
        }
        val w = view.width.toFloat()
        val h = view.height.toFloat()
        val liveHit: ArHit = service.hitTestCenter(frame, view.width, view.height)
        val live = liveHit.result
        val coaching = coachingTracker.update(System.nanoTime() / 1_000_000, status, live)

        if (pendingPlace.getAndSet(false) && coaching == CoachingState.READY) place(liveHit)

        // Anchors keep points glued to the world as tracking refines.
        val updates = HashMap<PointId, Vec3>()
        for (e in endpoints) {
            if (e.anchor.trackingState == TrackingState.TRACKING) {
                val t = e.anchor.pose
                updates[e.id] = Vec3(t.tx(), t.ty(), t.tz())
            }
        }
        if (updates.isNotEmpty()) session.updatePoints(updates)

        frame.camera.getViewMatrix(viewM, 0)
        frame.camera.getProjectionMatrix(projM, 0, NEAR, FAR)
        val camPos = frame.camera.pose.let { Vec3(it.tx(), it.ty(), it.tz()) }
        val pts = session.state.points
        val dots = pts.map { ScreenProjection.project(it.position, viewM, projM, w, h) }

        var segment: ScreenSegment? = null
        var label: LabelContent? = null
        var isLive = false
        if (pts.size >= 2) {
            segment = ScreenProjection.projectSegment(pts[0].position, pts[1].position, viewM, projM, w, h)
            label = MeasureLabel.build(
                status, observe(0, camPos), observe(1, camPos), session.state.totalLengthMeters,
            )
        } else if (pts.size == 1) {
            isLive = true
            val end = live.position
            if (end != null) {
                segment = ScreenProjection.projectSegment(pts[0].position, end, viewM, projM, w, h)
                label = MeasureLabel.build(
                    status, observe(0, camPos), EndpointObservation.from(live),
                    pts[0].position.distanceTo(end).toDouble(),
                )
            } else {
                label = MeasureLabel.build(status, observe(0, camPos), EndpointObservation.NONE, 0.0)
            }
        }
        _state.value = MeasureUiState(coaching, dots, segment, isLive, label, pts.size)
    }

    private fun place(hit: ArHit) {
        val sess = manager.session ?: return
        val pos = hit.result.position ?: return
        if (endpoints.size >= 2) clearAll()
        val anchor = hit.createAnchor(sess) ?: return
        val id = session.addPoint(pos, hit.result.confidence)
        endpoints += Endpoint(id, anchor, hit.result)
        if (endpoints.size == 2) {
            val cm = session.state.totalLengthMeters * 100.0
            android.util.Log.i(
                "OpenMeasureSpike",
                "dist=%.1f cm source=%s/%s conf=%.2f/%.2f".format(
                    cm, endpoints[0].hit.source, hit.result.source,
                    endpoints[0].hit.confidence, hit.result.confidence,
                ),
            )
        }
    }

    /** Endpoint as seen now: original hit source/confidence, camera distance from the current camera pose. */
    private fun observe(index: Int, camPos: Vec3): EndpointObservation {
        val e = endpoints.getOrNull(index) ?: return EndpointObservation.NONE
        val p = session.state.point(e.id)?.position ?: return EndpointObservation.NONE
        return EndpointObservation(e.hit.source, e.hit.confidence, camPos.distanceTo(p))
    }

    private companion object {
        const val NEAR = 0.05f
        const val FAR = 100f
    }
}
