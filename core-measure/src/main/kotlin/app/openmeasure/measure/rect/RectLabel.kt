package app.openmeasure.measure.rect

import app.openmeasure.measure.confidence.ConfidenceInput
import app.openmeasure.measure.confidence.ConfidenceLevel
import app.openmeasure.measure.confidence.ConfidenceReason
import app.openmeasure.measure.confidence.ConfidenceResult
import app.openmeasure.measure.confidence.ConfidenceScorer
import app.openmeasure.measure.confidence.EndpointObservation
import app.openmeasure.measure.confidence.TrackingStatus
import app.openmeasure.measure.label.LabelContent

/** What the area label may show. A number only ever appears inside [Area]. */
sealed interface AreaContent {
    data class Area(
        val squareMeters: Double,
        val level: ConfidenceLevel,
        val score: Float,
        val isRectangle: Boolean,
        /** Corners not on one flat surface: shown, but the level is capped at LOW. */
        val notPlanar: Boolean,
    ) : AreaContent

    data class Hint(val reason: ConfidenceReason) : AreaContent

    /** Corners do not form a usable convex quadrilateral (bow-tie, collinear, tiny). */
    data object Invalid : AreaContent
}

/** Side labels (side i = corners[i] -> corners[i+1]) and the area label for one quadrilateral. */
data class RectLabels(val sides: List<LabelContent>, val area: AreaContent)

object RectLabel {
    /**
     * [corners] are observations parallel to [result].corners (i.e. already permuted by [RectResult.order]).
     * Each side is gated by [ConfidenceScorer] like a line; the area is gated by its weakest side, and shows
     * nothing for a degenerate quad. NOT_PLANAR caps the area level at LOW.
     */
    fun build(tracking: TrackingStatus, corners: List<EndpointObservation>, result: RectResult): RectLabels {
        require(corners.size == 4) { "need 4 corner observations" }
        val scores = (0..3).map { ConfidenceScorer.score(ConfidenceInput(tracking, corners[it], corners[(it + 1) % 4])) }
        val sides = (0..3).map { i ->
            val r = scores[i]
            val len = result.sidesM[i]
            when {
                !r.canDisplayNumber -> LabelContent.Hint(r.reason)
                !len.isFinite() -> LabelContent.Hint(ConfidenceReason.NO_HIT)
                else -> LabelContent.Length(len, r.level, r.score)
            }
        }
        return RectLabels(sides, area(scores, result))
    }

    private fun area(scores: List<ConfidenceResult>, result: RectResult): AreaContent {
        val worst = scores.minWith(compareBy<ConfidenceResult> { it.level.ordinal * -1 }.thenBy { it.score })
        if (!worst.canDisplayNumber) return AreaContent.Hint(worst.reason)
        if (!result.isValid || !result.areaM2.isFinite()) return AreaContent.Invalid
        val notPlanar = RectWarning.NOT_PLANAR in result.warnings
        val level = if (notPlanar && worst.level.ordinal < ConfidenceLevel.LOW.ordinal) ConfidenceLevel.LOW else worst.level
        return AreaContent.Area(result.areaM2, level, worst.score, result.isRectangle, notPlanar)
    }
}
