package app.openmeasure.measure.hit

import app.openmeasure.measure.geometry.Plane
import app.openmeasure.measure.geometry.Vec3
import app.openmeasure.measure.rect.PlaneAccumulator
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max

/**
 * Fuses the depth patch around the reticle into one local surface plane and returns where the current
 * camera ray meets it. A plane uses ~100 depth samples at once, so per-sample noise averages out in a
 * way a single-frame median cannot, and the result does not depend on the camera being still.
 *
 * Per frame: fit a plane to the patch points (one trimmed refit), orient it towards the camera, reject
 * grazing rays, then blend it into a plane held in world space (exponential, alpha) while the new plane
 * agrees with the held one; otherwise replace it. The output is the current ray intersected with the
 * held plane and must stay near the raw median hit, so a bad fit cannot move the reticle far.
 *
 * Returns null when there is no reliable plane; callers then fall back to the median hit.
 */
class SurfaceFuser(
    val minPoints: Int = 12,
    val minInlierRatio: Float = 0.6f,
    /** Trim band after the first fit: max(trimAbsM, trimRms * rms) around the plane. */
    val trimAbsM: Float = 0.01f,
    val trimRms: Float = 2.5f,
    /** Max RMS point-to-plane distance of the final (inlier) fit. */
    val maxRmsM: Float = 0.02f,
    /** Rays whose angle to the plane normal exceeds this are grazing: no fused hit. */
    val maxGrazingDeg: Float = 80f,
    /** The new plane is blended into the held one only if its centre is this close to it ... */
    val blendMaxOffsetM: Float = 0.03f,
    /** ... and its normal is within this angle of the held normal. */
    val blendMaxAngleDeg: Float = 15f,
    /** Weight of the new plane in the exponential blend. */
    val alpha: Float = 0.3f,
    /** The fused hit must lie within this distance of the raw median hit. */
    val maxFallbackDeviationM: Float = 0.05f,
) {
    private var lastKey: Any? = null
    private var held: Plane? = null
    private val cosGrazing = cos(Math.toRadians(maxGrazingDeg.toDouble())).toFloat()
    private val cosBlendAngle = cos(Math.toRadians(blendMaxAngleDeg.toDouble())).toFloat()

    fun reset() {
        held = null
        lastKey = null
    }

    /**
     * @param camera camera position (world).
     * @param rayDir unit (or near-unit) direction of the current ray, world space.
     * @param points world-space patch points, or null when no depth is available (resets).
     * @param key identifies the hit kind; a change resets the held plane.
     * @param fallbackHit raw median hit on the same ray; the fused hit must be within [maxFallbackDeviationM].
     * @return the fused hit on [rayDir] from [camera], or null.
     */
    fun update(camera: Vec3, rayDir: Vec3, points: List<Vec3>?, key: Any?, fallbackHit: Vec3?): Vec3? {
        if (key != lastKey) { held = null; lastKey = key }
        val dir = rayDir.normalized()
        val fit = points?.let { fitPlane(it) }?.let { orientTowards(it, camera) }
        if (fit == null || abs(fit.normal dot dir) < cosGrazing) {
            held = null
            return null
        }
        val prev = held
        val smoothed = if (prev != null) blend(prev, fit) else fit
        held = smoothed
        val denom = dir dot smoothed.normal
        if (abs(denom) < 1e-6f) return null
        val t = ((smoothed.point - camera) dot smoothed.normal) / denom
        if (t <= 0f) return null
        val hit = camera + dir * t
        if (fallbackHit != null && hit.distanceTo(fallbackHit) > maxFallbackDeviationM) return null
        return hit
    }

    /** Trimmed least-squares plane through [points], or null if too few / inliers / not planar. */
    internal fun fitPlane(points: List<Vec3>): Plane? {
        if (points.size < minPoints) return null
        val first = PlaneAccumulator().apply { points.forEach { add(it) } }.fit() ?: return null
        val tol = max(trimAbsM, trimRms * rmsOf(points, first))
        val inliers = points.filter { abs(first.signedDistance(it)) <= tol }
        if (inliers.size < minPoints || inliers.size < minInlierRatio * points.size) return null
        val plane = PlaneAccumulator().apply { inliers.forEach { add(it) } }.fit() ?: return null
        return if (rmsOf(inliers, plane) <= maxRmsM) plane else null
    }

    private fun blend(prev: Plane, cur: Plane): Plane {
        val offset = abs(prev.signedDistance(cur.point))
        if (offset > blendMaxOffsetM || (prev.normal dot cur.normal) < cosBlendAngle) return cur
        val n = (prev.normal * (1f - alpha) + cur.normal * alpha).normalized()
        val p = prev.point + (cur.point - prev.point) * alpha
        return Plane(p, n)
    }

    private fun orientTowards(p: Plane, camera: Vec3): Plane =
        if (p.normal dot (camera - p.point) < 0f) Plane(p.point, -p.normal) else p

    private fun rmsOf(points: List<Vec3>, plane: Plane): Float {
        var sum = 0.0
        for (p in points) {
            val d = plane.signedDistance(p).toDouble()
            sum += d * d
        }
        return kotlin.math.sqrt(sum / points.size).toFloat()
    }
}
