package app.openmeasure.measure.guide

import app.openmeasure.measure.geometry.Vec3
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin

/** F12: which world axis the live segment is aligned to. ARCore world Y is up (gravity-aligned). */
enum class GuideState { NONE, HORIZONTAL, VERTICAL }

object GuideMath {
    /** Segments shorter than this have no meaningful direction. */
    const val MIN_LENGTH_M = 0.02f
    private const val RAD_TO_DEG = 180.0 / Math.PI

    /** Angle in degrees between the segment and the horizontal plane (0 = level, 90 = plumb); null if too short. */
    fun deviationFromHorizontal(start: Vec3, end: Vec3): Float? {
        val d = end - start
        val len = d.length()
        if (!(len >= MIN_LENGTH_M)) return null
        return (asin((abs(d.y) / len).coerceIn(0f, 1f)) * RAD_TO_DEG).toFloat()
    }

    /** Angle in degrees between the segment and the vertical axis (0 = plumb, 90 = level); null if too short. */
    fun deviationFromVertical(start: Vec3, end: Vec3): Float? {
        val d = end - start
        val len = d.length()
        if (!(len >= MIN_LENGTH_M)) return null
        return (acos((abs(d.y) / len).coerceIn(0f, 1f)) * RAD_TO_DEG).toFloat()
    }

    /** End projected onto the horizontal plane through [start] (HORIZONTAL) or the vertical line through it (VERTICAL). */
    fun alignedEnd(start: Vec3, end: Vec3, guide: GuideState): Vec3 = when (guide) {
        GuideState.NONE -> end
        GuideState.HORIZONTAL -> Vec3(end.x, start.y, end.z)
        GuideState.VERTICAL -> Vec3(start.x, end.y, start.z)
    }
}

/** Stateful guide detection with hysteresis: acquire within [acquireDeg], hold until beyond [releaseDeg]. */
class GuideTracker(private val acquireDeg: Float = 2f, private val releaseDeg: Float = 4f) {
    var state: GuideState = GuideState.NONE
        private set

    /** Last measured deviation (degrees) from the aligned axis, or from the nearer axis when not aligned; null if unknown. */
    var deviationDeg: Float? = null
        private set

    /** Result of the latest [update]; true when state just changed from NONE to an aligned state. */
    var acquired: Boolean = false
        private set

    fun reset() { state = GuideState.NONE; deviationDeg = null; acquired = false }

    fun update(start: Vec3?, end: Vec3?): GuideState {
        val prev = state
        val h = if (start != null && end != null) GuideMath.deviationFromHorizontal(start, end) else null
        val v = if (start != null && end != null) GuideMath.deviationFromVertical(start, end) else null
        if (h == null || v == null) {
            state = GuideState.NONE
            deviationDeg = null
        } else {
            deviationDeg = minOf(h, v)
            state = when (prev) {
                GuideState.HORIZONTAL -> if (h <= releaseDeg) prev else acquire(h, v)
                GuideState.VERTICAL -> if (v <= releaseDeg) prev else acquire(h, v)
                GuideState.NONE -> acquire(h, v)
            }
        }
        acquired = prev == GuideState.NONE && state != GuideState.NONE
        return state
    }

    private fun acquire(h: Float, v: Float) = when {
        h <= acquireDeg && h <= v -> GuideState.HORIZONTAL
        v <= acquireDeg -> GuideState.VERTICAL
        else -> GuideState.NONE
    }
}
