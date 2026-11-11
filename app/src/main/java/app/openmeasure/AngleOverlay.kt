package app.openmeasure

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.openmeasure.measure.angle.AngleArc
import app.openmeasure.measure.angle.AngleContent
import app.openmeasure.measure.angle.AngleLabel
import app.openmeasure.measure.confidence.ConfidenceLevel
import kotlin.math.roundToInt

private val ARC_RADIUS = 28.dp

/** Small arc at the vertex (smaller sweep between the projected arms). */
internal fun DrawScope.drawAngleArc(angle: AngleUi) {
    val v = angle.vertex ?: return
    val a = angle.a ?: return
    val b = angle.b ?: return
    val spec = AngleArc.compute(v.x, v.y, a.x, a.y, b.x, b.y) ?: return
    val r = ARC_RADIUS.toPx()
    val topLeft = Offset(v.x - r, v.y - r)
    drawArc(Color.Black, spec.startDeg, spec.sweepDeg, false, topLeft, Size(2 * r, 2 * r), style = Stroke(7.dp.toPx()))
    drawArc(Color.White, spec.startDeg, spec.sweepDeg, false, topLeft, Size(2 * r, 2 * r), style = Stroke(3.dp.toPx()))
}

/** Angle label centred just beyond the arc along the bisector; number only when the content is [AngleContent.Angle]. */
@Composable
fun AngleLabelView(angle: AngleUi, modifier: Modifier = Modifier) {
    val v = angle.vertex ?: return
    val content = angle.content ?: return
    val spec = angle.a?.let { a -> angle.b?.let { b -> AngleArc.compute(v.x, v.y, a.x, a.y, b.x, b.y) } }
    val offset = with(LocalDensity.current) { (ARC_RADIUS + 34.dp).toPx() }
    val cx = v.x + (spec?.bisectorX ?: 0f) * offset
    val cy = v.y + (spec?.bisectorY ?: -1f) * offset
    val (colour, badge) = when (content) {
        is AngleContent.Angle -> when (content.level) {
            ConfidenceLevel.HIGH -> BadgeGreen to stringResource(R.string.confidence_high)
            ConfidenceLevel.MEDIUM -> BadgeYellow to stringResource(R.string.confidence_medium)
            else -> BadgeRed to stringResource(R.string.confidence_low)
        }
        is AngleContent.Hint -> BadgeRed to ""
    }
    val description = when (content) {
        is AngleContent.Angle -> stringResource(
            R.string.spoken_angle_confidence, AngleLabel.speak(content.degrees) ?: AngleLabel.format(content.degrees), badge,
        )
        is AngleContent.Hint -> hintText(content.reason)
    }
    Column(
        modifier
            .layout { measurable, constraints ->
                val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(p.width, p.height) { p.place((cx - p.width / 2f).roundToInt(), (cy - p.height / 2f).roundToInt()) }
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
            is AngleContent.Angle -> {
                Text(AngleLabel.format(content.degrees), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(badge, color = colour, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            is AngleContent.Hint -> Text(hintText(content.reason), color = BadgeRed, fontSize = 14.sp)
        }
    }
}
