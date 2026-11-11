package app.openmeasure

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.openmeasure.measure.coaching.CoachingState
import app.openmeasure.settings.SettingsRepository

/** Camera overlay: crosshair, placed points, line, live label, "+" and reset buttons. */
@Composable
fun MeasureOverlay(controller: MeasureController, settingsRepository: SettingsRepository, cameraView: app.openmeasure.ar.ArCameraView) {
    val stateHolder = controller.state.collectAsState()
    val s by stateHolder
    val settings = settingsRepository.settings
    val view = LocalView.current
    val ready = s.coaching == CoachingState.READY
    // Haptic only when the end point lands (measurement finished) and Settings haptics is on.
    val placedPoints = s.pointCount
    LaunchedEffect(placedPoints) {
        if (placedPoints >= 2 && settings.hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
    val layer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
            },
        ) {
        Canvas(
            Modifier.fillMaxSize().pointDragInput(stateHolder, controller) {
                if (settings.hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            },
        ) {
            val line = Color(0xFFFFEB3B)
            s.segments.forEach { su ->
                val it = su.segment ?: return@forEach
                drawLine(
                    Color.Black, Offset(it.a.x, it.a.y), Offset(it.b.x, it.b.y), strokeWidth = 7.dp.toPx(),
                )
                drawLine(
                    line, Offset(it.a.x, it.a.y), Offset(it.b.x, it.b.y), strokeWidth = 3.dp.toPx(),
                    pathEffect = if (su.live) PathEffect.dashPathEffect(floatArrayOf(18f, 12f)) else null,
                )
            }
            s.dots.forEachIndexed { i, d ->
                if (d == null) return@forEachIndexed
                val grown = if (s.ids.getOrNull(i) == s.draggingId) 4.dp.toPx() else 0f
                drawCircle(Color.Black, 9.dp.toPx() + grown, Offset(d.x, d.y))
                drawCircle(Color.White, 6.dp.toPx() + grown, Offset(d.x, d.y))
            }
            if (ready) {
                val c = Offset(size.width / 2, size.height / 2)
                drawCircle(Color.Green, radius = 18f, center = c, style = Stroke(3f))
                drawCircle(Color.Green, radius = 3f, center = c)
            }
        }
        if (s.segments.isNotEmpty()) {
            // Last placed dot anchors a label when its segment is off screen (e.g. no hit yet).
            val fallback = s.dots.lastOrNull { it != null }
                ?: app.openmeasure.measure.projection.ScreenPoint(0f, 0f)
            MeasureLabelsLayer(s.segments, fallback, settings.unitSystem)
        }
        s.totalMeters?.let {
            PathTotalChip(it, settings.unitSystem, Modifier.align(Alignment.TopCenter).padding(top = 24.dp))
        }
        }
        if (!ready) CoachingHint(Modifier.align(Alignment.Center))
        CaptureButton(layer, cameraView, settings.hapticsEnabled, Modifier.align(Alignment.BottomEnd).padding(24.dp))
        Column(
            Modifier.align(Alignment.BottomCenter).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val placeDesc = stringResource(R.string.measure_place_point_desc)
            Button(
                onClick = { controller.place() },
                enabled = ready && s.canAdd,
                modifier = Modifier.semantics { contentDescription = placeDesc },
            ) { Text(stringResource(R.string.measure_place_point)) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val undoDesc = stringResource(R.string.measure_undo_desc)
                Button(
                    onClick = { controller.undo() },
                    enabled = s.canUndo,
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = undoDesc },
                ) { Text(stringResource(R.string.measure_undo)) }
                if (s.pointCount > 0) {
                    // "Done" ends the current path; afterwards the same button reads "New" (next point starts fresh).
                    Button(
                        onClick = { if (s.finished) controller.reset() else controller.done() },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(if (s.finished) R.string.measure_new else R.string.measure_done))
                    }
                }
                Button(onClick = { controller.reset() }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.measure_reset))
                }
            }
        }
    }
}
