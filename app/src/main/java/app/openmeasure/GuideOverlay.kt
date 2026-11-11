package app.openmeasure

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.openmeasure.measure.guide.GuideState
import app.openmeasure.measure.projection.ScreenSegment
import app.openmeasure.ui.OmColors
import app.openmeasure.ui.SmallPill

private val GuideFaint = Color(0xB3FFFFFF)
private val GuideActive = OmColors.Accent

/** Draws faint dashed level/plumb references through the start point; the aligned one is thicker and brighter. */
fun DrawScope.drawGuides(g: GuideUi) {
    fun line(seg: ScreenSegment?, on: Boolean) {
        seg ?: return
        val a = Offset(seg.a.x, seg.a.y)
        val b = Offset(seg.b.x, seg.b.y)
        drawLine(OmColors.LineOutline, a, b, strokeWidth = (if (on) 5f else 3f).dp.toPx())
        drawLine(
            if (on) GuideActive else GuideFaint, a, b,
            strokeWidth = (if (on) 2.5f else 1.5f).dp.toPx(),
            pathEffect = if (on) null else PathEffect.dashPathEffect(floatArrayOf(14f, 14f)),
        )
    }
    line(g.level, g.state == GuideState.HORIZONTAL)
    line(g.plumb, g.state == GuideState.VERTICAL)
}

/** "Level" / "Plumb" dark pill shown only when aligned (text, not colour alone); announced politely. */
@Composable
fun GuideChip(state: GuideState, modifier: Modifier = Modifier) {
    if (state == GuideState.NONE) return
    SmallPill(stringResource(if (state == GuideState.HORIZONTAL) R.string.measure_guide_level else R.string.measure_guide_plumb), modifier, dot = GuideActive)
}
