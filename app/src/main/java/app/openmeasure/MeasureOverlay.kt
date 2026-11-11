package app.openmeasure

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
fun MeasureOverlay(controller: MeasureController, settingsRepository: SettingsRepository) {
    val s by controller.state.collectAsState()
    val settings = settingsRepository.settings
    val view = LocalView.current
    val ready = s.coaching == CoachingState.READY
    // Haptic only when the end point lands (measurement finished) and Settings haptics is on.
    val complete = s.pointCount >= 2
    LaunchedEffect(complete) {
        if (complete && settings.hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val line = Color(0xFFFFEB3B)
            s.segment?.let {
                drawLine(
                    Color.Black, Offset(it.a.x, it.a.y), Offset(it.b.x, it.b.y), strokeWidth = 7.dp.toPx(),
                )
                drawLine(
                    line, Offset(it.a.x, it.a.y), Offset(it.b.x, it.b.y), strokeWidth = 3.dp.toPx(),
                    pathEffect = if (s.live) PathEffect.dashPathEffect(floatArrayOf(18f, 12f)) else null,
                )
            }
            s.dots.filterNotNull().forEach {
                drawCircle(Color.Black, 9.dp.toPx(), Offset(it.x, it.y))
                drawCircle(Color.White, 6.dp.toPx(), Offset(it.x, it.y))
            }
            if (ready) {
                val c = Offset(size.width / 2, size.height / 2)
                drawCircle(Color.Green, radius = 18f, center = c, style = Stroke(3f))
                drawCircle(Color.Green, radius = 3f, center = c)
            }
        }
        s.label?.let { label ->
            // First placed dot is the label anchor when no segment is on screen (e.g. no hit yet).
            val fallback = s.dots.firstNotNullOfOrNull { it }
                ?: app.openmeasure.measure.projection.ScreenPoint(0f, 0f)
            MeasureLabelView(label, s.segment, fallback, settings.unitSystem)
        }
        if (!ready) CoachingHint(Modifier.align(Alignment.Center))
        Column(
            Modifier.align(Alignment.BottomCenter).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val placeDesc = stringResource(R.string.measure_place_point_desc)
            Button(
                onClick = { controller.place() },
                enabled = ready,
                modifier = Modifier.semantics { contentDescription = placeDesc },
            ) { Text(stringResource(R.string.measure_place_point)) }
            Button(onClick = { controller.reset() }) { Text(stringResource(R.string.measure_reset)) }
        }
    }
}
