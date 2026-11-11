package app.openmeasure.measure.rect

import app.openmeasure.measure.geometry.Vec3
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

/**
 * Turns four world-space corners (manual taps or [RectDetector]) into a [RectResult] (F9).
 *
 * 1. Least-squares plane through the 4 points; NOT_PLANAR if any point is farther than
 *    clamp([planarRelTol] x longer diagonal, [planarFloorM], [planarAbsTolM]) from it. The floor keeps small
 *    objects from failing on depth noise (~5 mm at 1 m); numbers are still computed on the projection.
 * 2. Corners projected onto the plane and expressed in a 2D basis oriented so the polygon is counter-clockwise.
 * 3. Must be a convex quad (bow-tie / concave -> DEGENERATE) with every side >= [minSideM] and every interior
 *    angle in [[minAngleDeg], 180 - [minAngleDeg]] (near-collinear corners -> DEGENERATE).
 * 4. Rectangle iff every interior angle is within [rightAngleTolDeg] of 90; else NOT_RECTANGULAR.
 *
 * With [reorder] the corners are first sorted by angle around their centroid, which turns any tap order of a
 * convex shape into a simple polygon (a Z-shaped tap order no longer gives a bow-tie).
 */
class QuadAnalyzer(
    val planarAbsTolM: Double = 0.02,
    val planarRelTol: Double = 0.03,
    val planarFloorM: Double = 0.005,
    val rightAngleTolDeg: Double = 5.0,
    val minSideM: Double = 0.01,
    val minAngleDeg: Double = 10.0,
) {

    fun analyze(points: List<Vec3>, reorder: Boolean = false): RectResult {
        require(points.size == 4) { "need exactly 4 corners, got ${points.size}" }
        val acc = PlaneAccumulator()
        points.forEach { acc.add(it) }
        val plane = acc.fit() ?: return RectResult.degenerate(points)
        var basis = PlaneBasis.of(plane.point, plane.normal, points[1] - points[0])
        val deviation = points.maxOf { abs(basis.distance(it).toDouble()) }

        var flat = points.map { basis.to2d(it) }
        val order = if (reorder) angularOrder(flat) else listOf(0, 1, 2, 3)
        flat = order.map { flat[it] }
        if (signedArea(flat) < 0) {
            basis = basis.flipped()
            flat = flat.map { Vec2(it.x, -it.y) }
        }

        val diagonals = listOf((flat[2] - flat[0]).length(), (flat[3] - flat[1]).length())
        val tol = (planarRelTol * max(diagonals[0], diagonals[1])).coerceIn(planarFloorM, planarAbsTolM)
        val notPlanar = deviation > tol
        if (!isConvex(flat)) return RectResult.degenerate(points, basis.normal, deviation, notPlanar)

        val sides = (0..3).map { (flat[(it + 1) % 4] - flat[it]).length() }
        val angles = (0..3).map { interiorAngle(flat[(it + 3) % 4], flat[it], flat[(it + 1) % 4]) }
        val area = signedArea(flat)
        val tiny = sides.any { it < minSideM } || area < minSideM * minSideM ||
            angles.any { it < minAngleDeg || it > 180.0 - minAngleDeg }
        if (tiny) return RectResult.degenerate(points, basis.normal, deviation, notPlanar)

        val isRect = angles.all { abs(it - 90.0) <= rightAngleTolDeg }
        val pairA = (sides[0] + sides[2]) / 2
        val pairB = (sides[1] + sides[3]) / 2
        val width = if (isRect) max(pairA, pairB) else Double.NaN
        val height = if (isRect) min(pairA, pairB) else Double.NaN
        val warnings = buildSet {
            if (notPlanar) add(RectWarning.NOT_PLANAR)
            if (!isRect) add(RectWarning.NOT_RECTANGULAR)
        }
        return RectResult(
            corners = flat.map { basis.to3d(it) },
            order = order,
            normal = basis.normal,
            sidesM = sides,
            diagonalsM = diagonals,
            anglesDeg = angles,
            areaM2 = area,
            isRectangle = isRect,
            widthM = width,
            heightM = height,
            rectAreaM2 = if (isRect) width * height else Double.NaN,
            maxPlaneDeviationM = deviation,
            warnings = warnings,
        )
    }

    companion object {
        /** Shoelace: positive for counter-clockwise polygons. */
        fun signedArea(poly: List<Vec2>): Double {
            var s = 0.0
            for (i in poly.indices) s += poly[i] cross poly[(i + 1) % poly.size]
            return s / 2
        }

        /** Strictly convex counter-clockwise polygon (every turn is a left turn). */
        internal fun isConvex(poly: List<Vec2>): Boolean {
            val n = poly.size
            for (i in 0 until n) {
                val a = poly[(i + 1) % n] - poly[i]
                val b = poly[(i + 2) % n] - poly[(i + 1) % n]
                if ((a cross b) <= 0.0) return false
            }
            return true
        }

        /** Interior angle at [b] in degrees, 0..180. */
        internal fun interiorAngle(a: Vec2, b: Vec2, c: Vec2): Double {
            val p = a - b
            val q = c - b
            return Math.toDegrees(atan2(abs(p cross q), p dot q))
        }

        /** Indices sorted by polar angle around the centroid, starting from index 0. */
        internal fun angularOrder(pts: List<Vec2>): List<Int> {
            val cx = pts.sumOf { it.x } / pts.size
            val cy = pts.sumOf { it.y } / pts.size
            val ang = pts.map { atan2(it.y - cy, it.x - cx) }
            val sorted = pts.indices.sortedBy { ang[it] }
            val start = sorted.indexOf(0)
            return List(sorted.size) { sorted[(start + it) % sorted.size] }
        }
    }
}
