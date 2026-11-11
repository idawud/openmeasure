package app.openmeasure.measure.room

import app.openmeasure.measure.box.VolumeContent
import app.openmeasure.measure.confidence.ConfidenceInput
import app.openmeasure.measure.confidence.ConfidenceLevel
import app.openmeasure.measure.confidence.ConfidenceReason
import app.openmeasure.measure.confidence.ConfidenceResult
import app.openmeasure.measure.confidence.ConfidenceScorer
import app.openmeasure.measure.confidence.EndpointObservation
import app.openmeasure.measure.confidence.TrackingStatus
import app.openmeasure.measure.label.LabelContent
import app.openmeasure.measure.rect.AreaContent

/**
 * Confidence-gated labels for one [RoomPlan]. [walls] is parallel to the plan walls; [ceiling] and [volume]
 * are null until a ceiling height exists. Numbers only ever appear inside Length / Area / Volume.
 */
data class RoomLabels(
    val walls: List<LabelContent>,
    val area: AreaContent,
    val perimeter: LabelContent,
    val ceiling: LabelContent?,
    val volume: VolumeContent?,
    /** Weakest level over every corner (and the ceiling tap): the badge for the room as a whole. */
    val level: ConfidenceLevel,
)

object RoomLabel {
    /**
     * [obs] are the corner observations in TAP order (the plan maps walls back via [RoomPlan.sourceIndex]);
     * [ceilingObs] is the ceiling tap. A wall is gated by its two corners; area, perimeter, height and volume
     * by the weakest corner. UNEVEN_FLOOR caps every level at LOW.
     */
    fun build(
        tracking: TrackingStatus,
        obs: List<EndpointObservation>,
        plan: RoomPlan,
        ceilingObs: EndpointObservation? = null,
    ): RoomLabels {
        require(obs.size == plan.corners.size) { "one observation per corner" }
        val uneven = RoomWarning.UNEVEN_FLOOR in plan.warnings
        val walls = plan.wallLengths.indices.map { i ->
            val (a, b) = plan.wallSources(i)
            length(ConfidenceScorer.score(ConfidenceInput(tracking, obs[a], obs[b])), plan.wallLengths[i], uneven)
        }
        val all = weakest(tracking, obs)
        val area = area(all, plan, uneven)
        val perimeter = if (plan.isValid) length(all, plan.perimeterM, uneven) else LabelContent.Hint(ConfidenceReason.NO_HIT)
        val h = plan.ceilingHeightM
        if (h == null) return RoomLabels(walls, area, perimeter, null, null, cap(all.level, uneven))
        val withCeiling = weakest(tracking, obs + (ceilingObs ?: EndpointObservation.NONE))
        val ceiling = length(withCeiling, h, uneven)
        val volume = when {
            !withCeiling.canDisplayNumber -> VolumeContent.Hint(withCeiling.reason)
            !plan.isValid || plan.volumeM3?.isFinite() != true -> VolumeContent.Invalid
            else -> VolumeContent.Volume(plan.volumeM3!!, cap(withCeiling.level, uneven), withCeiling.score, uneven)
        }
        return RoomLabels(walls, area, perimeter, ceiling, volume, cap(withCeiling.level, uneven))
    }

    /** Weakest single endpoint (scored against itself, so every distance/plane cap applies). Empty -> NONE. */
    fun weakest(tracking: TrackingStatus, obs: List<EndpointObservation>): ConfidenceResult {
        if (obs.isEmpty()) return ConfidenceResult.none(ConfidenceReason.NO_HIT)
        return obs.map { ConfidenceScorer.score(ConfidenceInput(tracking, it, it)) }
            .maxWith(compareBy<ConfidenceResult> { it.level.ordinal }.thenByDescending { it.score })
    }

    private fun area(r: ConfidenceResult, plan: RoomPlan, uneven: Boolean): AreaContent = when {
        !r.canDisplayNumber -> AreaContent.Hint(r.reason)
        !plan.isValid || !plan.areaM2.isFinite() -> AreaContent.Invalid
        else -> AreaContent.Area(plan.areaM2, cap(r.level, uneven), r.score, plan.isRectilinear, uneven)
    }

    private fun length(r: ConfidenceResult, meters: Double, uneven: Boolean): LabelContent = when {
        !r.canDisplayNumber -> LabelContent.Hint(r.reason)
        !meters.isFinite() || meters < 0.0 -> LabelContent.Hint(ConfidenceReason.NO_HIT)
        else -> LabelContent.Length(meters, cap(r.level, uneven), r.score)
    }

    private fun cap(level: ConfidenceLevel, uneven: Boolean) =
        if (uneven && level.ordinal < ConfidenceLevel.LOW.ordinal) ConfidenceLevel.LOW else level
}
