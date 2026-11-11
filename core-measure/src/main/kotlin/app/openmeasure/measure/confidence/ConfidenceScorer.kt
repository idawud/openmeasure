package app.openmeasure.measure.confidence

import app.openmeasure.measure.hit.HitResult
import app.openmeasure.measure.hit.HitSource

/** Engine-side tracking state; the AR layer maps ARCore's TrackingState onto this. */
enum class TrackingStatus { TRACKING, PAUSED, STOPPED }

enum class ConfidenceLevel { HIGH, MEDIUM, LOW, NONE }

/** Why the level is what it is, for a short UI hint. Ordered by priority. */
enum class ConfidenceReason {
    TRACKING_POOR,
    NO_HIT,
    TOO_FAR,
    LOW_DEPTH_CONFIDENCE,
    /** Capped at MEDIUM only because an endpoint is a plane hit without strong depth agreement. */
    PLANE_ONLY,
    OK,
}

/**
 * One measured endpoint as seen by the hit test.
 * [confidence] 0..1 or null (unknown -> a per-source default is used).
 * [cameraDistanceM] straight-line camera-to-point distance in metres.
 */
data class EndpointObservation(
    val source: HitSource,
    val confidence: Float?,
    val cameraDistanceM: Float,
) {
    companion object {
        fun from(hit: HitResult) = EndpointObservation(hit.source, hit.confidence, hit.distanceM)
        val NONE = EndpointObservation(HitSource.NONE, null, 0f)
    }
}

/** Input for one length: tracking state plus both endpoints (use the same endpoint twice for a single point). */
data class ConfidenceInput(
    val tracking: TrackingStatus,
    val start: EndpointObservation,
    val end: EndpointObservation,
)

/**
 * [level] is authoritative for the badge; [score] is the raw 0..1 weakest-endpoint score
 * (it is not lowered by the plane/far caps). [canDisplayNumber] is false iff [level] == NONE.
 */
data class ConfidenceResult(
    val level: ConfidenceLevel,
    val score: Float,
    val reason: ConfidenceReason,
) {
    val canDisplayNumber: Boolean get() = level != ConfidenceLevel.NONE

    companion object {
        fun none(reason: ConfidenceReason) = ConfidenceResult(ConfidenceLevel.NONE, 0f, reason)
    }
}

/**
 * Combines tracking state, per-endpoint hit confidence and camera distance into a badge.
 *
 * Rules:
 * - tracking != TRACKING -> NONE/TRACKING_POOR; any endpoint NONE -> NONE/NO_HIT.
 * - Endpoint score = confidence (or source default) x distance factor; the weakest endpoint wins.
 * - Distance factor is 1 up to [FAR_START_M], then falls linearly to [FAR_MIN_FACTOR] at [MAX_RANGE_M];
 *   beyond [MAX_RANGE_M] -> NONE/TOO_FAR. Any endpoint beyond [FAR_START_M] also caps the level at MEDIUM.
 *   (Hit confidences from HitResolver/DepthHitTester already decay past 2.5 m, so this factor is gentle.)
 * - A PLANE endpoint with raw confidence < [PLANE_HIGH_MIN_CONFIDENCE] caps the level at MEDIUM.
 * - Score below [MIN_DISPLAY_SCORE] -> NONE (never show a number we would not trust).
 */
object ConfidenceScorer {
    const val HIGH_MIN_SCORE = 0.75f
    const val MEDIUM_MIN_SCORE = 0.45f
    const val MIN_DISPLAY_SCORE = 0.15f

    const val FAR_START_M = 3.0f
    const val MAX_RANGE_M = 8.0f
    const val FAR_MIN_FACTOR = 0.5f

    /** Plane-only hits from HitResolver top out at 0.85, so they stay MEDIUM unless depth agreed. */
    const val PLANE_HIGH_MIN_CONFIDENCE = 0.9f
    const val DEFAULT_PLANE_CONFIDENCE = 0.7f
    const val DEFAULT_DEPTH_CONFIDENCE = 0.5f

    fun score(input: ConfidenceInput): ConfidenceResult {
        if (input.tracking != TrackingStatus.TRACKING) return ConfidenceResult.none(ConfidenceReason.TRACKING_POOR)
        val ends = listOf(input.start, input.end)
        if (ends.any { it.source == HitSource.NONE }) return ConfidenceResult.none(ConfidenceReason.NO_HIT)
        if (ends.any { !it.cameraDistanceM.isFinite() || it.cameraDistanceM > MAX_RANGE_M }) {
            return ConfidenceResult.none(ConfidenceReason.TOO_FAR)
        }

        val score = ends.minOf { endpointScore(it) }
        val far = ends.any { it.cameraDistanceM > FAR_START_M }
        val weakPlane = ends.any { it.source == HitSource.PLANE && rawConfidence(it) < PLANE_HIGH_MIN_CONFIDENCE }

        var level = levelFor(score)
        if ((far || weakPlane) && level == ConfidenceLevel.HIGH) level = ConfidenceLevel.MEDIUM

        val reason = when {
            level == ConfidenceLevel.HIGH -> ConfidenceReason.OK
            far -> ConfidenceReason.TOO_FAR
            weakPlane && score >= HIGH_MIN_SCORE -> ConfidenceReason.PLANE_ONLY
            else -> ConfidenceReason.LOW_DEPTH_CONFIDENCE
        }
        return ConfidenceResult(level, score, reason)
    }

    fun score(tracking: TrackingStatus, start: HitResult, end: HitResult): ConfidenceResult =
        score(ConfidenceInput(tracking, EndpointObservation.from(start), EndpointObservation.from(end)))

    fun levelFor(score: Float): ConfidenceLevel = when {
        score >= HIGH_MIN_SCORE -> ConfidenceLevel.HIGH
        score >= MEDIUM_MIN_SCORE -> ConfidenceLevel.MEDIUM
        score >= MIN_DISPLAY_SCORE -> ConfidenceLevel.LOW
        else -> ConfidenceLevel.NONE
    }

    /** 1 at <= [FAR_START_M], linear down to [FAR_MIN_FACTOR] at [MAX_RANGE_M]. */
    fun distanceFactor(distanceM: Float): Float {
        if (distanceM <= FAR_START_M) return 1f
        val t = ((distanceM - FAR_START_M) / (MAX_RANGE_M - FAR_START_M)).coerceIn(0f, 1f)
        return 1f - t * (1f - FAR_MIN_FACTOR)
    }

    internal fun endpointScore(e: EndpointObservation): Float =
        (rawConfidence(e) * distanceFactor(e.cameraDistanceM)).coerceIn(0f, 1f)

    private fun rawConfidence(e: EndpointObservation): Float {
        val c = e.confidence ?: when (e.source) {
            HitSource.PLANE -> DEFAULT_PLANE_CONFIDENCE
            else -> DEFAULT_DEPTH_CONFIDENCE
        }
        return if (c.isFinite()) c.coerceIn(0f, 1f) else 0f
    }
}
