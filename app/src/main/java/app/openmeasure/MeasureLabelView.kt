package app.openmeasure

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.openmeasure.measure.confidence.ConfidenceLevel
import app.openmeasure.measure.confidence.ConfidenceReason
import app.openmeasure.measure.label.LabelContent
import app.openmeasure.measure.units.LengthFormatter
import app.openmeasure.measure.units.SpokenLength
import app.openmeasure.measure.units.UnitSystem

private val BadgeGreen = Color(0xFF69F0AE)
private val BadgeYellow = Color(0xFFFFFF00)
private val BadgeRed = Color(0xFFFF8A80)

/** Chip with the path total; call only when every segment is displayable. */
@Composable
fun PathTotalChip(meters: Double, units: UnitSystem, modifier: Modifier = Modifier) {
    val spoken = SpokenLength.speak(meters, units) ?: LengthFormatter.format(meters, units)
    val description = stringResource(R.string.spoken_path_total, spoken)
    Text(
        stringResource(R.string.measure_total, LengthFormatter.format(meters, units)),
        modifier
            .background(Color(0xCC000000))
            .border(2.dp, Color.White)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .semantics {
                contentDescription = description
                liveRegion = LiveRegionMode.Polite
            },
        color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
    )
}

/**
 * Label content for one segment (positioned by [MeasureLabelsLayer]). Shows the length plus a text+colour confidence badge, or only a hint when no number may be shown.
 */
@Composable
fun MeasureLabelView(
    content: LabelContent,
    units: UnitSystem,
    modifier: Modifier = Modifier,
) {
    val (colour, badge) = when (content) {
        is LabelContent.Length -> when (content.level) {
            ConfidenceLevel.HIGH -> BadgeGreen to stringResource(R.string.confidence_high)
            ConfidenceLevel.MEDIUM -> BadgeYellow to stringResource(R.string.confidence_medium)
            else -> BadgeRed to stringResource(R.string.confidence_low)
        }
        is LabelContent.Hint -> BadgeRed to ""
    }
    val description = when (content) {
        is LabelContent.Length -> stringResource(
            R.string.spoken_line_confidence, SpokenLength.speak(content.meters, units) ?: LengthFormatter.format(content.meters, units), badge,
        )
        is LabelContent.Hint -> hintText(content.reason)
    }
    Column(
        modifier
            .background(Color(0xCC000000))
            .border(2.dp, colour)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .semantics {
                contentDescription = description
                liveRegion = LiveRegionMode.Polite
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (content) {
            is LabelContent.Length -> {
                Text(
                    LengthFormatter.format(content.meters, units),
                    color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                )
                Text(badge, color = colour, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            is LabelContent.Hint -> Text(hintText(content.reason), color = BadgeRed, fontSize = 14.sp)
        }
    }
}

@Composable
private fun hintText(reason: ConfidenceReason): String = stringResource(
    when (reason) {
        ConfidenceReason.TRACKING_POOR -> R.string.hint_tracking_poor
        ConfidenceReason.NO_HIT -> R.string.hint_no_hit
        ConfidenceReason.TOO_FAR -> R.string.hint_too_far
        ConfidenceReason.LOW_DEPTH_CONFIDENCE -> R.string.hint_low_depth
        ConfidenceReason.PLANE_ONLY -> R.string.hint_plane_only
        ConfidenceReason.OK -> R.string.hint_no_hit
    },
)
