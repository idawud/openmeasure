package app.openmeasure

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.openmeasure.measure.guide.GuideState
import app.openmeasure.measure.projection.ScreenSegment

private val GuideFaint = Color(0x99FFFFFF)
private val GuideActive = Color(0xFF69F0AE)

/** Draws faint dashed level/plumb references through the start point; the aligned one is thicker and brighter. */
fun DrawScope.drawGuides(g: GuideUi) {
    fun line(seg: ScreenSegment?, on: Boolean) {
        seg ?: return
        val a = Offset(seg.a.x, seg.a.y)
        val b = Offset(seg.b.x, seg.b.y)
        if (on) drawLine(Color.Black, a, b, strokeWidth = 6.dp.toPx())
        drawLine(
            if (on) GuideActive else GuideFaint, a, b,
            strokeWidth = (if (on) 3f else 1.5f).dp.toPx(),
            pathEffect = if (on) null else PathEffect.dashPathEffect(floatArrayOf(14f, 14f)),
        )
    }
    line(g.level, g.state == GuideState.HORIZONTAL)
    line(g.plumb, g.state == GuideState.VERTICAL)
}

/** "Level" / "Plumb" chip shown only when aligned (text, not colour alone); announced politely. */
@Composable
fun GuideChip(state: GuideState, modifier: Modifier = Modifier) {
    if (state == GuideState.NONE) return
    val text = stringResource(if (state == GuideState.HORIZONTAL) R.string.measure_guide_level else R.string.measure_guide_plumb)
    Box(
        modifier
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(text, color = GuideActive, style = MaterialTheme.typography.labelLarge)
    }
}
