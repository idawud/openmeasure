package app.openmeasure

import android.util.Log
import app.openmeasure.ar.ArSessionManager
import app.openmeasure.measure.confidence.EndpointObservation
import app.openmeasure.measure.geometry.Vec3
import app.openmeasure.measure.hit.HitResult
import app.openmeasure.measure.model.PointId
import app.openmeasure.measure.session.AnchorPoseFilter
import app.openmeasure.measure.session.MeasurementSession
import com.google.ar.core.Anchor
import com.google.ar.core.TrackingState
import com.google.ar.core.Pose as ArPose

/**
 * GL-thread bookkeeping: one world-fixed ARCore [Anchor] and the original [HitResult] per measurement point.
 * Anchor poses pass through [AnchorPoseFilter] so placed points stay frozen except for real tracking corrections.
 */
internal class AnchorStore(
    private val manager: ArSessionManager,
    private val session: MeasurementSession,
    /** Debug builds only: log placed-point positions (tag [DRIFT_TAG]) for ~10 s after placement. */
    private val debugLog: Boolean = false,
) {
    private val anchors = HashMap<PointId, Anchor>()
    /** Original hit per point (source/confidence for badges); kept after removal so undo can restore it. */
    private val hits = HashMap<PointId, HitResult>()
    private val filter = AnchorPoseFilter<PointId>()
    private val logFrames = HashMap<PointId, Int>()

    fun createAt(pos: Vec3): Anchor? =
        manager.session?.createAnchor(ArPose.makeTranslation(pos.x, pos.y, pos.z))

    fun put(id: PointId, anchor: Anchor, hit: HitResult) {
        anchors.put(id, anchor)?.detach()
        hits[id] = hit
        val p = session.state.point(id)?.position ?: anchor.pose.let { Vec3(it.tx(), it.ty(), it.tz()) }
        filter.seed(id, p)
        if (debugLog) {
            logFrames[id] = 0
            Log.d(DRIFT_TAG, "placed $id at ${fmt(p)} source=${hit.source} conf=${hit.confidence}")
        }
    }

    fun setHit(id: PointId, hit: HitResult) { hits[id] = hit }

    /**
     * Filtered world positions of anchors that changed this frame, skipping [skip] (a dragged point follows
     * the finger instead). Points whose anchor is not tracking, jittered sub-mm or jumped once are left as is.
     */
    fun poses(skip: PointId?, cameraTracking: Boolean): Map<PointId, Vec3> {
        val updates = HashMap<PointId, Vec3>()
        for ((id, anchor) in anchors) {
            if (id == skip) continue
            val state = anchor.trackingState
            val raw = if (state == TrackingState.TRACKING) anchor.pose.let { Vec3(it.tx(), it.ty(), it.tz()) } else null
            filter.update(id, raw, cameraTracking && state == TrackingState.TRACKING)?.let { updates[id] = it }
            if (debugLog) logDrift(id, state, raw)
        }
        return updates
    }

    /** Makes anchors match the session: detach orphans, create/replace where a point has none or moved (undo). */
    fun sync() {
        val sess = manager.session
        val pts = session.state.points
        val live = pts.map { it.id }.toSet()
        anchors.keys.filter { it !in live }.forEach { anchors.remove(it)?.detach(); filter.remove(it); logFrames.remove(it) }
        if (sess == null) return
        for (p in pts) {
            val a = anchors[p.id]
            // Compare with what the filter last pushed into the session, not the raw (noisy) anchor pose.
            val ok = a != null && a.trackingState != TrackingState.STOPPED &&
                (filter.accepted(p.id)?.distanceTo(p.position) ?: Float.MAX_VALUE) < 1e-4f
            if (ok) continue
            a?.detach()
            anchors[p.id] = sess.createAnchor(ArPose.makeTranslation(p.position.x, p.position.y, p.position.z))
            filter.seed(p.id, p.position)
        }
    }

    /** Endpoint as seen now: original hit source/confidence, camera distance from the current camera pose. */
    fun observe(index: Int, camPos: Vec3): EndpointObservation {
        val p = session.state.points.getOrNull(index) ?: return EndpointObservation.NONE
        val h = hits[p.id] ?: return EndpointObservation.NONE
        return EndpointObservation(h.source, h.confidence, camPos.distanceTo(p.position))
    }

    private fun logDrift(id: PointId, state: TrackingState, raw: Vec3?) {
        val n = logFrames[id] ?: return
        if (n > LOG_FRAMES) { logFrames.remove(id); return }
        logFrames[id] = n + 1
        if (n % LOG_EVERY != 0) return
        val shown = session.state.point(id)?.position
        Log.d(DRIFT_TAG, "$id f=$n anchor=$state raw=${raw?.let(::fmt)} shown=${shown?.let(::fmt)} src=${hits[id]?.source}")
    }

    private fun fmt(v: Vec3) = "(%.4f, %.4f, %.4f)".format(v.x, v.y, v.z)

    companion object {
        const val DRIFT_TAG = "OpenMeasureDrift"
        private const val LOG_FRAMES = 300 // ~10 s at 30 fps
        private const val LOG_EVERY = 30
    }
}
