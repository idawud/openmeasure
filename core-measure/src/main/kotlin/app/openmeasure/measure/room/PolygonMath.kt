package app.openmeasure.measure.room

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** 2D point/vector in metres (plan space: x right, y up, i.e. a top view of the floor). */
data class Vec2(val x: Double, val y: Double) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun times(s: Double) = Vec2(x * s, y * s)
    infix fun dot(o: Vec2) = x * o.x + y * o.y
    infix fun cross(o: Vec2) = x * o.y - y * o.x
    fun length() = hypot(x, y)
    fun distanceTo(o: Vec2) = (this - o).length()

    /** Rotated counter-clockwise by [rad]. */
    fun rotated(rad: Double): Vec2 {
        val c = cos(rad)
        val s = sin(rad)
        return Vec2(x * c - y * s, x * s + y * c)
    }
}

/** Simple-polygon helpers. Polygons are closed implicitly (last vertex connects to the first). */
object PolygonMath {
    /** Shoelace signed area: > 0 for counter-clockwise (y up). */
    fun signedArea(p: List<Vec2>): Double {
        var s = 0.0
        for (i in p.indices) s += p[i] cross p[(i + 1) % p.size]
        return s / 2.0
    }

    fun perimeter(p: List<Vec2>): Double = p.indices.sumOf { p[it].distanceTo(p[(it + 1) % p.size]) }

    /**
     * True when two non-adjacent edges touch or cross (bow-tie, figure eight), or two adjacent edges fold back
     * onto each other (a 0° spike). Tolerance [eps] metres. O(n²); rooms have few corners.
     */
    fun isSelfIntersecting(p: List<Vec2>, eps: Double = 1e-6): Boolean {
        val n = p.size
        if (n < 3) return false
        for (i in 0 until n) {
            val a = p[i]
            val b = p[(i + 1) % n]
            for (j in i + 1 until n) {
                val c = p[j]
                val d = p[(j + 1) % n]
                val adjacent = j == i + 1 || (i == 0 && j == n - 1)
                if (adjacent) {
                    // Shared vertex: only a fold-back (collinear, pointing back) counts.
                    val shared = if (j == i + 1) b else a
                    val u = (if (j == i + 1) a else b) - shared
                    val v = (if (j == i + 1) d else c) - shared
                    if (abs(u cross v) <= eps * u.length() * v.length() && (u dot v) > 0) return true
                    continue
                }
                if (segmentsTouch(a, b, c, d, eps)) return true
            }
        }
        return false
    }

    /** Closed-segment intersection test with collinear overlap handling. */
    fun segmentsTouch(a: Vec2, b: Vec2, c: Vec2, d: Vec2, eps: Double = 1e-9): Boolean {
        val d1 = orient(c, d, a)
        val d2 = orient(c, d, b)
        val d3 = orient(a, b, c)
        val d4 = orient(a, b, d)
        if (((d1 > eps && d2 < -eps) || (d1 < -eps && d2 > eps)) && ((d3 > eps && d4 < -eps) || (d3 < -eps && d4 > eps))) return true
        return (abs(d1) <= eps && onSegment(c, d, a, eps)) ||
            (abs(d2) <= eps && onSegment(c, d, b, eps)) ||
            (abs(d3) <= eps && onSegment(a, b, c, eps)) ||
            (abs(d4) <= eps && onSegment(a, b, d, eps))
    }

    /**
     * Interior angle in degrees (0..360) at each vertex of a counter-clockwise polygon: 90 for a box corner,
     * 270 for the inside corner of an L-shaped room. Sums to (n − 2) × 180 for a simple polygon.
     */
    fun interiorAnglesDeg(ccw: List<Vec2>): List<Double> = ccw.indices.map { i ->
        val c = ccw[i]
        val toPrev = ccw[(i - 1 + ccw.size) % ccw.size] - c
        val toNext = ccw[(i + 1) % ccw.size] - c
        var deg = Math.toDegrees(atan2(toNext cross toPrev, toNext dot toPrev))
        if (deg < 0) deg += 360.0
        deg
    }

    private fun orient(a: Vec2, b: Vec2, c: Vec2): Double = (b - a) cross (c - a)

    private fun onSegment(a: Vec2, b: Vec2, p: Vec2, eps: Double): Boolean =
        p.x >= minOf(a.x, b.x) - eps && p.x <= maxOf(a.x, b.x) + eps &&
            p.y >= minOf(a.y, b.y) - eps && p.y <= maxOf(a.y, b.y) + eps
}
