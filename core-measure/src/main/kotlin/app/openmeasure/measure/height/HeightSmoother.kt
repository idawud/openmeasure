package app.openmeasure.measure.height

import app.openmeasure.measure.confidence.ConfidenceLevel
import kotlin.math.abs
import kotlin.math.max

/**
 * Smoothed live readout. [heightMeters] is non-null only when the latest frame was OK and at least
 * `minSamples` valid estimates are buffered; [stable] additionally requires a robust spread below the threshold.
 */
data class SmoothedHeight(
    val heightMeters: Float?,
    val confidenceLevel: ConfidenceLevel,
    /** Reason of the latest frame (OK while still collecting samples). */
    val reason: HeightReason,
    val stable: Boolean,
    /** Robust spread of the inliers (1.4826 x MAD), metres. */
    val spreadM: Float,
    val sampleCount: Int,
) {
    val isCollecting: Boolean get() = reason == HeightReason.OK && heightMeters == null

    companion object {
        val EMPTY = SmoothedHeight(null, ConfidenceLevel.NONE, HeightReason.NO_PERSON, false, 0f, 0)
    }
}

/**
 * Median of the last [window] valid estimates with outlier rejection (|x - median| > max(k*spread, [minOutlierTolM])).
 * The buffer survives brief dropouts but is cleared after [resetAfterInvalid] consecutive non-OK frames
 * (person left / a different person). Not thread-safe; feed it from one thread.
 */
class HeightSmoother(
    val window: Int = 15,
    val minSamples: Int = 5,
    val minStableSamples: Int = 8,
    val stableSpreadM: Float = 0.01f,
    val outlierK: Float = 3f,
    val minOutlierTolM: Float = 0.02f,
    val resetAfterInvalid: Int = 10,
) {
    private val heights = ArrayDeque<Float>()
    private val levels = ArrayDeque<ConfidenceLevel>()
    private var invalidRun = 0

    fun reset() {
        heights.clear(); levels.clear(); invalidRun = 0
    }

    fun add(r: HeightResult): SmoothedHeight {
        val h = r.heightMeters
        if (!r.isOk || h == null || r.confidenceLevel == ConfidenceLevel.NONE) {
            if (++invalidRun >= resetAfterInvalid) { heights.clear(); levels.clear() }
            return SmoothedHeight(null, ConfidenceLevel.NONE, r.reason, false, 0f, heights.size)
        }
        invalidRun = 0
        heights.addLast(h); levels.addLast(r.confidenceLevel)
        while (heights.size > window) { heights.removeFirst(); levels.removeFirst() }
        if (heights.size < minSamples) {
            return SmoothedHeight(null, ConfidenceLevel.NONE, HeightReason.OK, false, 0f, heights.size)
        }

        val all = heights.toList()
        val med0 = median(all)
        val tol = max(outlierK * robustSpread(all, med0), minOutlierTolM)
        val inlierIdx = all.indices.filter { abs(all[it] - med0) <= tol }
        val inliers = inlierIdx.map { all[it] }
        val med = median(inliers)
        val spread = robustSpread(inliers, med)
        val stable = inliers.size >= minStableSamples && spread < stableSpreadM

        // Badge: the weaker of the latest frame and the typical inlier, capped at MEDIUM until stable.
        val typical = inlierIdx.map { levels.elementAt(it) }.sortedBy { it.ordinal }[inlierIdx.size / 2]
        var level = if (r.confidenceLevel.ordinal > typical.ordinal) r.confidenceLevel else typical
        if (!stable && level == ConfidenceLevel.HIGH) level = ConfidenceLevel.MEDIUM
        return SmoothedHeight(med, level, HeightReason.OK, stable, spread, inliers.size)
    }

    private fun median(v: List<Float>): Float = HeightEstimator.median(v)

    private fun robustSpread(v: List<Float>, med: Float): Float = 1.4826f * median(v.map { abs(it - med) })
}
