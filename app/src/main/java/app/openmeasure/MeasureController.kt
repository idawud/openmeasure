package app.openmeasure

import app.openmeasure.ar.ArCameraView
import app.openmeasure.ar.ArHit
import app.openmeasure.ar.ArSessionManager
import app.openmeasure.ar.HitTestService
import app.openmeasure.measure.coaching.CoachingState
import app.openmeasure.measure.coaching.CoachingTracker
import app.openmeasure.measure.confidence.TrackingStatus
import app.openmeasure.measure.geometry.Vec3
import app.openmeasure.measure.label.PathTotal
import app.openmeasure.measure.model.PointId
import app.openmeasure.measure.session.MeasurementSession
import com.google.ar.core.Frame
import com.google.ar.core.TrackingState
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Point-to-point and rectangle controller (F1/F3/F4/F9). Runs on the GL thread via [ArCameraView.onFrame]; each placed point is
 * backed by an ARCore anchor ([AnchorStore]) whose pose is fed into [MeasurementSession.updatePoints] every frame.
 * UI-thread calls (drag, undo, remove) are queued and applied at the start of the next frame.
 * Mode-specific logic lives in separate classes ([RectModeController], [DragController]); add new modes the same way.
 */
class MeasureController(private val view: ArCameraView, private val manager: ArSessionManager) {
    private val service = HitTestService()
    private val session = MeasurementSession()
    private val anchors = AnchorStore(manager, session)
    private val snapper = Snapper(service, view)
    private val drag = DragController(manager, session, anchors, service, snapper)
    private val rect = RectModeController(manager, session, anchors, service, ::clearAll)
    private val angle = AngleModeController()
    private val commands = ConcurrentLinkedQueue<() -> Unit>()

    /** Settings "Snap to edges"; read on the GL thread every frame. */
    var snapEnabled: Boolean
        get() = snapper.enabled
        set(v) { snapper.enabled = v }
    /** Max snap distance in view pixels (set from dp by the UI). Snapped points never move farther. */
    var snapRadiusPx: Float
        get() = snapper.radiusPx
        set(v) { snapper.radiusPx = v }
    /** Settings "Guide lines" (F12): references + lock to level/plumb while placing the next point in line mode. */
    @Volatile var guideEnabled: Boolean = true
    /** Units for F13 ruler ticks; null = ticks off. */
    @Volatile var rulerUnits: app.openmeasure.measure.units.UnitSystem? = null

    private val guide = GuideController()
    private var lockedEnd: Vec3? = null
    private val coachingTracker = CoachingTracker()
    private var finished = false
    private val pendingDone = AtomicBoolean(false)
    private val pendingPlace = AtomicBoolean(false)
    private val pendingReset = AtomicBoolean(false)
    private val pendingDetect = AtomicBoolean(false)
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
    fun setRectMode(on: Boolean) = setMode(on, false)
    /** F16: switch angle mode on/off (clears the measurement; rectangle and angle are mutually exclusive). */
    fun setAngleMode(on: Boolean) = setMode(false, on)
    private fun setMode(rectOn: Boolean, angleOn: Boolean) {
        commands += { if (rect.active != rectOn || angle.active != angleOn) { rect.active = rectOn; angle.active = angleOn; clearAll() } }
    }
    /** F9: auto-detect a rectangle at the reticle on the next frame; failure bumps [MeasureUiState.detectFailTick]. */
    fun detect() { pendingDetect.set(true) }

    /** Finger down on [id]: start dragging; [x],[y] are the (already lifted) view-pixel target. */
    fun beginDrag(id: PointId, x: Float, y: Float) = drag.begin(id, x, y)
    fun dragTo(x: Float, y: Float) = drag.moveTo(x, y)

    /** Finger lifted: commit the drag (re-anchor at the last valid hit). */
    fun endDrag() {
        drag.release()
        commands += { drag.finish() }
    }

    /** Long-press: drop the drag and remove the point. */
    fun remove(id: PointId) {
        drag.release()
        commands += { drag.finish(commit = false); session.removePoint(id); anchors.sync() }
    }

    fun undo() {
        commands += { drag.finish(commit = false); session.undo(); finished = false; anchors.sync() }
    }

    private fun clearAll() {
        drag.cancel()
        session.clear()
        anchors.sync()
        finished = false
    }

    private fun onFrame(frame: Frame) {
        while (true) (commands.poll() ?: break).invoke()
        if (pendingReset.getAndSet(false)) clearAll()
        if (pendingDone.getAndSet(false) && !session.state.isEmpty) finished = true
        val status = when (frame.camera.trackingState) {
            TrackingState.TRACKING -> TrackingStatus.TRACKING
            TrackingState.PAUSED -> TrackingStatus.PAUSED
            else -> TrackingStatus.STOPPED
        }
        val w = view.width.toFloat()
        val h = view.height.toFloat()
        snapper.syncRadius()
        val liveSnap = snapper.snap(frame, w / 2f, h / 2f, service.hitTestCenter(frame, view.width, view.height), snapper.reticle)
        val liveHit: ArHit = liveSnap.hit
        var live = liveHit.result
        frame.camera.getViewMatrix(viewM, 0)
        frame.camera.getProjectionMatrix(projM, 0, NEAR, FAR)
        val camPos = frame.camera.pose.let { Vec3(it.tx(), it.ty(), it.tz()) }
        val fv = FrameView(viewM, projM, w, h, camPos, status)
        // F12: when aligned, the live end becomes its level/plumb projection so lengths are the true horizontal/vertical distance.
        val start = if (rect.active || angle.active || finished) null else session.state.points.lastOrNull()?.position
        val g = guide.update(start, live.position, guideEnabled && status == TrackingStatus.TRACKING, fv)
        lockedEnd = if (g != null && g.locked) g.end else null
        lockedEnd?.let { live = live.copy(position = it) }
        val coaching = coachingTracker.update(System.nanoTime() / 1_000_000, status, live)

        if (pendingPlace.getAndSet(false) && coaching == CoachingState.READY) place(liveHit, lockedEnd)
        if (pendingDetect.getAndSet(false) && coaching == CoachingState.READY) rect.detect(frame, w / 2f, h / 2f)
        drag.apply(frame, status)

        // Anchors keep points glued to the world as tracking refines (a dragged point follows the finger instead).
        val updates = anchors.poses(drag.draggingId)
        if (updates.isNotEmpty()) session.updatePoints(updates)

        val pts = session.state.points
        val dots = pts.map { fv.project(it.position) }
        val observe = { i: Int -> anchors.observe(i, camPos) }
        val angleDone = angle.active && pts.size >= AngleModeController.POINTS
        val built = MeasureFrame.build(pts, finished || angleDone, rect.active, live, rulerUnits, observe, fv)
        val dragging = drag.draggingId != null
        _state.value = MeasureUiState(
            coaching, dots, pts.map { it.id }, built.segments, if (angle.active) null else PathTotal.of(built.placed), pts.size, finished,
            canAdd = rect.active || angle.active || pts.size < PathTotal.MAX_POINTS,
            canUndo = session.state.canUndo,
            draggingId = drag.draggingId,
            snapKind = if (dragging) drag.snapKind else liveSnap.kind,
            snapPoint = if (dragging) drag.snapPoint else liveSnap.screen,
            snapTick = snapper.tick,
            guide = g?.ui,
            guideTick = guide.acquireTick,
            rectMode = rect.active,
            angleMode = angle.active,
            angle = angle.build(pts, observe, fv),
            rect = built.rect,
            detectFailTick = rect.failTick,
            save = if (status == TrackingStatus.TRACKING && !angle.active) SaveCandidate.of(rect.active, finished, built.placed, built.rect, pts) else null,
        )
    }

    private fun place(hit: ArHit, aligned: Vec3? = null) {
        val sess = manager.session ?: return
        val pos = aligned ?: hit.result.position ?: return
        if (finished || (rect.active && session.state.points.size >= MeasureFrame.RECT_CORNERS) ||
            (angle.active && session.state.points.size >= AngleModeController.POINTS)) clearAll()
        if (session.state.points.size >= PathTotal.MAX_POINTS) return
        val anchor = (if (aligned != null) anchors.createAt(pos) else hit.createAnchor(sess)) ?: return
        val id = session.addPoint(pos, hit.result.confidence)
        anchors.put(id, anchor, hit.result)
    }

    private companion object {
        const val NEAR = 0.05f
        const val FAR = 100f
    }
}
