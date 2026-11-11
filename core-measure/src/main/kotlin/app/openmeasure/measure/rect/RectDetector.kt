package app.openmeasure.measure.rect

import app.openmeasure.measure.geometry.Plane
import app.openmeasure.measure.geometry.Pose
import app.openmeasure.measure.geometry.Vec3
import app.openmeasure.measure.hit.CameraIntrinsics
import app.openmeasure.measure.hit.DepthMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Auto-detected rectangle. [corners] are world-space, counter-clockwise as seen from the camera. */
data class DetectedRect(
    val corners: List<Vec3>,
    val widthM: Float,
    val heightM: Float,
    /** Region area (holes filled) / rectangle area; >= [RectDetector.minFillRatio]. */
    val fillRatio: Float,
    /** World-space plane of the surface, normal towards the camera. */
    val plane: Plane,
    /** 0..1: sensor confidence x distance, as for depth hits. */
    val confidence: Float,
    val meanDepthM: Float,
)

/**
 * Finds a rectangle on a planar surface around a seed pixel in an ARCore depth map (F9 auto-detect).
 *
 * 1. Plane from the (2r+1)^2 patch around the seed (must be flat: rms <= tolerance / 2).
 * 2. Region grow (4-connected) over pixels within max([planeAbsTolMm], [planeRelTol] * z) of the plane; the
 *    plane is refitted on the region and the region regrown [refits] times.
 * 3. Reject if the region touches the image border (partially visible: size unknown) or is tiny.
 * 4. Every pixel's footprint (rays through its corners intersected with the plane) is mapped into a 2D plane
 *    basis. Enclosed holes (dropouts, objects lying on top) are filled. Width, height and centre come from the
 *    area second moments of the footprint union (w = sqrt(12 var)): unbiased and averaged along whole edges,
 *    unlike hull extremes, which sit ~0.5-0.7 px outside a slanted edge.
 * 5. Fill ratio = filled area / minimum-area hull rectangle (bias-corrected); below [minFillRatio] the shape is
 *    not a rectangle (a disc gives ~pi/4) -> null.
 *
 * Only surfaces that stand out in depth (box/book/table top, door in a recess) can be found; a poster flush on a
 * wall is invisible to depth, so callers fall back to manual corners on null. Expected error ~0.5 depth pixel per
 * edge (~4 mm at 1 m on a 160 px-wide map) plus ARCore edge smoothing.
 */
class RectDetector(
    val minDepthMm: Int = 100,
    val maxDepthMm: Int = 8000,
    val seedRadius: Int = 3,
    val minSeedValidFraction: Float = 0.6f,
    val planeAbsTolMm: Float = 12f,
    val planeRelTol: Float = 0.012f,
    val refits: Int = 2,
    val minRegionPixels: Int = 40,
    val minFillRatio: Float = 0.85f,
    /** |cos| between plane normal and viewing ray; below this the surface is too oblique to trust. */
    val minIncidenceCos: Float = 0.25f,
    val minSideM: Float = 0.03f,
    val unknownSensorConfidence: Float = 0.7f,
    val fullConfidenceRangeM: Float = 2.5f,
    val zeroConfidenceRangeM: Float = 6.0f,
) {

    fun detect(depth: DepthMap, k: CameraIntrinsics, u: Float, v: Float, cameraPose: Pose): DetectedRect? {
        val sx = u.roundToInt()
        val sy = v.roundToInt()
        val w = depth.width
        val h = depth.height
        if (sx !in 0 until w || sy !in 0 until h) return null
        val pts = CameraPoints(depth, k, minDepthMm, maxDepthMm)

        var plane = seedPlane(pts, sx, sy) ?: return null
        var region = grow(pts, plane, sx, sy) ?: return null
        repeat(refits) {
            plane = fitRegion(pts, region) ?: return null
            region = grow(pts, plane, sx, sy) ?: return null
        }
        var count = 0
        for (y in 0 until h) for (x in 0 until w) {
            if (!region[y * w + x]) continue
            count++
            if (x == 0 || y == 0 || x == w - 1 || y == h - 1) return null
        }
        if (count < minRegionPixels) return null

        // Normal towards the camera (camera at the origin in camera space).
        val n = if ((plane.normal dot plane.point) > 0f) -plane.normal else plane.normal
        val camPlane = Plane(plane.point, n)
        if ((n dot -plane.point.normalized()) < minIncidenceCos) return null
        val basis = PlaneBasis.of(camPlane.point, n, Vec3(1f, 0f, 0f))
        val footprint = Footprint(k, camPlane, basis)

        val shape = shape(region, footprint, w, h) ?: return null
        if (shape.height < minSideM || shape.fill < minFillRatio) return null

        val corners = shape.corners.map { cameraPose.transformPoint(basis.to3d(it)) }
        val worldNormal = cameraPose.rotateVector(n)
        val worldPlane = Plane(cameraPose.transformPoint(camPlane.point), worldNormal)
        val meanDepth = -plane.point.z
        return DetectedRect(
            corners, shape.width.toFloat(), shape.height.toFloat(), shape.fill.toFloat().coerceAtMost(1f), worldPlane,
            confidence(depth, region, meanDepth), meanDepth,
        )
    }

    private class Shape(val corners: List<Vec2>, val width: Double, val height: Double, val fill: Double)

    /**
     * Orientation: principal axis of the area moments when clearly elongated, else the minimum-area hull rectangle
     * (moments cannot orient a square). Size and centre: area moments (unbiased). Fill: filled area over the hull
     * rectangle shrunk by half a mean pixel footprint per dimension (hull extremes sit ~0.5-0.7 px outside the edge).
     */
    private fun shape(region: BooleanArray, footprint: Footprint, w: Int, h: Int): Shape? {
        val outline = ArrayList<Vec2>()
        for (y in 0 until h) for (x in 0 until w) {
            if (!region[y * w + x] || !isBoundary(region, w, x, y)) continue
            outline += footprint.corners(x, y) ?: return null
        }
        val hull = MinAreaRect.of(outline) ?: return null
        val moments = RegionMoments()
        val filled = fillHoles(region, w, h)
        for (i in filled.indices) if (filled[i]) moments.add(footprint.corners(i % w, i / w) ?: return null)
        if (moments.area <= 0.0) return null

        val (angle, anisotropy) = moments.principalAxis()
        val d = if (anisotropy > ANISOTROPY_FOR_MOMENT_AXIS) RegionMoments.unit(angle)
        else (hull.corners[1] - hull.corners[0]).let { it * (1.0 / it.length()) }
        val p = Vec2(-d.y, d.x)
        val a = moments.extentAlong(d)
        val b = moments.extentAlong(p)
        val c = moments.centroid
        val corners = listOf(
            c - d * (a / 2) - p * (b / 2), c + d * (a / 2) - p * (b / 2),
            c + d * (a / 2) + p * (b / 2), c - d * (a / 2) + p * (b / 2),
        )
        val shrink = 0.5 * sqrt(moments.area / moments.count)
        val fill = moments.area / ((hull.width - shrink).coerceAtLeast(1e-9) * (hull.height - shrink).coerceAtLeast(1e-9))
        return Shape(corners, maxOf(a, b), minOf(a, b), fill)
    }

    private fun tolM(zM: Float): Float = max(planeAbsTolMm / 1000f, planeRelTol * zM)

    private fun seedPlane(pts: CameraPoints, sx: Int, sy: Int): Plane? {
        val acc = PlaneAccumulator()
        var total = 0
        for (y in sy - seedRadius..sy + seedRadius) for (x in sx - seedRadius..sx + seedRadius) {
            total++
            pts.at(x, y)?.let { acc.add(it) }
        }
        if (acc.count < minSeedValidFraction * total) return null
        val plane = acc.fit() ?: return null
        var sq = 0.0
        for (y in sy - seedRadius..sy + seedRadius) for (x in sx - seedRadius..sx + seedRadius) {
            val p = pts.at(x, y) ?: continue
            val d = plane.signedDistance(p).toDouble()
            sq += d * d
        }
        val rms = sqrt(sq / acc.count)
        return if (rms <= tolM(-plane.point.z) / 2f) plane else null
    }

    private fun inlier(pts: CameraPoints, plane: Plane, x: Int, y: Int): Boolean {
        val p = pts.at(x, y) ?: return false
        return abs(plane.signedDistance(p)) <= tolM(-p.z)
    }

    /** 4-connected flood from the seed over plane inliers, or null if the seed itself is not an inlier. */
    private fun grow(pts: CameraPoints, plane: Plane, sx: Int, sy: Int): BooleanArray? {
        val w = pts.w
        if (!inlier(pts, plane, sx, sy)) return null
        val region = BooleanArray(w * pts.h)
        val stack = IntArrayStack()
        region[sy * w + sx] = true
        stack.push(sy * w + sx)
        while (stack.isNotEmpty()) {
            val i = stack.pop()
            val x = i % w
            val y = i / w
            for ((ox, oy) in N4) {
                val nx = x + ox
                val ny = y + oy
                if (nx !in 0 until w || ny !in 0 until pts.h) continue
                val j = ny * w + nx
                if (region[j] || !inlier(pts, plane, nx, ny)) continue
                region[j] = true
                stack.push(j)
            }
        }
        return region
    }

    private fun fitRegion(pts: CameraPoints, region: BooleanArray): Plane? {
        val acc = PlaneAccumulator()
        for (i in region.indices) if (region[i]) pts.at(i % pts.w, i / pts.w)?.let { acc.add(it) }
        return acc.fit()
    }

    private fun confidence(depth: DepthMap, region: BooleanArray, depthM: Float): Float {
        var sum = 0L
        var n = 0
        if (depth.confidence != null) {
            for (i in region.indices) if (region[i]) { sum += depth.confidence[i].toInt() and 0xFF; n++ }
        }
        val sensor = if (n > 0) sqrt(sum / (255f * n)) else unknownSensorConfidence
        val dist = 1f - (depthM - fullConfidenceRangeM) / (zeroConfidenceRangeM - fullConfidenceRangeM)
        return (sensor * dist.coerceIn(0f, 1f)).coerceIn(0f, 1f)
    }

    internal companion object {
        val N4 = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        /** Above this moment anisotropy the principal axis orients the rectangle (exact for rectangles). */
        const val ANISOTROPY_FOR_MOMENT_AXIS = 0.15

        fun isBoundary(region: BooleanArray, w: Int, x: Int, y: Int): Boolean = N4.any { (ox, oy) ->
            val nx = x + ox
            val ny = y + oy
            nx < 0 || ny < 0 || nx >= w || ny * w + nx >= region.size || !region[ny * w + nx]
        }

        /** Region plus every non-region pixel not 4-connected to the image border (enclosed holes). */
        fun fillHoles(region: BooleanArray, w: Int, h: Int): BooleanArray {
            val outside = BooleanArray(region.size)
            val stack = IntArrayStack()
            fun seed(x: Int, y: Int) {
                val i = y * w + x
                if (!region[i] && !outside[i]) { outside[i] = true; stack.push(i) }
            }
            for (x in 0 until w) { seed(x, 0); seed(x, h - 1) }
            for (y in 0 until h) { seed(0, y); seed(w - 1, y) }
            while (stack.isNotEmpty()) {
                val i = stack.pop()
                val x = i % w
                val y = i / w
                for ((ox, oy) in N4) {
                    val nx = x + ox
                    val ny = y + oy
                    if (nx in 0 until w && ny in 0 until h) seed(nx, ny)
                }
            }
            return BooleanArray(region.size) { !outside[it] }
        }
    }
}
