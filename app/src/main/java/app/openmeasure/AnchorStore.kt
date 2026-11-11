package app.openmeasure

import app.openmeasure.ar.ArSessionManager
import app.openmeasure.measure.confidence.EndpointObservation
import app.openmeasure.measure.geometry.Vec3
import app.openmeasure.measure.hit.HitResult
import app.openmeasure.measure.model.PointId
import app.openmeasure.measure.session.MeasurementSession
import com.google.ar.core.Anchor
import com.google.ar.core.TrackingState
import com.google.ar.core.Pose as ArPose

/** GL-thread bookkeeping: one ARCore [Anchor] and the original [HitResult] per measurement point. */
internal class AnchorStore(private val manager: ArSessionManager, private val session: MeasurementSession) {
    private val anchors = HashMap<PointId, Anchor>()
    /** Original hit per point (source/confidence for badges); kept after removal so undo can restore it. */
    private val hits = HashMap<PointId, HitResult>()

    fun createAt(pos: Vec3): Anchor? =
        manager.session?.createAnchor(ArPose.makeTranslation(pos.x, pos.y, pos.z))

    fun put(id: PointId, anchor: Anchor, hit: HitResult) {
        anchors.put(id, anchor)?.detach()
        hits[id] = hit
    }

    fun setHit(id: PointId, hit: HitResult) { hits[id] = hit }

    /** Current world positions of tracking anchors, skipping [skip] (a dragged point follows the finger instead). */
    fun poses(skip: PointId?): Map<PointId, Vec3> {
        val updates = HashMap<PointId, Vec3>()
        for ((id, anchor) in anchors) {
            if (id != skip && anchor.trackingState == TrackingState.TRACKING) {
                val t = anchor.pose
                updates[id] = Vec3(t.tx(), t.ty(), t.tz())
            }
        }
        return updates
    }

    /** Makes anchors match the session: detach orphans, create/replace where a point has none or moved (undo). */
    fun sync() {
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

    /** Endpoint as seen now: original hit source/confidence, camera distance from the current camera pose. */
    fun observe(index: Int, camPos: Vec3): EndpointObservation {
        val p = session.state.points.getOrNull(index) ?: return EndpointObservation.NONE
        val h = hits[p.id] ?: return EndpointObservation.NONE
        return EndpointObservation(h.source, h.confidence, camPos.distanceTo(p.position))
    }
}
