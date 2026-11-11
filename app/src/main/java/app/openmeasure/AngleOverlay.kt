package app.openmeasure

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.openmeasure.measure.angle.AngleArc
import app.openmeasure.measure.angle.AngleContent
import app.openmeasure.measure.angle.AngleLabel
import app.openmeasure.ui.OmColors
import app.openmeasure.ui.OmText
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
    drawArc(OmColors.LineOutline, spec.startDeg, spec.sweepDeg, false, topLeft, Size(2 * r, 2 * r), style = Stroke(5.dp.toPx()))
    drawArc(Color.White, spec.startDeg, spec.sweepDeg, false, topLeft, Size(2 * r, 2 * r), style = Stroke(2.5.dp.toPx()))
}

/** Angle pill centred just beyond the arc along the bisector; number only when the content is [AngleContent.Angle]. */
@Composable
fun AngleLabelView(angle: AngleUi, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val v = angle.vertex ?: return
    val content = angle.content ?: return
    val spec = angle.a?.let { a -> angle.b?.let { b -> AngleArc.compute(v.x, v.y, a.x, a.y, b.x, b.y) } }
    val offset = with(LocalDensity.current) { (ARC_RADIUS + 34.dp).toPx() }
    val cx = v.x + (spec?.bisectorX ?: 0f) * offset
    val cy = v.y + (spec?.bisectorY ?: -1f) * offset
    val place = modifier.layout { measurable, constraints ->
        val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(p.width, p.height) { p.place((cx - p.width / 2f).roundToInt(), (cy - p.height / 2f).roundToInt()) }
    }
    when (content) {
        is AngleContent.Angle -> LabelPill(
            main = AngleLabel.format(content.degrees),
            level = content.level,
            description = stringResource(
                R.string.spoken_angle_confidence, AngleLabel.speak(content.degrees) ?: AngleLabel.format(content.degrees),
                confidenceText(content.level),
            ),
            modifier = place, onClick = onClick, mainStyle = OmText.measureLarge,
        )
        is AngleContent.Hint -> HintPill(hintText(content.reason), place)
    }
}
