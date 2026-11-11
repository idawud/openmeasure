package app.openmeasure

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.openmeasure.measure.projection.ScreenPoint
import app.openmeasure.measure.snap.SnapKind
import kotlin.math.roundToInt

private val SnapColor = Color(0xFF00E5FF)

/**
 * Draws the snap marker at [p]: a square for EDGE, a diamond with corner ticks for CORNER (shape differs,
 * not just colour), with a dark outline for contrast on any background.
 */
fun DrawScope.drawSnapMarker(kind: SnapKind, p: ScreenPoint) {
    if (kind == SnapKind.NONE) return
    val c = Offset(p.x, p.y)
    val r = 12.dp.toPx()
    val outline = Stroke(6.dp.toPx())
    val stroke = Stroke(3.dp.toPx())
    when (kind) {
        SnapKind.EDGE -> {
            val tl = Offset(c.x - r, c.y - r)
            drawRect(Color.Black, tl, Size(2 * r, 2 * r), style = outline)
            drawRect(SnapColor, tl, Size(2 * r, 2 * r), style = stroke)
        }
        SnapKind.CORNER -> {
            val path = Path().apply {
                moveTo(c.x, c.y - r * 1.3f); lineTo(c.x + r * 1.3f, c.y)
                lineTo(c.x, c.y + r * 1.3f); lineTo(c.x - r * 1.3f, c.y); close()
            }
            drawPath(path, Color.Black, style = outline)
            drawPath(path, SnapColor, style = stroke)
        }
        SnapKind.NONE -> Unit
    }
    drawCircle(Color.Black, 4.dp.toPx(), c)
    drawCircle(SnapColor, 2.5.dp.toPx(), c)
}

/** Small "Edge" / "Corner" text chip below the snap marker; announced politely to TalkBack. */
@Composable
fun SnapLabel(kind: SnapKind, p: ScreenPoint?) {
    if (kind == SnapKind.NONE || p == null) return
    val text = stringResource(if (kind == SnapKind.CORNER) R.string.measure_snap_corner else R.string.measure_snap_edge)
    Box(
        Modifier
            .offset { IntOffset(p.x.roundToInt() - 24.dp.roundToPx(), p.y.roundToInt() + 20.dp.roundToPx()) }
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(text, color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}
