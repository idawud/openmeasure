package app.openmeasure

import app.openmeasure.ar.ArHit
import app.openmeasure.ar.ArSessionManager
import app.openmeasure.ar.HitTestService
import app.openmeasure.measure.confidence.TrackingStatus
import app.openmeasure.measure.model.PointId
import app.openmeasure.measure.projection.ScreenPoint
import app.openmeasure.measure.session.MeasurementSession
import app.openmeasure.measure.snap.SnapKind
import com.google.ar.core.Frame

/** Point dragging: UI-thread target setters, GL-thread [apply]/[finish]. */
internal class DragController(
    private val manager: ArSessionManager,
    private val session: MeasurementSession,
    private val anchors: AnchorStore,
    private val service: HitTestService,
    private val snapper: Snapper,
) {
    private class Drag(val id: PointId, @Volatile var x: Float, @Volatile var y: Float)

    @Volatile private var target: Drag? = null
    var draggingId: PointId? = null
        private set
    private var dragHit: ArHit? = null
    var snapKind = SnapKind.NONE
        private set
    var snapPoint: ScreenPoint? = null
        private set

    fun begin(id: PointId, x: Float, y: Float) { target = Drag(id, x, y) }
    fun moveTo(x: Float, y: Float) { target?.let { it.x = x; it.y = y } }
    fun release() { target = null }

    /** Forget the dragged point without committing (measurement cleared). */
    fun cancel() { draggingId = null }

    /** GL thread: commit (re-anchor at the last valid hit) or abandon the drag. */
    fun finish(commit: Boolean = true) {
        val id = draggingId
        val hit = dragHit
        draggingId = null
        dragHit = null
        session.endMove()
        val sess = manager.session ?: return
        if (!commit || id == null || hit == null || session.state.point(id) == null) return
        val fresh = hit.createAnchor(sess) ?: return
        anchors.put(id, fresh, hit.result)
    }

    fun apply(frame: Frame, status: TrackingStatus) {
        val d = target
        if (d == null) { snapper.drag.reset(); snapKind = SnapKind.NONE; snapPoint = null; return }
        if (draggingId != d.id) { draggingId = d.id; dragHit = null; snapper.drag.reset() }
        if (status != TrackingStatus.TRACKING) return
        val snap = snapper.snap(frame, d.x, d.y, service.hitTest(frame, d.x, d.y), snapper.drag)
        snapKind = snap.kind
        snapPoint = snap.screen
        val hit = snap.hit
        val pos = hit.result.position ?: return
        if (session.movePoint(d.id, pos, hit.result.confidence)) {
            dragHit = hit
            anchors.setHit(d.id, hit.result)
        }
    }
}
