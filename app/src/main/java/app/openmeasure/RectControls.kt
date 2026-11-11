package app.openmeasure

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import app.openmeasure.measure.rect.AreaContent
import app.openmeasure.measure.units.AreaFormatter
import app.openmeasure.measure.units.UnitSystem
import app.openmeasure.ui.HintBanner
import app.openmeasure.ui.OmText
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** "Couldn't detect — tap 4 corners", shown for a few seconds each time [failTick] increases. */
@Composable
fun DetectFailHint(failTick: Int, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(failTick) {
        if (failTick > 0) { visible = true; delay(HINT_MS); visible = false }
    }
    if (visible) HintBanner(stringResource(R.string.rect_detect_failed), modifier)
}

/** Area pill centred on the quad (number only when [AreaContent.Area]), with confidence and warnings. */
@Composable
fun RectAreaLabel(rect: RectUi, units: UnitSystem, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val center = rect.center ?: return
    val area = rect.area
    val place = modifier.layout { measurable, constraints ->
        val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(p.width, p.height) {
            p.place((center.x - p.width / 2f).roundToInt(), (center.y - p.height / 2f).roundToInt())
        }
    }
    if (area !is AreaContent.Area) {
        HintPill(if (area is AreaContent.Hint) hintText(area.reason) else stringResource(R.string.rect_invalid), place)
        return
    }
    val badge = confidenceText(area.level)
    val notes = buildList {
        if (!area.isRectangle) add(stringResource(R.string.rect_not_rectangular))
        if (area.notPlanar) add(stringResource(R.string.rect_not_planar))
    }
    val main = AreaFormatter.format(area.squareMeters, units)
    val description = stringResource(R.string.spoken_area_confidence, AreaFormatter.speak(area.squareMeters, units) ?: main, badge) +
        notes.joinToString("") { ". $it" }
    LabelPill(main, area.level, description, place, onClick, OmText.measureLarge, notes = notes)
}

private const val HINT_MS = 3000L
