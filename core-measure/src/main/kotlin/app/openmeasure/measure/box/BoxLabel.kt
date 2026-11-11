package app.openmeasure.measure.box

import app.openmeasure.measure.confidence.ConfidenceInput
import app.openmeasure.measure.confidence.ConfidenceLevel
import app.openmeasure.measure.confidence.ConfidenceReason
import app.openmeasure.measure.confidence.ConfidenceResult
import app.openmeasure.measure.confidence.ConfidenceScorer
import app.openmeasure.measure.confidence.EndpointObservation
import app.openmeasure.measure.confidence.TrackingStatus
import app.openmeasure.measure.label.LabelContent
import app.openmeasure.measure.units.UnitSystem
import app.openmeasure.measure.units.VolumeFormatter

/** What the volume label may show. A number only ever appears inside [Volume]. */
sealed interface VolumeContent {
    data class Volume(
        val cubicMeters: Double,
        val level: ConfidenceLevel,
        val score: Float,
        /** Base points not level: shown, but the level is capped at LOW. */
        val baseNotLevel: Boolean,
    ) : VolumeContent

    data class Hint(val reason: ConfidenceReason) : VolumeContent

    /** Box is degenerate (a side under 1 cm, or the top below the base). */
    data object Invalid : VolumeContent
}

/** Dimension labels and the volume label; [height] and [volume] are null until the top point is known. */
data class BoxLabels(
    val width: LabelContent,
    val depth: LabelContent,
    val height: LabelContent?,
    val volume: VolumeContent?,
)

object BoxLabel {
    /**
     * [obs] are the observations of A, B, C (and T when [box] != null) in tap order.
     * Each dimension is gated like a line by [ConfidenceScorer] over the points it depends on
     * (W: A,B; D: A,B,C; H: A,B,C,T); the volume uses the weakest of all four. BASE_NOT_LEVEL caps levels at LOW.
     */
    fun build(tracking: TrackingStatus, obs: List<EndpointObservation>, base: BoxBase, box: BoxResult?): BoxLabels {
        require(obs.size >= 3 && (box == null || obs.size >= 4)) { "need A, B, C (and T for a box)" }
        val notLevel = BoxWarning.BASE_NOT_LEVEL in base.warnings
        val width = length(worst(tracking, obs.take(2)), base.width, notLevel)
        val depth = length(worst(tracking, obs.take(3)), base.depth, notLevel)
        if (box == null) return BoxLabels(width, depth, null, null)
        val hScore = worst(tracking, obs.take(4))
        return BoxLabels(width, depth, length(hScore, box.height, notLevel), volume(hScore, box))
    }

    /** "Volume, 0.024 cubic metres, 24 litres"; null for invalid input. */
    fun speak(cubicMeters: Double, system: UnitSystem): String? =
        VolumeFormatter.speak(cubicMeters, system)?.let { "Volume, $it" }

    private fun volume(r: ConfidenceResult, box: BoxResult): VolumeContent {
        if (!r.canDisplayNumber) return VolumeContent.Hint(r.reason)
        if (!box.isValid || !box.volume.isFinite()) return VolumeContent.Invalid
        val notLevel = BoxWarning.BASE_NOT_LEVEL in box.warnings
        return VolumeContent.Volume(box.volume, cap(r.level, notLevel), r.score, notLevel)
    }

    private fun length(r: ConfidenceResult, meters: Double, notLevel: Boolean): LabelContent = when {
        !r.canDisplayNumber -> LabelContent.Hint(r.reason)
        !meters.isFinite() || meters < 0.0 -> LabelContent.Hint(ConfidenceReason.NO_HIT)
        else -> LabelContent.Length(meters, cap(r.level, notLevel), r.score)
    }

    private fun cap(level: ConfidenceLevel, notLevel: Boolean) =
        if (notLevel && level.ordinal < ConfidenceLevel.LOW.ordinal) ConfidenceLevel.LOW else level

    /** Weakest pairwise result over [points] (scorer already takes the weaker endpoint of each pair). */
    private fun worst(tracking: TrackingStatus, points: List<EndpointObservation>): ConfidenceResult =
        points.indices.flatMap { i -> (i + 1 until points.size).map { j -> ConfidenceScorer.score(ConfidenceInput(tracking, points[i], points[j])) } }
            .maxWith(compareBy<ConfidenceResult> { it.level.ordinal }.thenByDescending { it.score })
}
