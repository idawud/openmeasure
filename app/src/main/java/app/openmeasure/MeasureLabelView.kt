package app.openmeasure

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.openmeasure.measure.confidence.ConfidenceReason
import app.openmeasure.measure.label.LabelContent
import app.openmeasure.measure.units.LengthFormatter
import app.openmeasure.measure.units.SpokenLength
import app.openmeasure.measure.units.UnitSystem
import app.openmeasure.ui.OmText

/** Pill with the path total; call only when every segment is displayable. Tap opens the detail sheet. */
@Composable
fun PathTotalChip(meters: Double, units: UnitSystem, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val spoken = SpokenLength.speak(meters, units) ?: LengthFormatter.format(meters, units)
    LabelPill(
        main = LengthFormatter.format(meters, units),
        level = null,
        description = stringResource(R.string.spoken_path_total, spoken),
        modifier = modifier,
        onClick = onClick,
        mainStyle = OmText.measureLarge,
        secondary = stringResource(R.string.measure_total_caption),
    )
}

/** Label content for one segment (positioned by [MeasureLabelsLayer]): length pill with confidence, or only a hint when no number may be shown. */
@Composable
fun MeasureLabelView(content: LabelContent, units: UnitSystem, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    when (content) {
        is LabelContent.Length -> {
            val badge = confidenceText(content.level)
            LabelPill(
                main = LengthFormatter.format(content.meters, units),
                level = content.level,
                description = stringResource(
                    R.string.spoken_line_confidence,
                    SpokenLength.speak(content.meters, units) ?: LengthFormatter.format(content.meters, units), badge,
                ),
                modifier = modifier,
                onClick = onClick,
            )
        }
        is LabelContent.Hint -> HintPill(hintText(content.reason), modifier)
    }
}

@Composable
internal fun hintText(reason: ConfidenceReason): String = stringResource(
    when (reason) {
        ConfidenceReason.TRACKING_POOR -> R.string.hint_tracking_poor
        ConfidenceReason.NO_HIT -> R.string.hint_no_hit
        ConfidenceReason.TOO_FAR -> R.string.hint_too_far
        ConfidenceReason.LOW_DEPTH_CONFIDENCE -> R.string.hint_low_depth
        ConfidenceReason.PLANE_ONLY -> R.string.hint_plane_only
        ConfidenceReason.OK -> R.string.hint_no_hit
    },
)
