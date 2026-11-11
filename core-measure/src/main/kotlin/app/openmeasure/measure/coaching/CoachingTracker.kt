package app.openmeasure.measure.coaching

import app.openmeasure.measure.confidence.ConfidenceLevel
import app.openmeasure.measure.confidence.ConfidenceScorer
import app.openmeasure.measure.confidence.TrackingStatus
import app.openmeasure.measure.hit.HitResult

enum class CoachingState { MOVE_SLOWLY, SCAN_SURFACE, READY }

/**
 * Derives [CoachingState] from tracking + latest hit with hysteresis.
 * Becomes READY after [readyAfterMs] of continuously valid hits; leaves READY after [holdMs] without one
 * (immediately if tracking is lost). Time is injected (any monotonic ms clock).
 */
class CoachingTracker(
    private val readyAfterMs: Long = 300,
    private val holdMs: Long = 500,
) {
    private var validSince: Long? = null
    private var lastValid = 0L
    private var ready = false

    fun update(nowMs: Long, tracking: TrackingStatus, hit: HitResult): CoachingState {
        val valid = tracking == TrackingStatus.TRACKING &&
            ConfidenceScorer.score(tracking, hit, hit).level.let { it != ConfidenceLevel.NONE }
        if (tracking != TrackingStatus.TRACKING) {
            validSince = null; ready = false
            return CoachingState.MOVE_SLOWLY
        }
        if (valid) {
            lastValid = nowMs
            val since = validSince ?: nowMs.also { validSince = it }
            if (nowMs - since >= readyAfterMs) ready = true
        } else {
            validSince = null
            if (ready && nowMs - lastValid > holdMs) ready = false
        }
        return if (ready) CoachingState.READY else CoachingState.SCAN_SURFACE
    }

    fun reset() { validSince = null; ready = false }
}
