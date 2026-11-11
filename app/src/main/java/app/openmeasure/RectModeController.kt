package app.openmeasure

import app.openmeasure.ar.ArSessionManager
import app.openmeasure.ar.HitTestService
import app.openmeasure.ar.RectDetectService
import app.openmeasure.ar.toPose
import app.openmeasure.measure.geometry.Pose
import app.openmeasure.measure.hit.HitResult
import app.openmeasure.measure.hit.HitSource
import app.openmeasure.measure.session.MeasurementSession
import com.google.ar.core.Frame

/** F9 rectangle mode: the mode flag plus auto-detect of 4 corners at the reticle. */
internal class RectModeController(
    private val manager: ArSessionManager,
    private val session: MeasurementSession,
    private val anchors: AnchorStore,
    service: HitTestService,
    private val clearAll: () -> Unit,
) {
    private val rectService = RectDetectService(service.depthSource)
    var active = false
    var failTick = 0
        private set

    /** Replaces the measurement with detected corners (one undo step after the clear), or counts a failure. */
    fun detect(frame: Frame, x: Float, y: Float) {
        val sess = manager.session
        val rect = if (active && sess != null) rectService.detect(frame, x, y) else null
        if (sess == null || rect == null) { failTick++; return }
        clearAll()
        val ids = session.addPoints(rect.corners, rect.confidence)
        val cam = frame.camera.pose.toPose().translation
        ids.zip(rect.corners).forEach { (id, p) ->
            val a = anchors.createAt(p) ?: return@forEach
            anchors.put(id, a, HitResult(HitSource.DEPTH, p, Pose(p), rect.plane.normal, rect.confidence, cam.distanceTo(p)))
        }
    }
}
