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
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import com.google.ar.core.Pose as ArPose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Point-to-point controller (F1/F3/F4). Runs on the GL thread via [ArCameraView.onFrame]; each placed point is
 * backed by an ARCore [Anchor] whose pose is fed into [MeasurementSession.updatePoints] every frame.
 * UI-thread calls (drag, undo, remove) are queued and applied at the start of the next frame.
 */
class MeasureController(private val view: ArCameraView, private val manager: ArSessionManager) {
    private class Drag(val id: PointId, @Volatile var x: Float, @Volatile var y: Float)

    private val anchors = HashMap<PointId, Anchor>()
    /** Original hit per point (source/confidence for badges); kept after removal so undo can restore it. */
    private val hits = HashMap<PointId, HitResult>()
    private val commands = ConcurrentLinkedQueue<() -> Unit>()
    @Volatile private var dragTarget: Drag? = null
    private var draggingId: PointId? = null
    private var dragHit: ArHit? = null

    private val service = HitTestService()
    private val coachingTracker = CoachingTracker()
    private val session = MeasurementSession()
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

    /** Finger down on [id]: start dragging; [x],[y] are the (already lifted) view-pixel target. */
    fun beginDrag(id: PointId, x: Float, y: Float) {
        dragTarget = Drag(id, x, y)
    }

    fun dragTo(x: Float, y: Float) {
        dragTarget?.let { it.x = x; it.y = y }
    }

    /** Finger lifted: commit the drag (re-anchor at the last valid hit). */
    fun endDrag() {
        dragTarget = null
        commands += { finishDrag() }
    }

    /** Long-press: drop the drag and remove the point. */
    fun remove(id: PointId) {
        dragTarget = null
        commands += { finishDrag(commit = false); session.removePoint(id); syncAnchors() }
    }

    fun undo() {
        commands += { finishDrag(commit = false); session.undo(); finished = false; syncAnchors() }
    }

    private fun clearAll() {
        draggingId = null
        session.clear()
        syncAnchors()
        finished = false
    }

    /** Makes anchors match the session: detach orphans, create/replace where a point has none or moved (undo). */
    private fun syncAnchors() {
        val sess = manager.session
        val pts = session.state.points
        val live = pts.map { it.id }.toSet()
        anchors.keys.filter { it !in live }.forEach { anchors.remove(it)?.detach() }
        if (sess == null) return
        for (p in pts) {
            val a = anchors[p.id]
            val ap = a?.pose
            val ok = a != null && a.trackingState != TrackingState.STOPPED &&
                Vec3(ap!!.tx(), ap.ty(), ap.tz()).distanceTo(p.position) < 1e-3f
            if (ok) continue
            a?.detach()
            anchors[p.id] = sess.createAnchor(ArPose.makeTranslation(p.position.x, p.position.y, p.position.z))
        }
    }

    private fun finishDrag(commit: Boolean = true) {
        val id = draggingId
        val hit = dragHit
        draggingId = null
        dragHit = null
        session.endMove()
        val sess = manager.session ?: return
        if (!commit || id == null || hit == null || session.state.point(id) == null) return
        val fresh = hit.createAnchor(sess) ?: return
        anchors.put(id, fresh)?.detach()
        hits[id] = hit.result
    }

    private fun applyDrag(frame: Frame, status: TrackingStatus) {
        val d = dragTarget ?: return
        if (draggingId != d.id) { draggingId = d.id; dragHit = null }
        if (status != TrackingStatus.TRACKING) return
        val hit = service.hitTest(frame, d.x, d.y)
        val pos = hit.result.position ?: return
        if (session.movePoint(d.id, pos, hit.result.confidence)) {
            dragHit = hit
            hits[d.id] = hit.result
        }
    }

    private fun onFrame(frame: Frame) {
        while (true) (commands.poll() ?: break).invoke()
        if (pendingReset.getAndSet(false)) clearAll()
        if (pendingDone.getAndSet(false) && !session.state.isEmpty) finished = true
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
        applyDrag(frame, status)

        // Anchors keep points glued to the world as tracking refines (a dragged point follows the finger instead).
        val updates = HashMap<PointId, Vec3>()
        for ((id, anchor) in anchors) {
            if (id != draggingId && anchor.trackingState == TrackingState.TRACKING) {
                val t = anchor.pose
                updates[id] = Vec3(t.tx(), t.ty(), t.tz())
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
            coaching, dots, pts.map { it.id }, segs, PathTotal.of(placed), pts.size, finished,
            canAdd = pts.size < PathTotal.MAX_POINTS,
            canUndo = session.state.canUndo,
            draggingId = draggingId,
        )
    }

    private fun place(hit: ArHit) {
        val sess = manager.session ?: return
        val pos = hit.result.position ?: return
        if (finished) clearAll()
        if (session.state.points.size >= PathTotal.MAX_POINTS) return
        val anchor = hit.createAnchor(sess) ?: return
        val id = session.addPoint(pos, hit.result.confidence)
        anchors[id] = anchor
        hits[id] = hit.result
    }

    /** Endpoint as seen now: original hit source/confidence, camera distance from the current camera pose. */
    private fun observe(index: Int, camPos: Vec3): EndpointObservation {
        val p = session.state.points.getOrNull(index) ?: return EndpointObservation.NONE
        val h = hits[p.id] ?: return EndpointObservation.NONE
        return EndpointObservation(h.source, h.confidence, camPos.distanceTo(p.position))
    }

    private companion object {
        const val NEAR = 0.05f
        const val FAR = 100f
    }
}
