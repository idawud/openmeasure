package app.openmeasure.measure.session

import app.openmeasure.measure.geometry.Vec3

/**
 * Freezes placed points against anchor pose noise while still following real tracking corrections.
 *
 * Per point it keeps the last ACCEPTED position (seeded at placement) and, per frame, given the
 * anchor's resolved position:
 * - anchor/camera not tracking -> hold (paused anchors report stale or extrapolated poses);
 * - moved less than [deadbandM] -> hold (sub-millimetre anchor jitter never reaches the screen);
 * - moved up to [jumpM] -> accept (ordinary map refinement);
 * - moved more than [jumpM] in one go -> hold, and accept only once the new pose has been
 *   consistent (within [confirmTolM]) for [confirmFrames] consecutive tracking frames
 *   (a genuine relocalisation, not a one-frame glitch).
 */
class AnchorPoseFilter<K>(
    val deadbandM: Float = 0.002f,
    val jumpM: Float = 0.05f,
    val confirmFrames: Int = 10,
    val confirmTolM: Float = 0.01f,
) {
    private class Entry(var accepted: Vec3, var pending: Vec3? = null, var pendingCount: Int = 0)

    private val entries = HashMap<K, Entry>()

    /** (Re)starts [id] at [position] (placement, re-anchoring, undo). */
    fun seed(id: K, position: Vec3) { entries[id] = Entry(position) }

    fun remove(id: K) { entries.remove(id) }

    fun accepted(id: K): Vec3? = entries[id]?.accepted

    /** Returns the new accepted position if it changed this frame, else null (keep the point where it is). */
    fun update(id: K, anchorPosition: Vec3?, tracking: Boolean): Vec3? {
        val e = entries[id] ?: return null
        if (!tracking || anchorPosition == null) {
            e.pending = null; e.pendingCount = 0
            return null
        }
        val d = anchorPosition.distanceTo(e.accepted)
        if (d < deadbandM) {
            e.pending = null; e.pendingCount = 0
            return null
        }
        if (d <= jumpM) return accept(e, anchorPosition)
        val p = e.pending
        if (p != null && p.distanceTo(anchorPosition) <= confirmTolM) e.pendingCount++ else e.pendingCount = 1
        e.pending = anchorPosition
        return if (e.pendingCount >= confirmFrames) accept(e, anchorPosition) else null
    }

    private fun accept(e: Entry, pos: Vec3): Vec3 {
        e.accepted = pos
        e.pending = null
        e.pendingCount = 0
        return pos
    }
}
