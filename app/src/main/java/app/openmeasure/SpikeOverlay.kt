package app.openmeasure

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.openmeasure.measure.coaching.CoachingState
import app.openmeasure.measure.hit.HitSource
import app.openmeasure.measure.units.SpokenLength
import app.openmeasure.settings.SettingsRepository
import com.google.ar.core.TrackingState

/** Text colours for the readout panel, tuned to clear 4.5:1 over the 80% black panel. */
private val ReadoutRed = Color(0xFFFF8A80)
private val ReadoutYellow = Color(0xFFFFFF00)

/** Camera overlay: crosshair, live tracking readout, and point A/B controls. */
@Composable
fun SpikeOverlay(controller: SpikeController, settingsRepository: SettingsRepository) {
    val s by controller.state.collectAsState()
    val settings = settingsRepository.settings
    val view = LocalView.current
    val ok = s.tracking == TrackingState.TRACKING && s.source != HitSource.NONE
    // Haptic only when point B lands (the measurement is finished) and Settings haptics is on.
    val bSet = s.pointB != null
    LaunchedEffect(bSet) {
        if (bSet && settings.hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
    val spokenDistance = s.distanceCm?.let { SpokenLength.speak(it / 100.0, settings.unitSystem) }
    val distanceDescription = if (spokenDistance != null) {
        stringResource(R.string.spoken_line, spokenDistance)
    } else {
        stringResource(R.string.spoken_no_distance)
    }
    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures { controller.tap(it.x, it.y) }
        },
    ) {
        val ready = s.coaching == CoachingState.READY
        if (ready) Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2)
            val col = Color.Green
            drawCircle(col, radius = 18f, center = c, style = Stroke(3f))
            drawCircle(col, radius = 3f, center = c)
        }
        if (!ready) CoachingHint(Modifier.align(Alignment.Center))
        Column(
            Modifier.align(Alignment.TopStart).padding(12.dp).background(Color(0xCC000000)).padding(8.dp),
        ) {
            Text("State: ${s.tracking}", color = Color.White)
            Text("Hit: ${s.source}  conf=${"%.2f".format(s.confidence)}", color = Color.White)
            Text("A=${if (s.pointA != null) "set" else "-"}  B=${if (s.pointB != null) "set" else "-"}", color = Color.White)
            val d = s.distanceCm
            if (d != null && s.tracking == TrackingState.TRACKING) {
                Text(
                    "Distance: ${"%.1f".format(d)} cm (${s.distanceSource}, conf ${"%.2f".format(s.distanceConfidence)})",
                    color = ReadoutYellow,
                    modifier = Modifier.semantics {
                        contentDescription = distanceDescription
                        liveRegion = LiveRegionMode.Polite
                    },
                )
            } else if (!ok) {
                Text("Tracking poor / no hit", color = ReadoutRed)
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(24.dp)) {
            Button(onClick = { controller.tap() }, enabled = ready) { Text(stringResource(R.string.spike_place_point)) }
            Button(onClick = { controller.reset() }) { Text(stringResource(R.string.spike_reset)) }
        }
    }
}
