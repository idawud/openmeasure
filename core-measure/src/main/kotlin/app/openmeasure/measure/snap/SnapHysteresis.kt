package app.openmeasure.measure.snap

import app.openmeasure.measure.geometry.Vec3
import app.openmeasure.measure.projection.ScreenPoint
import kotlin.math.hypot

/**
 * Keeps the snap target stable across frames so the reticle doesn't flicker (F8).
 *
 * - Acquire: a fresh target is taken only if it projects within [acquireRadiusPx] of the reticle.
 * - Hold: once held, the target stays while the reticle is within [releaseRadiusPx] (> acquire) of it, even
 *   on frames where detection drops out.
 * - Follow: a held EDGE moves with fresh EDGE detections that land within [followRadiusPx] of it (sliding along
 *   an edge), but never jumps to a different, farther edge. A held CORNER only follows fresh corners.
 * - Upgrade: a fresh CORNER within the acquire radius replaces a held EDGE.
 *
 * Not thread-safe; use one instance per input (reticle, drag) on the GL thread.
 */
class SnapHysteresis(
    val acquireRadiusPx: Float,
    val releaseRadiusPx: Float = acquireRadiusPx * 1.6f,
    val followRadiusPx: Float = acquireRadiusPx,
) {
    init {
        require(acquireRadiusPx > 0f && releaseRadiusPx >= acquireRadiusPx) { "need 0 < acquire <= release" }
    }

    /** [acquired] is true on frames where a new target (or a new kind) was taken: the haptic tick moment. */
    data class Update(val target: SnapTarget?, val acquired: Boolean)

    var current: SnapTarget? = null
        private set

    fun reset() { current = null }

    /**
     * @param reticle where the user is aiming (view pixels).
     * @param fresh this frame's detection, already limited to the max snap radius; null if none.
     * @param project world -> view pixels for this frame; null if behind the camera.
     */
    fun update(reticle: ScreenPoint, fresh: SnapTarget?, project: (Vec3) -> ScreenPoint?): Update {
        val held = current
        val heldScreen = held?.let { project(it.position) }
        val freshScreen = fresh?.let { project(it.position) }
        val freshNear = fresh != null && freshScreen != null && dist(freshScreen, reticle) <= acquireRadiusPx

        val stillHeld = held != null && heldScreen != null && dist(heldScreen, reticle) <= releaseRadiusPx
        val next: SnapTarget? = if (stillHeld) {
            val follows = fresh != null && freshScreen != null && dist(freshScreen, heldScreen!!) <= followRadiusPx
            when {
                freshNear && fresh!!.kind == SnapKind.CORNER && held!!.kind != SnapKind.CORNER -> fresh
                follows && fresh!!.kind == held!!.kind -> fresh
                else -> held
            }
        } else {
            if (freshNear) fresh else null
        }
        val acquired = next != null && (!stillHeld || next.kind != held?.kind)
        current = next
        return Update(next, acquired)
    }

    private fun dist(a: ScreenPoint, b: ScreenPoint) = hypot(a.x - b.x, a.y - b.y)
}
