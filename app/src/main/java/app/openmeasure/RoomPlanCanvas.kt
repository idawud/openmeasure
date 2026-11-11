package app.openmeasure

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.openmeasure.measure.confidence.ConfidenceLevel
import app.openmeasure.measure.label.LabelContent
import app.openmeasure.measure.rect.AreaContent
import app.openmeasure.measure.room.LabelSize
import app.openmeasure.measure.room.PlanLayout
import app.openmeasure.measure.room.RoomLabels
import app.openmeasure.measure.room.RoomPlan
import app.openmeasure.measure.units.AreaFormatter
import app.openmeasure.measure.units.LengthFormatter
import app.openmeasure.measure.units.UnitSystem

/** Colours for [PlanCanvas]; dark defaults suit the camera overlay. */
data class PlanColors(
    val wall: Color = Color.White,
    val fill: Color = Color(0x33FFEB3B),
    val text: Color = Color.White,
    val labelBg: Color = Color(0xCC000000),
)

/**
 * 2D floor plan (F15). Wall dimensions come from [labels] when given (gated: a hint wall shows "—", colours follow
 * the confidence level) or straight from the plan (saved history entries, which were displayable when saved).
 * [showDims] false = outline only (mini preview); [openLast] draws the closing wall dashed (still scanning).
 * Decorative for accessibility: callers put the spoken summary on the surrounding semantics.
 */
@Composable
fun PlanCanvas(
    plan: RoomPlan,
    units: UnitSystem,
    modifier: Modifier = Modifier,
    labels: RoomLabels? = null,
    showDims: Boolean = true,
    openLast: Boolean = false,
    colors: PlanColors = PlanColors(),
) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        val n = plan.corners.size
        if (n < 2) return@Canvas
        val style = TextStyle(color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        val texts = if (!showDims) emptyList() else plan.wallLengths.indices.map { i ->
            when (val l = labels?.walls?.getOrNull(i)) {
                null -> LengthFormatter.format(plan.wallLengths[i], units)
                is LabelContent.Length -> LengthFormatter.format(l.meters, units)
                is LabelContent.Hint -> "—"
            }
        }
        val measured = texts.map { measurer.measure(it, style) }
        val pad = 6.dp.toPx()
        val sizes = measured.map { LabelSize(it.size.width + 2 * pad, it.size.height + pad) }
        val margin = (sizes.maxOfOrNull { maxOf(it.w, it.h) } ?: 0f) + 10.dp.toPx()
        val fit = PlanLayout.fit(plan, size.width, size.height, margin.coerceAtMost(minOf(size.width, size.height) / 3f) + 4.dp.toPx(), 6.dp.toPx(), sizes)
        val c = fit.corners.map { Offset(it.x, it.y) }
        val closed = n >= 3 && !openLast
        val path = Path().apply {
            moveTo(c[0].x, c[0].y)
            for (i in 1 until n) lineTo(c[i].x, c[i].y)
            if (closed) close()
        }
        if (n >= 3) drawPath(Path().apply { addPath(path); close() }, colors.fill)
        drawPath(path, colors.wall, style = Stroke(3.dp.toPx(), join = StrokeJoin.Round))
        if (n >= 3 && openLast) {
            drawLine(colors.wall, c[n - 1], c[0], 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
        }
        c.forEachIndexed { i, p -> drawCircle(colors.wall, if (i == 0) 5.dp.toPx() else 3.dp.toPx(), p) }
        measured.forEachIndexed { i, t ->
            if (openLast && i == n - 1) return@forEachIndexed
            val at = fit.wallLabels[i]
            val s = sizes[i]
            val border = (labels?.walls?.getOrNull(i) as? LabelContent.Length)?.level?.let(::levelColour)
            drawRect(colors.labelBg, Offset(at.x - s.w / 2, at.y - s.h / 2), Size(s.w, s.h))
            border?.let { drawRect(it, Offset(at.x - s.w / 2, at.y - s.h / 2), Size(s.w, s.h), style = Stroke(1.5.dp.toPx())) }
            drawText(t, topLeft = Offset(at.x - t.size.width / 2f, at.y - t.size.height / 2f))
        }
        if (showDims && plan.isValid && !openLast) {
            val areaText = when (val a = labels?.area) {
                null -> AreaFormatter.format(plan.areaM2, units)
                is AreaContent.Area -> AreaFormatter.format(a.squareMeters, units)
                else -> null
            }
            areaText?.let {
                val t = measurer.measure(it, style.copy(fontSize = 16.sp))
                drawRect(
                    colors.labelBg, Offset(fit.center.x - t.size.width / 2f - pad, fit.center.y - t.size.height / 2f - pad / 2),
                    Size(t.size.width + 2 * pad, t.size.height + pad),
                )
                drawText(t, topLeft = Offset(fit.center.x - t.size.width / 2f, fit.center.y - t.size.height / 2f))
            }
        }
    }
}

internal fun levelColour(level: ConfidenceLevel): Color = when (level) {
    ConfidenceLevel.HIGH -> BadgeGreen
    ConfidenceLevel.MEDIUM -> BadgeYellow
    else -> BadgeRed
}
