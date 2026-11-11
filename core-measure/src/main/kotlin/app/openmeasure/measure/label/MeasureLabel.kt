package app.openmeasure.measure.label

import app.openmeasure.measure.confidence.ConfidenceInput
import app.openmeasure.measure.confidence.ConfidenceLevel
import app.openmeasure.measure.confidence.ConfidenceReason
import app.openmeasure.measure.confidence.ConfidenceScorer
import app.openmeasure.measure.confidence.EndpointObservation
import app.openmeasure.measure.confidence.TrackingStatus

/** What the on-screen label may show. A number only ever appears inside [Length]. */
sealed interface LabelContent {
    data class Length(val meters: Double, val level: ConfidenceLevel, val score: Float) : LabelContent
    data class Hint(val reason: ConfidenceReason) : LabelContent
}

object MeasureLabel {
    /** Gate the number behind [ConfidenceScorer]: !canDisplayNumber (or a non-finite length) -> hint only. */
    fun build(
        tracking: TrackingStatus,
        start: EndpointObservation,
        end: EndpointObservation,
        lengthMeters: Double,
    ): LabelContent {
        val r = ConfidenceScorer.score(ConfidenceInput(tracking, start, end))
        if (!r.canDisplayNumber) return LabelContent.Hint(r.reason)
        if (!lengthMeters.isFinite()) return LabelContent.Hint(ConfidenceReason.NO_HIT)
        return LabelContent.Length(lengthMeters, r.level, r.score)
    }
}
