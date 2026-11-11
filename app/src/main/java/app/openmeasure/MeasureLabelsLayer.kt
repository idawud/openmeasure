package app.openmeasure

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.openmeasure.measure.label.LabelBox
import app.openmeasure.measure.label.LabelLayout
import app.openmeasure.measure.projection.LabelPlacement
import app.openmeasure.measure.projection.ScreenPoint
import app.openmeasure.measure.projection.ScreenSegment
import app.openmeasure.measure.units.UnitSystem
import kotlin.math.roundToInt

/** Places every segment label at its midpoint, then nudges overlapping labels apart (core-measure LabelLayout). */
@Composable
fun MeasureLabelsLayer(
    segments: List<SegmentUi>,
    fallback: ScreenPoint,
    units: UnitSystem,
    modifier: Modifier = Modifier,
    onLabelClick: (() -> Unit)? = null,
) {
    val lift = with(LocalDensity.current) { 30.dp.toPx() }
    Layout(
        content = { segments.forEach { MeasureLabelView(it.label, units, onClick = onLabelClick) } },
        modifier = modifier,
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val boxes = placeables.mapIndexed { i, p ->
            val seg = segments[i].segment
            val at = LabelPlacement.place(
                seg ?: ScreenSegment(fallback, fallback), w, h, p.width / 2f, p.height / 2f,
                if (seg != null) lift else 0f,
            )
            LabelBox(at.x, at.y, p.width.toFloat(), p.height.toFloat())
        }
        val centres = LabelLayout.resolve(boxes, w, h)
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { i, p ->
                p.place((centres[i].x - p.width / 2f).roundToInt(), (centres[i].y - p.height / 2f).roundToInt())
            }
        }
    }
}
