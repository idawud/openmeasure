package app.openmeasure

import androidx.compose.runtime.State
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.dp
import app.openmeasure.measure.snap.SnapKind
import app.openmeasure.ui.OmColors

/** Reticle values are States read here (draw phase), so their animation never recomposes the overlay. */
internal fun DrawScope.drawMeasureScene(
    s: MeasureUiState,
    ready: Boolean,
    textMeasurer: TextMeasurer,
    reticleColor: State<Color>,
    pulse: State<Float>,
) {
    s.guide?.let { drawGuides(it) }
    s.box?.let { drawBoxEdges(it) }
    s.room?.let { drawRoomEdges(it) }
    s.segments.forEach { su ->
        val seg = su.segment ?: return@forEach
        val a = Offset(seg.a.x, seg.a.y)
        val b = Offset(seg.b.x, seg.b.y)
        val dash = if (su.live) PathEffect.dashPathEffect(floatArrayOf(16f, 12f)) else null
        drawLine(OmColors.LineOutline, a, b, strokeWidth = 4.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = dash)
        drawLine(Color.White, a, b, strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = dash)
    }
    s.angle?.let { drawAngleArc(it) }
    drawRuler(s.segments, textMeasurer)
    s.dots.forEachIndexed { i, d ->
        if (d == null) return@forEachIndexed
        val grown = if (s.ids.getOrNull(i) == s.draggingId) 4.dp.toPx() else 0f
        val at = Offset(d.x, d.y)
        drawCircle(OmColors.DotShadow, 9.dp.toPx() + grown, at + Offset(0f, 1.dp.toPx()))
        drawCircle(Color.White, 6.dp.toPx() + grown, at)
    }
    val snap = s.snapPoint?.takeIf { s.snapKind != SnapKind.NONE }
    if (ready) drawReticle(snap?.takeIf { s.draggingId == null }, reticleColor.value, pulse.value)
    if (snap != null) drawSnapMarker(s.snapKind, snap)
}

/** iOS-like reticle: ring with a centre dot; the ring breathes subtly and the whole thing is yellow when snapped / aligned. */
private fun DrawScope.drawReticle(snap: app.openmeasure.measure.projection.ScreenPoint?, color: Color, pulse: Float) {
    val c = Offset(size.width / 2, size.height / 2)
    if (snap != null) {
        // Snapped: the marker shows where the point will go; a thin line links it to the aim point.
        drawLine(color, c, Offset(snap.x, snap.y), strokeWidth = 1.5.dp.toPx())
    }
    val r = 20.dp.toPx() * pulse
    drawCircle(OmColors.LineOutline, r, c, style = Stroke(4.5.dp.toPx()))
    drawCircle(color, r, c, style = Stroke(2.5.dp.toPx()))
    drawCircle(OmColors.DotShadow, 4.dp.toPx(), c)
    drawCircle(color, 2.5.dp.toPx(), c)
}
