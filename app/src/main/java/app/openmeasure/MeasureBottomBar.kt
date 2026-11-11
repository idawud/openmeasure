package app.openmeasure

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.openmeasure.ui.CircleIconButton
import app.openmeasure.ui.Dimens
import app.openmeasure.ui.OmColors
import app.openmeasure.ui.PillButton

/** Large white "+" shutter (72dp, thin outer ring); dimmed until READY, ticks haptically, shrinks slightly while pressed. */
@Composable
fun PlaceButton(description: String, enabled: Boolean, haptics: Boolean, onPlace: () -> Unit, modifier: Modifier = Modifier) {
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "placeScale")
    Box(
        modifier
            .size(Dimens.shutter)
            .scale(scale)
            .alpha(if (enabled) 1f else 0.4f)
            .border(BorderStroke(2.dp, Color.White), CircleShape)
            .padding(5.dp)
            .clip(CircleShape)
            .background(Color.White)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button) {
                if (haptics) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onPlace()
            }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppIcons.Plus, contentDescription = null, tint = OmColors.Black.copy(alpha = 0.85f), modifier = Modifier.size(36.dp))
    }
}

/** Everything the control cluster needs; built once in [MeasureOverlay]. */
internal class MeasureControls(
    val s: MeasureUiState,
    val controller: MeasureController,
    val ready: Boolean,
    val haptics: Boolean,
    val capture: CaptureAction,
    val saver: HistorySaver,
) {
    @Composable
    fun Place(modifier: Modifier = Modifier) {
        val ceilingStep = s.room?.step == RoomStep.CEILING
        val cornerMode = s.rectMode || (s.roomMode && s.room?.step == RoomStep.CORNERS)
        val desc = stringResource(
            if (ceilingStep) R.string.room_place_ceiling_desc else if (cornerMode) R.string.measure_place_corner_desc else R.string.measure_place_point_desc,
        )
        PlaceButton(desc, ready && s.canAdd, haptics, { controller.place() }, modifier)
    }

    @Composable
    fun Undo() {
        if (s.canUndo) CircleIconButton(AppIcons.Undo, stringResource(R.string.measure_undo_desc), { controller.undo() })
    }

    @Composable
    fun Detect() {
        if (s.rectMode) PillButton(
            stringResource(R.string.measure_detect), { controller.detect() }, enabled = ready,
            description = stringResource(R.string.measure_detect_desc),
        )
    }

    @Composable
    fun Capture() = CaptureButton(capture)

    @Composable
    fun Save() {
        if (s.save != null) PillButton(
            stringResource(R.string.history_save), saver::save, enabled = !saver.busy,
            description = stringResource(R.string.history_save_desc),
        )
    }
}

/** Portrait / wide layout: [undo, detect] | place | [capture, save], with the place button always exactly centred. */
@Composable
internal fun MeasureControlsRow(c: MeasureControls, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            c.Undo(); c.Detect()
        }
        c.Place(Modifier.padding(horizontal = 12.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.Start), verticalAlignment = Alignment.CenterVertically) {
            c.Capture(); c.Save()
        }
    }
}

/** Landscape / short windows: the same controls stacked on the right edge. */
@Composable
internal fun MeasureControlsColumn(c: MeasureControls, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        c.Save(); c.Capture(); c.Place(Modifier.padding(vertical = 4.dp)); c.Undo(); c.Detect()
    }
}
