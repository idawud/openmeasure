package app.openmeasure.measure.angle

import app.openmeasure.measure.geometry.Vec3
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** F16 angle maths. Angles in degrees. */
object AngleMath {
    /** Arms shorter than this (metres) are too short to give a stable angle. */
    const val MIN_ARM_M = 0.02f

    /** Angle at [v] between arms v->[a] and v->[b], 0..180. Null if a point is non-finite or an arm has ~zero length. */
    fun angleDegrees(a: Vec3, v: Vec3, b: Vec3): Double? {
        val va = a - v
        val vb = b - v
        if (!finite(va) || !finite(vb)) return null
        if (va.length() < 1e-6f || vb.length() < 1e-6f) return null
        val cross = (va cross vb).length().toDouble()
        val dot = (va dot vb).toDouble()
        return Math.toDegrees(atan2(cross, dot))
    }

    fun supplementary(degrees: Double): Double = 180.0 - degrees

    /** Angle between the line [from]->[to] and the horizontal plane (Y up), 0..90. Null for a zero-length line. */
    fun angleToHorizontalDegrees(from: Vec3, to: Vec3): Double? {
        val d = to - from
        if (!finite(d) || d.length() < 1e-6f) return null
        return Math.toDegrees(atan2(abs(d.y).toDouble(), hypot(d.x.toDouble(), d.z.toDouble())))
    }

    /** Angle between the line [from]->[to] and the vertical, 0..90. */
    fun angleToVerticalDegrees(from: Vec3, to: Vec3): Double? =
        angleToHorizontalDegrees(from, to)?.let { 90.0 - it }

    private fun finite(v: Vec3) = v.x.isFinite() && v.y.isFinite() && v.z.isFinite()
}

/** Screen-space arc at the vertex: start angle (degrees, y down, atan2) and signed sweep (|sweep| <= 180). */
data class AngleArcSpec(val startDeg: Float, val sweepDeg: Float, val bisectorX: Float, val bisectorY: Float)

object AngleArc {
    /** Smaller sweep from the vertex->a screen direction to vertex->b, or null if either screen arm is degenerate. */
    fun compute(vx: Float, vy: Float, ax: Float, ay: Float, bx: Float, by: Float): AngleArcSpec? {
        if ((ax == vx && ay == vy) || (bx == vx && by == vy)) return null
        val a = Math.toDegrees(atan2((ay - vy).toDouble(), (ax - vx).toDouble()))
        val b = Math.toDegrees(atan2((by - vy).toDouble(), (bx - vx).toDouble()))
        var sweep = (b - a) % 360.0
        if (sweep > 180.0) sweep -= 360.0
        if (sweep < -180.0) sweep += 360.0
        val mid = Math.toRadians(a + sweep / 2.0)
        return AngleArcSpec(a.toFloat(), sweep.toFloat(), cos(mid).toFloat(), sin(mid).toFloat())
    }
}
