package app.openmeasure.measure.hit

import app.openmeasure.measure.geometry.Pose
import app.openmeasure.measure.geometry.Vec3
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Samples a depth map around a sub-pixel location and lifts it to a world-space point.
 *
 * Depth noise on a single ARCore depth pixel is ~1-2 cm at 1 m, so we take the median over a
 * (2r+1)^2 patch (robust to edge outliers and holes) and report a confidence that drops with
 * patch spread (depth edges, slanted/noisy surfaces), sparse coverage, low sensor confidence
 * and distance.
 */
class DepthHitTester(
    val patchRadius: Int = 2,
    /** Below this fraction of valid samples in the patch we report no hit. */
    val minValidFraction: Float = 0.4f,
    val minDepthMm: Int = 100,
    val maxDepthMm: Int = 8000,
    /** Robust relative spread (1.4826*MAD/median) at which the spread factor reaches 0. */
    val maxRelativeSpread: Float = 0.06f,
    /** A sample is an inlier if within max(abs, rel * median) of the patch median. */
    val inlierAbsMm: Int = 15,
    val inlierRel: Float = 0.02f,
    /** Distance range over which confidence falls linearly from 1 to 0. */
    val fullConfidenceRangeM: Float = 2.5f,
    val zeroConfidenceRangeM: Float = 6.0f,
    /** Sensor-confidence factor used when no confidence image is available. */
    val unknownSensorConfidence: Float = 0.7f,
) {

    /**
     * @param u,v pixel-index coordinates in [depth] (centre of pixel i is i).
     * @param intrinsics intrinsics already scaled to the depth map resolution.
     * @param cameraPose ARCore physical camera pose (OpenGL convention: +X right, +Y up, looks along -Z,
     *   aligned with image readout — i.e. `Camera.getPose()`, not the display-oriented pose).
     */
    fun hitTest(depth: DepthMap, intrinsics: CameraIntrinsics, u: Float, v: Float, cameraPose: Pose): DepthHit? {
        val stats = samplePatch(depth, u, v) ?: return null
        val depthM = stats.medianMm / 1000f
        val world = cameraPose.transformPoint(unprojectToCamera(intrinsics, u, v, depthM))
        val conf = confidence(depthM, stats.madMm / 1000f, stats.inlierFraction, stats.meanConfidence01)
        return DepthHit(world, depthM, conf, stats.validFraction)
    }

    /** Robust statistics of the depth patch, or null if too few valid samples / out of bounds. */
    fun samplePatch(depth: DepthMap, u: Float, v: Float): PatchStats? {
        val cx = u.roundToInt()
        val cy = v.roundToInt()
        if (cx !in 0 until depth.width || cy !in 0 until depth.height) return null
        val total = (2 * patchRadius + 1) * (2 * patchRadius + 1)
        val values = IntArray(total)
        var n = 0
        var confSum = 0L
        var confCount = 0
        for (y in cy - patchRadius..cy + patchRadius) {
            if (y !in 0 until depth.height) continue
            for (x in cx - patchRadius..cx + patchRadius) {
                if (x !in 0 until depth.width) continue
                depth.confidenceAt(x, y)?.let { confSum += it; confCount++ }
                val d = depth.depthAt(x, y)
                if (d in minDepthMm..maxDepthMm) values[n++] = d
            }
        }
        val validFraction = n.toFloat() / total
        if (n == 0 || validFraction < minValidFraction) return null
        val sorted = values.copyOf(n).also { it.sort() }
        val median = median(sorted)
        val deviations = FloatArray(n) { abs(sorted[it] - median) }.also { it.sort() }
        val mad = medianF(deviations)
        val meanConf = if (confCount > 0) (confSum.toFloat() / confCount) / 255f else null
        // MAD is blind to a minority surface (e.g. 3 vs 2 columns at a depth edge); inliers are not.
        val tol = maxOf(inlierAbsMm.toFloat(), inlierRel * median)
        val inliers = deviations.count { it <= tol }
        return PatchStats(median, mad, validFraction, inliers.toFloat() / total, meanConf)
    }

    /** 0..1 product of independent quality factors. */
    fun confidence(depthM: Float, madM: Float, inlierFraction: Float, meanConfidence01: Float?): Float {
        if (depthM <= 0f) return 0f
        val relSpread = 1.4826f * madM / depthM
        val spread = clamp01(1f - relSpread / maxRelativeSpread)
        val dist = clamp01(1f - (depthM - fullConfidenceRangeM) / (zeroConfidenceRangeM - fullConfidenceRangeM))
        // Raw-depth confidence is sparse (0 where no estimate); sqrt softens mid-range values.
        val sensor = meanConfidence01?.let { sqrt(clamp01(it)) } ?: unknownSensorConfidence
        return clamp01(spread * dist * clamp01(inlierFraction) * sensor)
    }

    data class PatchStats(
        val medianMm: Float,
        val madMm: Float,
        val validFraction: Float,
        /** Inliers around the median / total patch size: penalises holes and depth edges. */
        val inlierFraction: Float,
        val meanConfidence01: Float?,
    )

    companion object {
        /**
         * Pixel (u, v) at z-depth [depthM] -> ARCore camera space (+X right, +Y up, -Z forward).
         * Pinhole model gives OpenCV coords (y down, z forward); we flip Y and Z.
         */
        fun unprojectToCamera(k: CameraIntrinsics, u: Float, v: Float, depthM: Float): Vec3 {
            val x = (u - k.cx) * depthM / k.fx
            val y = (v - k.cy) * depthM / k.fy
            return Vec3(x, -y, -depthM)
        }

        fun median(sorted: IntArray): Float {
            val n = sorted.size
            return if (n % 2 == 1) sorted[n / 2].toFloat() else (sorted[n / 2 - 1] + sorted[n / 2]) / 2f
        }

        private fun medianF(sorted: FloatArray): Float {
            val n = sorted.size
            return if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2f
        }

        internal fun clamp01(f: Float): Float = f.coerceIn(0f, 1f)
    }
}
