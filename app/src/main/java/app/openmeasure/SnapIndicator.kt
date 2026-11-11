package app.openmeasure

import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.openmeasure.measure.projection.ScreenPoint
import app.openmeasure.measure.snap.SnapKind
import app.openmeasure.ui.OmColors
import app.openmeasure.ui.SmallPill
import kotlin.math.roundToInt

private val SnapColor = OmColors.Accent

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
            drawRect(OmColors.LineOutline, tl, Size(2 * r, 2 * r), style = outline)
            drawRect(SnapColor, tl, Size(2 * r, 2 * r), style = stroke)
        }
        SnapKind.CORNER -> {
            val path = Path().apply {
                moveTo(c.x, c.y - r * 1.3f); lineTo(c.x + r * 1.3f, c.y)
                lineTo(c.x, c.y + r * 1.3f); lineTo(c.x - r * 1.3f, c.y); close()
            }
            drawPath(path, OmColors.LineOutline, style = outline)
            drawPath(path, SnapColor, style = stroke)
        }
        SnapKind.NONE -> Unit
    }
    drawCircle(OmColors.DotShadow, 4.dp.toPx(), c)
    drawCircle(SnapColor, 2.5.dp.toPx(), c)
}

/** Small "Edge" / "Corner" dark pill below the snap marker; announced politely to TalkBack. */
@Composable
fun SnapLabel(kind: SnapKind, p: ScreenPoint?) {
    if (kind == SnapKind.NONE || p == null) return
    val text = stringResource(if (kind == SnapKind.CORNER) R.string.measure_snap_corner else R.string.measure_snap_edge)
    SmallPill(
        text,
        Modifier.offset { IntOffset(p.x.roundToInt() - 24.dp.roundToPx(), p.y.roundToInt() + 20.dp.roundToPx()) },
        dot = SnapColor,
    )
}
