package app.openmeasure

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp
import app.openmeasure.measure.geometry.Vec3
import app.openmeasure.measure.label.LabelContent
import app.openmeasure.measure.ruler.RulerTicks
import app.openmeasure.measure.ruler.TickKind
import app.openmeasure.measure.units.UnitSystem
import kotlin.math.abs
import kotlin.math.hypot

/** A ruler tick in view pixels. [nx],[ny] is the unit screen-space perpendicular, pointing downward (label side). */
data class ScreenTick(val x: Float, val y: Float, val nx: Float, val ny: Float, val kind: TickKind, val label: String?)

/** Builds screen-space ticks (F13) for one segment; only for a displayable [LabelContent.Length]. */
internal object RulerBuilder {
    fun build(a: Vec3, b: Vec3, label: LabelContent, units: UnitSystem?, f: FrameView): List<ScreenTick> {
        if (units == null || label !is LabelContent.Length) return emptyList() // never ticks without a number
        val pa = f.project(a) ?: return emptyList()
        val pb = f.project(b) ?: return emptyList()
        val len = label.meters
        val dx = pb.x - pa.x
        val dy = pb.y - pa.y
        val px = hypot(dx, dy)
        if (len <= 0.0 || px < 1f) return emptyList()
        val ticks = RulerTicks.generate(len, units, px / len)
        if (ticks.isEmpty()) return emptyList()
        var nx = -dy / px
        var ny = dx / px
        if (ny < 0f || (ny == 0f && nx < 0f)) { nx = -nx; ny = -ny }
        val dir = (b - a) * (1f / len.toFloat())
        val mid = f.project(a + (b - a) * 0.5f) ?: return emptyList()
        val out = ArrayList<ScreenTick>(ticks.size)
        for (t in ticks) {
            val p = f.project(a + dir * t.positionMeters.toFloat()) ?: continue
            out += ScreenTick(p.x, p.y, nx, ny, t.kind, t.label)
        }
        // The segment's own midpoint label sits there: drop the nearest ruler label.
        val nearest = out.indices.filter { out[it].label != null }
            .minByOrNull { hypot(out[it].x - mid.x, out[it].y - mid.y) }
        if (nearest != null) out[nearest] = out[nearest].copy(label = null)
        return out
    }
}

/** Draws tick marks and major labels (decorative: no semantics) for every segment that has ticks. */
internal fun DrawScope.drawRuler(segments: List<SegmentUi>, measurer: TextMeasurer) {
    val density = this.density
    val style = TextStyle(color = Color.White, fontSize = 12.sp)
    for (seg in segments) for (t in seg.ticks) {
        val len = density * when (t.kind) { TickKind.MAJOR -> 14f; TickKind.MEDIUM -> 10f; TickKind.MINOR -> 6f }
        val from = Offset(t.x, t.y)
        val to = Offset(t.x + t.nx * len, t.y + t.ny * len)
        drawLine(Color.Black, from, to, strokeWidth = 3f * density)
        drawLine(Color.White, from, to, strokeWidth = 1.5f * density)
        val text = t.label ?: continue
        val m = measurer.measure(text, style)
        val w = m.size.width.toFloat()
        val h = m.size.height.toFloat()
        val reach = len + 4f * density + abs(t.nx) * w / 2f + abs(t.ny) * h / 2f
        val cx = (t.x + t.nx * reach).coerceIn(w / 2f, maxOf(w / 2f, size.width - w / 2f))
        val cy = (t.y + t.ny * reach).coerceIn(h / 2f, maxOf(h / 2f, size.height - h / 2f))
        drawRect(Color(0xCC000000), Offset(cx - w / 2f - 3f, cy - h / 2f), Size(w + 6f, h))
        drawText(m, topLeft = Offset(cx - w / 2f, cy - h / 2f))
    }
}
