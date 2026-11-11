package app.openmeasure

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.openmeasure.measure.box.BoxLabel
import app.openmeasure.measure.box.VolumeContent
import app.openmeasure.measure.units.UnitSystem
import app.openmeasure.measure.units.VolumeFormatter
import app.openmeasure.ui.HintBanner
import app.openmeasure.ui.OmColors
import app.openmeasure.ui.OmText
import kotlin.math.roundToInt

/** Thin white wireframe with a soft dark outline under the labelled W/D/H segments. */
internal fun DrawScope.drawBoxEdges(box: BoxUi) {
    val halo = 3.5.dp.toPx()
    val stroke = 1.5.dp.toPx()
    box.edges.forEach { drawLine(OmColors.LineOutline, Offset(it.a.x, it.a.y), Offset(it.b.x, it.b.y), strokeWidth = halo) }
    box.edges.forEach { drawLine(Color.White, Offset(it.a.x, it.a.y), Offset(it.b.x, it.b.y), strokeWidth = stroke) }
}

/** Current step instruction (polite live region so TalkBack reads each new step). */
@Composable
fun BoxStepHint(step: BoxStep, modifier: Modifier = Modifier) {
    val text = stringResource(
        when (step) {
            BoxStep.PLACE_A -> R.string.box_step_a
            BoxStep.PLACE_B -> R.string.box_step_b
            BoxStep.PLACE_C -> R.string.box_step_c
            BoxStep.PLACE_TOP -> R.string.box_step_top
            BoxStep.DONE -> R.string.box_step_done
        },
    )
    HintBanner(text, modifier)
}

/** Volume pill at the projected box centre: number only for [VolumeContent.Volume], with confidence (+ litres in metric). */
@Composable
fun BoxVolumeLabel(box: BoxUi, units: UnitSystem, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val center = box.center ?: return
    val volume = box.volume ?: return
    val place = modifier.layout { measurable, constraints ->
        val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(p.width, p.height) {
            p.place((center.x - p.width / 2f).roundToInt(), (center.y - p.height / 2f).roundToInt())
        }
    }
    if (volume !is VolumeContent.Volume) {
        HintPill(if (volume is VolumeContent.Hint) hintText(volume.reason) else stringResource(R.string.box_invalid), place)
        return
    }
    val notLevel = stringResource(R.string.box_base_not_level)
    val main = VolumeFormatter.format(volume.cubicMeters, units)
    val litres = volume.takeIf { units == UnitSystem.METRIC && it.cubicMeters >= LITRE_M3 }?.let { VolumeFormatter.litres(it.cubicMeters) }
    val description = stringResource(
        R.string.spoken_volume_confidence, BoxLabel.speak(volume.cubicMeters, units) ?: main, confidenceText(volume.level),
    ) + if (volume.baseNotLevel) ". $notLevel" else ""
    LabelPill(
        main, volume.level, description, place, onClick, OmText.measureLarge,
        secondary = litres, notes = if (volume.baseNotLevel) listOf(notLevel) else emptyList(),
    )
}

private const val LITRE_M3 = 0.001
