package app.openmeasure

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.platform.LocalDensity
import app.openmeasure.measure.projection.PointPicker
import kotlinx.coroutines.withTimeoutOrNull

/** Grab radius around a projected point (dp). */
private val PICK_RADIUS: Dp = 32.dp

/** The dragged point is shown this far above the finger so it stays visible (dp). */
private val FINGER_LIFT: Dp = 56.dp

/**
 * Touch handling for editing points. Only touches that start within [PICK_RADIUS] of a point are consumed, so the
 * rest of the screen is untouched. Press-and-hold without moving removes the point ([onLongPress]); moving drags it.
 */
@Composable
fun Modifier.pointDragInput(
    state: State<MeasureUiState>,
    controller: MeasureController,
    onLongPress: () -> Unit,
): Modifier {
    val density = LocalDensity.current
    val radius = with(density) { PICK_RADIUS.toPx() }
    val lift = with(density) { FINGER_LIFT.toPx() }
    val config = LocalViewConfiguration.current
    return pointerInput(controller) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val s = state.value
            val index = PointPicker.pick(s.dots, down.position.x, down.position.y, radius) ?: return@awaitEachGesture
            val id = s.ids[index]
            down.consume()

            // Hold still -> long press (remove); move past slop -> drag; lift early -> nothing.
            var moved = false
            val timedOut = withTimeoutOrNull(config.longPressTimeoutMillis) {
                while (true) {
                    val e: PointerEvent = awaitPointerEvent(PointerEventPass.Initial)
                    val c: PointerInputChange = e.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull
                    c.consume()
                    if (!c.pressed) return@withTimeoutOrNull
                    if ((c.position - down.position).getDistance() > config.touchSlop) {
                        moved = true
                        return@withTimeoutOrNull
                    }
                }
            } == null
            if (timedOut && !moved) {
                onLongPress()
                controller.remove(id)
                // Swallow the rest of this touch.
                while (true) {
                    val e = awaitPointerEvent(PointerEventPass.Initial)
                    e.changes.forEach { it.consume() }
                    if (e.changes.none { it.pressed }) break
                }
                return@awaitEachGesture
            }
            if (!moved) return@awaitEachGesture

            controller.beginDrag(id, down.position.x, down.position.y - lift)
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                val c = e.changes.firstOrNull { it.id == down.id } ?: break
                c.consume()
                if (!c.pressed) break
                if (c.positionChange() != androidx.compose.ui.geometry.Offset.Zero) {
                    controller.dragTo(c.position.x, c.position.y - lift)
                }
            }
            controller.endDrag()
        }
    }
}
