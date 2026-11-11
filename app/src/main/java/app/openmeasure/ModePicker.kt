package app.openmeasure

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.openmeasure.ui.Dimens
import app.openmeasure.ui.OmColors
import app.openmeasure.ui.OmShapes

/** Measuring modes reachable from the picker. */
enum class MeasureMode(val labelRes: Int, val icon: ImageVector) {
    LINE(R.string.measure_mode_line, AppIcons.Line),
    RECT(R.string.measure_mode_rect, AppIcons.Rectangle),
    ANGLE(R.string.measure_mode_angle, AppIcons.Angle),
    BOX(R.string.measure_mode_box, AppIcons.Box),
    ROOM(R.string.measure_mode_room, AppIcons.Room);

    fun select(c: MeasureController) = when (this) {
        LINE -> c.setRectMode(false)
        RECT -> c.setRectMode(true)
        ANGLE -> c.setAngleMode(true)
        BOX -> c.setBoxMode(true)
        ROOM -> c.setRoomMode(true)
    }

    companion object {
        fun of(s: MeasureUiState) = when {
            s.rectMode -> RECT
            s.angleMode -> ANGLE
            s.boxMode -> BOX
            s.roomMode -> ROOM
            else -> LINE
        }
    }
}

/** Horizontal row of icon + label chips; the active mode is yellow. Scrolls on narrow windows. */
@Composable
fun ModePicker(current: MeasureMode, controller: MeasureController, modifier: Modifier = Modifier) {
    val modeDesc = stringResource(R.string.measure_mode_desc)
    Row(
        modifier.horizontalScroll(rememberScrollState()).semantics { contentDescription = modeDesc },
        horizontalArrangement = Arrangement.spacedBy(Dimens.gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MeasureMode.entries.forEach { mode ->
            val selected = mode == current
            val fg = if (selected) OmColors.Black else Color.White
            Row(
                Modifier
                    .minimumInteractiveComponentSize()
                    .height(Dimens.pillHeight)
                    .clip(OmShapes.pill)
                    .background(if (selected) OmColors.Accent else OmColors.OverlayLabel)
                    .selectable(selected = selected, role = Role.RadioButton) { mode.select(controller) }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(mode.icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
                Text(stringResource(mode.labelRes), color = fg, maxLines = 1, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
