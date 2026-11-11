package app.openmeasure

import app.openmeasure.ar.ArCameraView
import app.openmeasure.ar.ArHit
import app.openmeasure.ar.ArSessionManager
import app.openmeasure.ar.HitTestService
import app.openmeasure.ar.RectDetectService
import app.openmeasure.ar.SnapService
import app.openmeasure.ar.toPose
import app.openmeasure.measure.coaching.CoachingState
import app.openmeasure.measure.coaching.CoachingTracker
import app.openmeasure.measure.confidence.EndpointObservation
import app.openmeasure.measure.confidence.TrackingStatus
import app.openmeasure.measure.geometry.Pose
import app.openmeasure.measure.geometry.Vec3
import app.openmeasure.measure.hit.HitResult
import app.openmeasure.measure.hit.HitSource
import app.openmeasure.measure.label.PathTotal
import app.openmeasure.measure.model.PointId
import app.openmeasure.measure.projection.ScreenPoint
import app.openmeasure.measure.session.MeasurementSession
import app.openmeasure.measure.snap.SnapHysteresis
import app.openmeasure.measure.snap.SnapKind
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
 * Point-to-point and rectangle controller (F1/F3/F4/F9). Runs on the GL thread via [ArCameraView.onFrame]; each placed point is
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

    /** Settings "Snap to edges"; read on the GL thread every frame. */
    @Volatile var snapEnabled: Boolean = true
    /** Settings "Guide lines" (F12): references + lock to level/plumb while placing the next point in line mode. */
    @Volatile var guideEnabled: Boolean = true
    /** Units for F13 ruler ticks; null = ticks off. */
    @Volatile var rulerUnits: app.openmeasure.measure.units.UnitSystem? = null
    /** Max snap distance in view pixels (set from dp by the UI). Snapped points never move farther. */
    @Volatile var snapRadiusPx: Float = 60f

    private val service = HitTestService()
    private val snapService = SnapService(service.depthSource)
    private val rectService = RectDetectService(service.depthSource)
    private var rectMode = false
    private val pendingDetect = AtomicBoolean(false)
    private var detectFailTick = 0
    private var reticleSnap = newHysteresis()
    private var dragSnap = newHysteresis()
    private var hystRadius = snapRadiusPx
    private var dragSnapKind = SnapKind.NONE
    private var dragSnapPoint: ScreenPoint? = null
    private var snapTick = 0

    private val guide = GuideController()
    private var lockedEnd: Vec3? = null
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
    /** F9: switch between line and rectangle mode (clears the current measurement, undoable). */
    fun setRectMode(on: Boolean) { commands += { if (rectMode != on) { rectMode = on; clearAll() } } }
    /** F9: auto-detect a rectangle at the reticle on the next frame; failure bumps [MeasureUiState.detectFailTick]. */
    fun detect() { pendingDetect.set(true) }

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

    /** Release snaps at the max radius and acquire only well inside it, so a held target never exceeds it. */
    private fun newHysteresis(): SnapHysteresis {
        val r = snapRadiusPx.coerceAtLeast(1f)
        return SnapHysteresis(acquireRadiusPx = r * 0.6f, releaseRadiusPx = r)
    }

    /** Applies snapping (if enabled) to [base] hit at view (x, y); counts acquire ticks for haptics. */
    private fun snapped(frame: Frame, x: Float, y: Float, base: ArHit, hyst: SnapHysteresis): SnapService.Outcome {
        if (!snapEnabled) {
            hyst.reset()
            return SnapService.Outcome(base, SnapKind.NONE, false, null)
        }
        val out = snapService.snap(frame, x, y, base, hyst, view.width, view.height, snapRadiusPx)
        if (out.acquired) snapTick++
        return out
    }

    private fun applyDrag(frame: Frame, status: TrackingStatus) {
        val d = dragTarget
        if (d == null) { dragSnap.reset(); dragSnapKind = SnapKind.NONE; dragSnapPoint = null; return }
        if (draggingId != d.id) { draggingId = d.id; dragHit = null; dragSnap.reset() }
        if (status != TrackingStatus.TRACKING) return
        val snap = snapped(frame, d.x, d.y, service.hitTest(frame, d.x, d.y), dragSnap)
        dragSnapKind = snap.kind
        dragSnapPoint = snap.screen
        val hit = snap.hit
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
        if (hystRadius != snapRadiusPx) {
            hystRadius = snapRadiusPx
            reticleSnap = newHysteresis()
            dragSnap = newHysteresis()
        }
        val liveSnap = snapped(frame, w / 2f, h / 2f, service.hitTestCenter(frame, view.width, view.height), reticleSnap)
        val liveHit: ArHit = liveSnap.hit
        var live = liveHit.result
        frame.camera.getViewMatrix(viewM, 0)
        frame.camera.getProjectionMatrix(projM, 0, NEAR, FAR)
        val camPos = frame.camera.pose.let { Vec3(it.tx(), it.ty(), it.tz()) }
        val fv = FrameView(viewM, projM, w, h, camPos, status)
        // F12: when aligned, the live end becomes its level/plumb projection so lengths are the true horizontal/vertical distance.
        val start = if (rectMode || finished) null else session.state.points.lastOrNull()?.position
        val g = guide.update(start, live.position, guideEnabled && status == TrackingStatus.TRACKING, fv)
        lockedEnd = if (g != null && g.locked) g.end else null
        lockedEnd?.let { live = live.copy(position = it) }
        val coaching = coachingTracker.update(System.nanoTime() / 1_000_000, status, live)

        if (pendingPlace.getAndSet(false) && coaching == CoachingState.READY) place(liveHit, lockedEnd)
        if (pendingDetect.getAndSet(false) && coaching == CoachingState.READY) detectRect(frame, w / 2f, h / 2f)
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

        val pts = session.state.points
        val dots = pts.map { fv.project(it.position) }
        val built = MeasureFrame.build(pts, finished, rectMode, live, rulerUnits, { observe(it, camPos) }, fv)
        _state.value = MeasureUiState(
            coaching, dots, pts.map { it.id }, built.segments, PathTotal.of(built.placed), pts.size, finished,
            canAdd = rectMode || pts.size < PathTotal.MAX_POINTS,
            canUndo = session.state.canUndo,
            draggingId = draggingId,
            snapKind = if (draggingId != null) dragSnapKind else liveSnap.kind,
            snapPoint = if (draggingId != null) dragSnapPoint else liveSnap.screen,
            snapTick = snapTick,
            guide = g?.ui,
            guideTick = guide.acquireTick,
            rectMode = rectMode,
            rect = built.rect,
            detectFailTick = detectFailTick,
            save = if (status == TrackingStatus.TRACKING) SaveCandidate.of(rectMode, finished, built.placed, built.rect, pts) else null,
        )
    }

    private fun place(hit: ArHit, aligned: Vec3? = null) {
        val sess = manager.session ?: return
        val pos = aligned ?: hit.result.position ?: return
        if (finished || (rectMode && session.state.points.size >= MeasureFrame.RECT_CORNERS)) clearAll()
        if (session.state.points.size >= PathTotal.MAX_POINTS) return
        val anchor = (if (aligned != null) sess.createAnchor(ArPose.makeTranslation(pos.x, pos.y, pos.z)) else hit.createAnchor(sess))
            ?: return
        val id = session.addPoint(pos, hit.result.confidence)
        anchors[id] = anchor
        hits[id] = hit.result
    }

    /** Replaces the measurement with detected corners (one undo step after the clear), or counts a failure. */
    private fun detectRect(frame: Frame, x: Float, y: Float) {
        val sess = manager.session
        val rect = if (rectMode && sess != null) rectService.detect(frame, x, y) else null
        if (sess == null || rect == null) { detectFailTick++; return }
        clearAll()
        val ids = session.addPoints(rect.corners, rect.confidence)
        val cam = frame.camera.pose.toPose().translation
        ids.zip(rect.corners).forEach { (id, p) ->
            anchors[id] = sess.createAnchor(ArPose.makeTranslation(p.x, p.y, p.z))
            hits[id] = HitResult(HitSource.DEPTH, p, Pose(p), rect.plane.normal, rect.confidence, cam.distanceTo(p))
        }
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
