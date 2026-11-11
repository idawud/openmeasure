package app.openmeasure

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.openmeasure.measure.rect.AreaContent
import app.openmeasure.measure.room.RoomPlan
import app.openmeasure.measure.units.AreaFormatter
import app.openmeasure.measure.units.UnitSystem

/** Dashed closing wall (last corner or reticle back to the first corner) while scanning. */
internal fun DrawScope.drawRoomEdges(room: RoomUi) {
    room.edges.forEach {
        drawLine(Color.Black, Offset(it.a.x, it.a.y), Offset(it.b.x, it.b.y), strokeWidth = 4.dp.toPx())
        drawLine(
            Color.White, Offset(it.a.x, it.a.y), Offset(it.b.x, it.b.y), strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
        )
    }
}

/** Current step instruction (polite live region so TalkBack reads each new step). */
@Composable
fun RoomStepHint(room: RoomUi, modifier: Modifier = Modifier) {
    val text = stringResource(
        when (room.step) {
            RoomStep.CORNERS -> when {
                room.nearFirst -> R.string.room_step_near_first
                room.canClose -> R.string.room_step_close
                else -> R.string.room_step_corners
            }
            RoomStep.CEILING -> R.string.room_step_ceiling
            RoomStep.DONE -> R.string.room_step_done
        },
    )
    Text(
        text,
        modifier.background(Color(0xCC000000)).padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = Color.White, fontSize = 15.sp,
    )
}

/** Step actions: Close room (>= 3 corners), Skip ceiling, View plan. */
@Composable
fun RoomActions(room: RoomUi, controller: MeasureController, onViewPlan: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (room.step) {
            RoomStep.CORNERS -> {
                val desc = stringResource(R.string.room_close_desc)
                Button(
                    onClick = { controller.closeRoom() }, enabled = room.canClose,
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = desc },
                ) { Text(stringResource(R.string.room_close)) }
            }
            RoomStep.CEILING -> Button(onClick = { controller.skipCeiling() }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.room_skip_ceiling))
            }
            RoomStep.DONE -> Button(onClick = onViewPlan, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.room_view_plan))
            }
        }
    }
}

/** Collapsible live 2D preview in a screen corner; the toggle is a 48dp target. */
@Composable
fun RoomMiniPlan(room: RoomUi, units: UnitSystem, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(true) }
    val plan = room.preview
    Column(modifier.background(Color(0x99000000)), horizontalAlignment = Alignment.End) {
        TextButton(onClick = { open = !open }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
            Text(stringResource(if (open) R.string.room_preview_hide else R.string.room_preview_show), color = Color.White)
        }
        if (open && plan != null) {
            val summary = roomSummary(plan, room.result?.labels?.area, units)
            PlanCanvas(
                plan, units,
                Modifier.size(width = 160.dp, height = 130.dp).padding(6.dp).semantics { contentDescription = summary },
                showDims = false, openLast = room.step == RoomStep.CORNERS,
            )
        }
    }
}

/**
 * TalkBack summary, e.g. "Room, 4 walls, area 14.2 square metres". The area is spoken only when [area] is a
 * displayable number (or, for saved history entries, when [area] is null and [trustPlan] is true).
 */
@Composable
fun roomSummary(plan: RoomPlan, area: AreaContent?, units: UnitSystem, trustPlan: Boolean = false): String {
    val m2 = (area as? AreaContent.Area)?.squareMeters ?: plan.areaM2.takeIf { trustPlan && area == null && plan.isValid }
    val spoken = m2?.let { AreaFormatter.speak(it, units) }
    val walls = if (plan.corners.size >= 3) plan.corners.size else plan.wallLengths.size
    val wallText = pluralStringResource(R.plurals.room_walls, walls, walls)
    return if (spoken != null) stringResource(R.string.room_summary, wallText, spoken) else stringResource(R.string.room_summary_no_area, wallText)
}
