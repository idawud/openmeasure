package app.openmeasure

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.openmeasure.measure.box.BoxLabel
import app.openmeasure.measure.box.VolumeContent
import app.openmeasure.measure.confidence.ConfidenceLevel
import app.openmeasure.measure.units.UnitSystem
import app.openmeasure.measure.units.VolumeFormatter
import kotlin.math.roundToInt

/** Thin white wireframe (black halo) under the yellow labelled W/D/H segments. */
internal fun DrawScope.drawBoxEdges(box: BoxUi) {
    val halo = 4.dp.toPx()
    val stroke = 1.5.dp.toPx()
    box.edges.forEach { drawLine(Color.Black, Offset(it.a.x, it.a.y), Offset(it.b.x, it.b.y), strokeWidth = halo) }
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
    Text(
        text,
        modifier.background(Color(0xCC000000)).padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = Color.White, fontSize = 15.sp,
    )
}

/** Volume label at the projected box centre: number only for [VolumeContent.Volume], with text badge (+ litres in metric). */
@Composable
fun BoxVolumeLabel(box: BoxUi, units: UnitSystem, modifier: Modifier = Modifier) {
    val center = box.center ?: return
    val volume = box.volume ?: return
    val (colour, badge) = when (volume) {
        is VolumeContent.Volume -> when (volume.level) {
            ConfidenceLevel.HIGH -> BadgeGreen to stringResource(R.string.confidence_high)
            ConfidenceLevel.MEDIUM -> BadgeYellow to stringResource(R.string.confidence_medium)
            else -> BadgeRed to stringResource(R.string.confidence_low)
        }
        else -> BadgeRed to ""
    }
    val notLevel = stringResource(R.string.box_base_not_level)
    val main = when (volume) {
        is VolumeContent.Volume -> VolumeFormatter.format(volume.cubicMeters, units)
        is VolumeContent.Hint -> hintText(volume.reason)
        VolumeContent.Invalid -> stringResource(R.string.box_invalid)
    }
    val litres = (volume as? VolumeContent.Volume)?.takeIf { units == UnitSystem.METRIC && it.cubicMeters >= LITRE_M3 }
        ?.let { VolumeFormatter.litres(it.cubicMeters) }
    val description = when (volume) {
        is VolumeContent.Volume -> stringResource(
            R.string.spoken_volume_confidence, BoxLabel.speak(volume.cubicMeters, units) ?: main, badge,
        ) + if (volume.baseNotLevel) ". $notLevel" else ""
        else -> main
    }
    Column(
        modifier
            .layout { measurable, constraints ->
                val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(p.width, p.height) {
                    p.place((center.x - p.width / 2f).roundToInt(), (center.y - p.height / 2f).roundToInt())
                }
            }
            .background(Color(0xCC000000))
            .border(2.dp, colour)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .semantics {
                contentDescription = description
                liveRegion = LiveRegionMode.Polite
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val isNumber = volume is VolumeContent.Volume
        Text(main, color = if (isNumber) Color.White else BadgeRed, fontSize = if (isNumber) 22.sp else 14.sp, fontWeight = FontWeight.Bold)
        litres?.let { Text(it, color = Color.White, fontSize = 15.sp) }
        if (badge.isNotEmpty()) Text(badge, color = colour, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        if ((volume as? VolumeContent.Volume)?.baseNotLevel == true) Text(notLevel, color = BadgeYellow, fontSize = 13.sp)
    }
}

private const val LITRE_M3 = 0.001
