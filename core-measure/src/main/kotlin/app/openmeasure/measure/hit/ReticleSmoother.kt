package app.openmeasure.measure.hit

import app.openmeasure.measure.geometry.Vec3
import kotlin.math.abs
import kotlin.math.max

/**
 * Temporal smoothing of the live reticle hit so a placed point lands where the reticle visibly was,
 * not on one noisy depth sample.
 *
 * Per-pixel depth noise is almost entirely ALONG the camera ray, so the output always stays on the
 * CURRENT ray (camera -> raw hit) and only the distance along it is filtered: the median of the
 * along-ray depths of recent hits that still lie close to the current ray. Old samples drop out
 * automatically as the camera moves (they leave the ray gate), so there is no lag while panning.
 * The window resets when the hit [key] changes (source / snap kind) or the depth jumps (new object or
 * an edge), so discontinuities are never blurred.
 */
class ReticleSmoother(
    val window: Int = 8,
    /** A past hit counts if its distance to the current ray is within max(abs, rel * depth). */
    val gateAbsM: Float = 0.003f,
    val gateRel: Float = 0.003f,
    /** Raw depth farther than max(abs, rel * depth) from the window median resets the window. */
    val jumpAbsM: Float = 0.03f,
    val jumpRel: Float = 0.03f,
) {
    private val samples = ArrayDeque<Vec3>()
    private var lastKey: Any? = null

    fun reset() {
        samples.clear()
        lastKey = null
    }

    /**
     * [hit] is this frame's raw hit (null = no hit, resets), [camera] the camera position, [key] any
     * value identifying the hit kind; a change of key resets. Returns the smoothed hit on the current ray.
     */
    fun update(camera: Vec3, hit: Vec3?, key: Any? = null): Vec3? {
        if (hit == null) { reset(); return null }
        val toHit = hit - camera
        val rawDepth = toHit.length()
        if (rawDepth < 1e-4f) { reset(); return hit }
        if (key != lastKey) { samples.clear(); lastKey = key }
        val dir = toHit * (1f / rawDepth)
        val gate = max(gateAbsM, gateRel * rawDepth)
        val depths = ArrayList<Float>(samples.size + 1)
        for (s in samples) {
            val v = s - camera
            val along = v dot dir
            if (along <= 0f) continue
            val perp = (v - dir * along).length()
            if (perp <= gate) depths += along
        }
        if (depths.isNotEmpty() && abs(rawDepth - median(depths)) > max(jumpAbsM, jumpRel * rawDepth)) {
            samples.clear()
            depths.clear()
        }
        samples.addLast(hit)
        while (samples.size > window) samples.removeFirst()
        depths += rawDepth
        return camera + dir * median(depths)
    }

    private fun median(v: List<Float>): Float {
        val s = v.sorted()
        val n = s.size
        return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) * 0.5f
    }
}
