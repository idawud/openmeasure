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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.openmeasure.measure.confidence.ConfidenceLevel
import app.openmeasure.measure.confidence.ConfidenceReason
import app.openmeasure.measure.label.LabelContent
import app.openmeasure.measure.projection.LabelPlacement
import app.openmeasure.measure.projection.ScreenPoint
import app.openmeasure.measure.projection.ScreenSegment
import app.openmeasure.measure.units.LengthFormatter
import app.openmeasure.measure.units.SpokenLength
import app.openmeasure.measure.units.UnitSystem
import kotlin.math.roundToInt

private val BadgeGreen = Color(0xFF69F0AE)
private val BadgeYellow = Color(0xFFFFFF00)
private val BadgeRed = Color(0xFFFF8A80)

/**
 * Screen-aligned label at the segment midpoint (always upright, independent of device rotation).
 * Shows the length plus a text+colour confidence badge, or only a hint when no number may be shown.
 */
@Composable
fun MeasureLabelView(
    content: LabelContent,
    segment: ScreenSegment?,
    fallback: ScreenPoint,
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
    val lift = with(androidx.compose.ui.platform.LocalDensity.current) { 28.dp.toPx() }
    Column(
        modifier
            .layout { measurable, constraints ->
                val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    val seg = segment ?: ScreenSegment(fallback, fallback)
                    val at = LabelPlacement.place(
                        seg, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(),
                        p.width / 2f, p.height / 2f, if (segment != null) lift else 0f,
                    )
                    val o = IntOffset((at.x - p.width / 2f).roundToInt(), (at.y - p.height / 2f).roundToInt())
                    p.place(o.x, o.y)
                }
            }
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
