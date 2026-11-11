package app.openmeasure.measure.snap

import app.openmeasure.measure.geometry.Pose
import app.openmeasure.measure.hit.CameraIntrinsics
import app.openmeasure.measure.hit.DepthHitTester
import app.openmeasure.measure.hit.DepthMap
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Finds a depth edge or corner near a pixel of an ARCore depth map (F8).
 *
 * Works on inverse depth w = 1/z, which is affine in (u, v) on any plane, so its second difference is ~0 on
 * flat *and* slanted surfaces and spikes only at discontinuities. An "edgel" is a pixel on the *near* side of
 * a depth jump (the object's rim, where users want the point), found by:
 *  - scale-2 second difference of w (robust to ARCore's 1-2 px edge smoothing) <= -[edgeRelThreshold] * w,
 *    (thresholds scale up past [thresholdKneeM]),
 *  - a 4-neighbour that is farther by >= max([minJumpMm], [adjacentJumpRel] * z) (keeps the outermost pixel),
 *  - mean raw sensor confidence around it >= [minSensorConfidence] (when a confidence image exists),
 *  - at least [minEdgeNeighbours] other edgels in its 8-neighbourhood (edges are lines, noise is isolated).
 * Corners are edgels whose structure tensor of grad(w)/w (radius [tensorRadius]) has two strong directions
 * (eigen-ratio >= [cornerRatio]). Corners win over edges. Anything ambiguous returns [SnapResult.NONE].
 *
 * Accuracy: the snapped pixel is the outermost near-side depth pixel, so the point sits up to ~1 depth pixel
 * inside the true rim (~7 mm at 1 m on a 160 px-wide depth map). Its depth is the median of same-surface
 * neighbours only, never mixed with the background.
 */
class EdgeSnapper(
    val minDepthMm: Int = 100,
    val maxDepthMm: Int = 8000,
    val edgeRelThreshold: Float = 0.08f,
    val adjacentJumpRel: Float = 0.04f,
    /** Beyond this depth both relative thresholds grow linearly (ARCore relative noise grows with distance). */
    val thresholdKneeM: Float = 1.5f,
    val minJumpMm: Int = 15,
    val minSensorConfidence: Float = 0.2f,
    val unknownSensorConfidence: Float = 0.7f,
    val minEdgeNeighbours: Int = 2,
    val tensorRadius: Int = 2,
    val cornerRatio: Float = 0.15f,
    val minCornerEnergy: Float = 0.002f,
    val minSnapConfidence: Float = 0.25f,
    val fullConfidenceRangeM: Float = 2.5f,
    val zeroConfidenceRangeM: Float = 6.0f,
) {

    /**
     * @param u,v pixel-index coords in [depth]; [radiusPx] max snap distance in depth pixels (circular).
     * @param k intrinsics scaled to the depth map; [cameraPose] as for [DepthHitTester.hitTest].
     */
    fun snap(depth: DepthMap, k: CameraIntrinsics, u: Float, v: Float, radiusPx: Int, cameraPose: Pose): SnapResult {
        if (radiusPx < 1) return SnapResult.NONE
        val cx = u.roundToInt()
        val cy = v.roundToInt()
        if (cx !in 0 until depth.width || cy !in 0 until depth.height) return SnapResult.NONE

        val r1 = radiusPx + 1
        val n = 2 * r1 + 1
        val strength = FloatArray(n * n)
        for (dy in -r1..r1) for (dx in -r1..r1) {
            val x = cx + dx
            val y = cy + dy
            if (isEdgel(depth, x, y)) strength[(dy + r1) * n + dx + r1] = edgeStrength(depth, x, y)
        }

        var best: Candidate? = null
        for (dy in -radiusPx..radiusPx) for (dx in -radiusPx..radiusPx) {
            val s = strength[(dy + r1) * n + dx + r1]
            if (s <= 0f) continue
            val x = cx + dx
            val y = cy + dy
            val dist = hypot(x - u, y - v)
            if (dist > radiusPx) continue
            if (neighbours(strength, n, dx + r1, dy + r1) < minEdgeNeighbours) continue
            val (ratio, lMin) = structure(depth, x, y)
            val corner = ratio >= cornerRatio && lMin >= minCornerEnergy
            val c = Candidate(x, y, dist, s, corner, ratio * lMin)
            if (best == null || c.betterThan(best)) best = c
        }
        val b = best ?: return SnapResult.NONE
        return finish(depth, k, cameraPose, b)
    }

    private fun finish(depth: DepthMap, k: CameraIntrinsics, pose: Pose, c: Candidate): SnapResult {
        val dMm = depth.depthAt(c.x, c.y)
        val tol = max(minJumpMm.toFloat(), adjacentJumpRel * dMm)
        val same = ArrayList<Int>(9)
        for (y in c.y - 1..c.y + 1) for (x in c.x - 1..c.x + 1) {
            val d = depthOrZero(depth, x, y)
            if (d > 0 && abs(d - dMm) <= tol) same += d
        }
        same.sort()
        val depthM = DepthHitTester.median(same.toIntArray()) / 1000f
        val sensor = sensorMean(depth, c.x, c.y)?.let { sqrt(it) } ?: unknownSensorConfidence
        val dist = clamp01(1f - (depthM - fullConfidenceRangeM) / (zeroConfidenceRangeM - fullConfidenceRangeM))
        val conf = clamp01(sensor * dist * clamp01(c.strength / (2f * edgeRelThreshold * scale(dMm))))
        if (conf < minSnapConfidence) return SnapResult.NONE
        val world = pose.transformPoint(DepthHitTester.unprojectToCamera(k, c.x.toFloat(), c.y.toFloat(), depthM))
        val kind = if (c.corner) SnapKind.CORNER else SnapKind.EDGE
        return SnapResult(true, world, kind, c.x, c.y, c.dist, conf, depthM)
    }

    private class Candidate(
        val x: Int, val y: Int, val dist: Float, val strength: Float, val corner: Boolean, val cornerEnergy: Float,
    ) {
        /** Corners beat edges; among corners the most balanced strong response (the vertex); among edges the nearest. */
        fun betterThan(o: Candidate): Boolean = when {
            corner != o.corner -> corner
            corner -> if (cornerEnergy != o.cornerEnergy) cornerEnergy > o.cornerEnergy else dist < o.dist
            dist != o.dist -> dist < o.dist
            else -> strength > o.strength
        }
    }

    /** Near-side rim pixel with confident depth? */
    internal fun isEdgel(depth: DepthMap, x: Int, y: Int): Boolean {
        val d = depthOrZero(depth, x, y)
        if (d == 0) return false
        val sc = scale(d)
        if (edgeStrength(depth, x, y) < edgeRelThreshold * sc) return false
        val jump = max(minJumpMm.toFloat(), adjacentJumpRel * sc * d)
        val farNeighbour = NEIGHBOURS_4.any { (ox, oy) ->
            val q = depthOrZero(depth, x + ox, y + oy)
            q > 0 && q - d >= jump
        }
        if (!farNeighbour) return false
        val conf = sensorMean(depth, x, y)
        return conf == null || conf >= minSensorConfidence
    }

    /** -min(second difference of w along x, along y) / w, >= 0; > 0 only on the near side of a jump/ridge. */
    internal fun edgeStrength(depth: DepthMap, x: Int, y: Int): Float {
        val w0 = inv(depth, x, y)
        if (w0.isNaN()) return 0f
        var s = 0f
        val l = inv(depth, x - 2, y)
        val r = inv(depth, x + 2, y)
        if (!l.isNaN() && !r.isNaN()) s = min(s, l + r - 2f * w0)
        val t = inv(depth, x, y - 2)
        val b = inv(depth, x, y + 2)
        if (!t.isNaN() && !b.isNaN()) s = min(s, t + b - 2f * w0)
        return -s / w0
    }

    /** (lambdaMin / lambdaMax, lambdaMin) of the structure tensor of grad(w)/w(centre) around (x, y). */
    internal fun structure(depth: DepthMap, x: Int, y: Int): Pair<Float, Float> {
        val w0 = inv(depth, x, y)
        if (w0.isNaN()) return Pair(0f, 0f)
        val minG = edgeRelThreshold / 4f
        var a = 0f
        var b = 0f
        var c = 0f
        for (yy in y - tensorRadius..y + tensorRadius) for (xx in x - tensorRadius..x + tensorRadius) {
            val l = inv(depth, xx - 1, yy)
            val r = inv(depth, xx + 1, yy)
            val t = inv(depth, xx, yy - 1)
            val bt = inv(depth, xx, yy + 1)
            if (l.isNaN() || r.isNaN() || t.isNaN() || bt.isNaN()) continue
            val gx = (r - l) / (2f * w0)
            val gy = (bt - t) / (2f * w0)
            if (gx * gx + gy * gy < minG * minG) continue
            a += gx * gx; b += gx * gy; c += gy * gy
        }
        val half = (a + c) / 2f
        val root = sqrt(((a - c) / 2f) * ((a - c) / 2f) + b * b)
        val lMax = half + root
        val lMin = half - root
        if (lMax <= 1e-12f) return Pair(0f, 0f)
        return Pair(lMin / lMax, lMin)
    }

    private fun scale(dMm: Int): Float = max(1f, dMm / (1000f * thresholdKneeM))

    private fun neighbours(strength: FloatArray, n: Int, gx: Int, gy: Int): Int {
        var count = 0
        for (oy in -1..1) for (ox in -1..1) {
            if (ox == 0 && oy == 0) continue
            val x = gx + ox
            val y = gy + oy
            if (x in 0 until n && y in 0 until n && strength[y * n + x] > 0f) count++
        }
        return count
    }

    /** Mean raw confidence (0..1) over the 3x3 patch, or null without a confidence image. */
    private fun sensorMean(depth: DepthMap, x: Int, y: Int): Float? {
        if (depth.confidence == null) return null
        var sum = 0
        var cnt = 0
        for (yy in y - 1..y + 1) for (xx in x - 1..x + 1) {
            if (xx !in 0 until depth.width || yy !in 0 until depth.height) continue
            sum += depth.confidenceAt(xx, yy) ?: 0
            cnt++
        }
        return if (cnt == 0) 0f else sum / (255f * cnt)
    }

    private fun depthOrZero(depth: DepthMap, x: Int, y: Int): Int {
        if (x !in 0 until depth.width || y !in 0 until depth.height) return 0
        val d = depth.depthAt(x, y)
        return if (d in minDepthMm..maxDepthMm) d else 0
    }

    /** Inverse depth in 1/m, NaN when invalid or out of bounds. */
    private fun inv(depth: DepthMap, x: Int, y: Int): Float {
        val d = depthOrZero(depth, x, y)
        return if (d == 0) Float.NaN else 1000f / d
    }

    private companion object {
        val NEIGHBOURS_4 = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        fun clamp01(f: Float) = f.coerceIn(0f, 1f)
    }
}
