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
import app.openmeasure.measure.label.PathTotal
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

/** One drawn line with its label; [live] = last placed point to the reticle. Numbers only inside Length. */
data class SegmentUi(val segment: ScreenSegment?, val label: LabelContent, val live: Boolean)

/** Per-frame UI snapshot of the measurement path (screen-space, view pixels). */
data class MeasureUiState(
    val coaching: CoachingState = CoachingState.MOVE_SLOWLY,
    /** Placed points projected to the screen; null entries are behind the camera. */
    val dots: List<ScreenPoint?> = emptyList(),
    /** Placed segments, plus a trailing live one while a path is being drawn. */
    val segments: List<SegmentUi> = emptyList(),
    /** Total metres; null unless >= 2 placed segments and all displayable. */
    val totalMeters: Double? = null,
    val pointCount: Int = 0,
    /** Path was ended with Done; the next placed point starts a new path. */
    val finished: Boolean = false,
    val canAdd: Boolean = true,
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
    private val endpoints = ArrayList<Endpoint>()
    private var finished = false
    private val pendingDone = AtomicBoolean(false)
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
    /** Finish the current path (keeps it on screen); the next point starts a new one. */
    fun done() { pendingDone.set(true) }

    private fun clearAll() {
        endpoints.forEach { it.anchor.detach() }
        endpoints.clear()
        session.clear()
        finished = false
    }

    private fun onFrame(frame: Frame) {
        if (pendingReset.getAndSet(false)) clearAll()
        if (pendingDone.getAndSet(false) && endpoints.isNotEmpty()) finished = true
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

        val segs = ArrayList<SegmentUi>()
        val placed = ArrayList<LabelContent>()
        for (i in 0 until pts.size - 1) {
            val a = pts[i].position
            val b = pts[i + 1].position
            val label = MeasureLabel.build(status, observe(i, camPos), observe(i + 1, camPos), a.distanceTo(b).toDouble())
            placed += label
            segs += SegmentUi(ScreenProjection.projectSegment(a, b, viewM, projM, w, h), label, false)
        }
        if (pts.isNotEmpty() && !finished) {
            val start = pts.last().position
            val end = live.position
            if (end != null) {
                segs += SegmentUi(
                    ScreenProjection.projectSegment(start, end, viewM, projM, w, h),
                    MeasureLabel.build(
                        status, observe(pts.size - 1, camPos), EndpointObservation.from(live),
                        start.distanceTo(end).toDouble(),
                    ),
                    true,
                )
            } else {
                segs += SegmentUi(
                    null,
                    MeasureLabel.build(status, observe(pts.size - 1, camPos), EndpointObservation.NONE, 0.0),
                    true,
                )
            }
        }
        _state.value = MeasureUiState(
            coaching, dots, segs, PathTotal.of(placed), pts.size, finished,
            canAdd = pts.size < PathTotal.MAX_POINTS,
        )
    }

    private fun place(hit: ArHit) {
        val sess = manager.session ?: return
        val pos = hit.result.position ?: return
        if (finished) clearAll()
        if (endpoints.size >= PathTotal.MAX_POINTS) return
        val anchor = hit.createAnchor(sess) ?: return
        val id = session.addPoint(pos, hit.result.confidence)
        endpoints += Endpoint(id, anchor, hit.result)
        if (endpoints.size >= 2) {
            val prev = endpoints[endpoints.size - 2]
            val pp = session.state.point(prev.id)?.position
            val cm = (pp?.distanceTo(pos) ?: 0f) * 100.0
            android.util.Log.i(
                "OpenMeasureSpike",
                "dist=%.1f cm source=%s/%s conf=%.2f/%.2f".format(
                    cm, prev.hit.source, hit.result.source, prev.hit.confidence, hit.result.confidence,
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
