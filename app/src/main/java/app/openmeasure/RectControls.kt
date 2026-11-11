package app.openmeasure

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.openmeasure.measure.confidence.ConfidenceLevel
import app.openmeasure.measure.rect.AreaContent
import app.openmeasure.measure.units.AreaFormatter
import app.openmeasure.measure.units.UnitSystem
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** F9/F14/F15/F16: Line / Rectangle / Angle / Box / Room mode chips, plus "Detect" (auto-detect at the reticle) in rectangle mode. */
@Composable
fun RectModeBar(rectMode: Boolean, angleMode: Boolean, boxMode: Boolean, roomMode: Boolean, ready: Boolean, controller: MeasureController, modifier: Modifier = Modifier) {
    val modeDesc = stringResource(R.string.measure_mode_desc)
    Row(
        // Scrolls horizontally when four chips (+ Detect) do not fit a narrow window.
        modifier.horizontalScroll(rememberScrollState()).semantics { contentDescription = modeDesc },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = !rectMode && !angleMode && !boxMode && !roomMode, onClick = { controller.setRectMode(false) },
            label = { Text(stringResource(R.string.measure_mode_line)) }, modifier = Modifier.heightIn(min = 48.dp),
        )
        FilterChip(
            selected = rectMode, onClick = { controller.setRectMode(true) },
            label = { Text(stringResource(R.string.measure_mode_rect)) }, modifier = Modifier.heightIn(min = 48.dp),
        )
        FilterChip(
            selected = angleMode, onClick = { controller.setAngleMode(true) },
            label = { Text(stringResource(R.string.measure_mode_angle)) }, modifier = Modifier.heightIn(min = 48.dp),
        )
        FilterChip(
            selected = boxMode, onClick = { controller.setBoxMode(true) },
            label = { Text(stringResource(R.string.measure_mode_box)) }, modifier = Modifier.heightIn(min = 48.dp),
        )
        FilterChip(
            selected = roomMode, onClick = { controller.setRoomMode(true) },
            label = { Text(stringResource(R.string.measure_mode_room)) }, modifier = Modifier.heightIn(min = 48.dp),
        )
        if (rectMode) {
            val detectDesc = stringResource(R.string.measure_detect_desc)
            Button(
                onClick = { controller.detect() }, enabled = ready,
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = detectDesc },
            ) { Text(stringResource(R.string.measure_detect)) }
        }
    }
}

/** "Couldn't detect — tap 4 corners", shown for a few seconds each time [failTick] increases. */
@Composable
fun DetectFailHint(failTick: Int, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(failTick) {
        if (failTick > 0) { visible = true; delay(HINT_MS); visible = false }
    }
    if (!visible) return
    Text(
        stringResource(R.string.rect_detect_failed),
        modifier.background(Color(0xCC000000)).padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = Color.White, fontSize = 16.sp,
    )
}

/** Area label centred on the quad (number only when [AreaContent.Area]), with text badge and warnings. */
@Composable
fun RectAreaLabel(rect: RectUi, units: UnitSystem, modifier: Modifier = Modifier) {
    val center = rect.center ?: return
    val area = rect.area
    val (colour, badge) = when (area) {
        is AreaContent.Area -> when (area.level) {
            ConfidenceLevel.HIGH -> BadgeGreen to stringResource(R.string.confidence_high)
            ConfidenceLevel.MEDIUM -> BadgeYellow to stringResource(R.string.confidence_medium)
            else -> BadgeRed to stringResource(R.string.confidence_low)
        }
        else -> BadgeRed to ""
    }
    val notes = buildList {
        if (area is AreaContent.Area && !area.isRectangle) add(stringResource(R.string.rect_not_rectangular))
        if (area is AreaContent.Area && area.notPlanar) add(stringResource(R.string.rect_not_planar))
    }
    val main = when (area) {
        is AreaContent.Area -> AreaFormatter.format(area.squareMeters, units)
        is AreaContent.Hint -> hintText(area.reason)
        AreaContent.Invalid -> stringResource(R.string.rect_invalid)
    }
    val description = when (area) {
        is AreaContent.Area -> stringResource(
            R.string.spoken_area_confidence, AreaFormatter.speak(area.squareMeters, units) ?: main, badge,
        )
        else -> main
    } + notes.joinToString("") { ". $it" }
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
        val isNumber = area is AreaContent.Area
        Text(main, color = if (isNumber) Color.White else BadgeRed, fontSize = if (isNumber) 22.sp else 14.sp, fontWeight = FontWeight.Bold)
        if (badge.isNotEmpty()) Text(badge, color = colour, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        notes.forEach { Text(it, color = BadgeYellow, fontSize = 13.sp) }
    }
}

private const val HINT_MS = 3000L
