package app.openmeasure

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import app.openmeasure.data.HistoryItem
import app.openmeasure.data.MeasurementKind
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.openmeasure.ar.ArCameraView
import app.openmeasure.ar.ArSessionManager
import app.openmeasure.ar.PoseHeightService
import app.openmeasure.measure.confidence.ConfidenceLevel
import app.openmeasure.measure.height.HeightReason
import app.openmeasure.measure.height.SmoothedHeight
import app.openmeasure.measure.units.LengthFormatter
import app.openmeasure.measure.units.SpokenLength
import app.openmeasure.measure.units.UnitSystem
import app.openmeasure.settings.SettingsRepository

/** F10 person height: guidance, skeleton, live smoothed readout with badge, and Capture to hold a stable value. */
@Composable
fun HeightOverlay(cameraView: ArCameraView, manager: ArSessionManager, settingsRepository: SettingsRepository) {
    val context = LocalContext.current
    val service = remember { PoseHeightService(context.applicationContext) }
    DisposableEffect(service) {
        cameraView.onFrame = { frame ->
            manager.session?.let { service.onFrame(frame, it, cameraView.display?.rotation ?: 0) }
        }
        onDispose { cameraView.onFrame = null; service.close() }
    }
    val state by service.state.collectAsState()
    val units = settingsRepository.settings.unitSystem
    var captured by rememberSaveable { mutableStateOf<Float?>(null) }
    var capturedLevel by rememberSaveable { mutableStateOf(ConfidenceLevel.LOW) }
    val snackbar = remember { SnackbarHostState() }
    val s = state.smoothed

    Box(Modifier.fillMaxSize()) {
        SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 120.dp))
        val bones = state.skeletonView
        Canvas(Modifier.fillMaxSize()) {
            for (i in 0 until bones.size - 3 step 4) {
                drawLine(Color(0xCCFFFFFF), Offset(bones[i], bones[i + 1]), Offset(bones[i + 2], bones[i + 3]), strokeWidth = 4f)
            }
        }
        Column(
            Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.height_guidance),
                Modifier.background(Color(0x99000000)).padding(horizontal = 10.dp, vertical = 4.dp),
                color = Color.White, fontSize = 14.sp,
            )
            HeightReadout(s, units, Modifier.padding(top = 12.dp))
        }
        Column(
            Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            captured?.let { h ->
                val text = LengthFormatter.format(h.toDouble(), units)
                val spoken = stringResource(R.string.spoken_height_captured, SpokenLength.speak(h.toDouble(), units) ?: text)
                Text(
                    stringResource(R.string.height_captured, text),
                    Modifier.background(Color(0xCC000000)).border(2.dp, BadgeGreen).padding(horizontal = 10.dp, vertical = 6.dp)
                        .semantics { contentDescription = spoken },
                    color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                )
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val canCapture = s.stable && s.heightMeters != null
                Button(onClick = { captured = s.heightMeters; capturedLevel = s.confidenceLevel }, enabled = canCapture) {
                    Text(stringResource(R.string.height_capture))
                }
                captured?.let { h ->
                    HistorySaveButton(snackbar) {
                        HistoryItem(
                            createdAtMillis = System.currentTimeMillis(), kind = MeasurementKind.HEIGHT, heightMeters = h.toDouble(),
                            confidenceLevel = capturedLevel.name,
                        )
                    }
                }
                if (captured != null) OutlinedButton(onClick = { captured = null; service.reset() }) {
                    Text(stringResource(R.string.height_clear))
                }
            }
        }
    }
}

@Composable
private fun HeightReadout(s: SmoothedHeight, units: UnitSystem, modifier: Modifier = Modifier) {
    val h = s.heightMeters
    val (colour, badge) = when (s.confidenceLevel) {
        ConfidenceLevel.HIGH -> BadgeGreen to stringResource(R.string.confidence_high)
        ConfidenceLevel.MEDIUM -> BadgeYellow to stringResource(R.string.confidence_medium)
        else -> BadgeRed to stringResource(R.string.confidence_low)
    }
    val hint = when {
        h != null && !s.stable -> stringResource(R.string.height_hold_still)
        h != null -> ""
        s.isCollecting -> stringResource(R.string.height_measuring)
        else -> heightHint(s.reason)
    }
    val description = if (h != null) {
        val text = LengthFormatter.format(h.toDouble(), units)
        stringResource(R.string.spoken_height, SpokenLength.speak(h.toDouble(), units) ?: text, badge)
    } else hint
    Column(
        modifier.background(Color(0xCC000000)).border(2.dp, if (h != null) colour else BadgeRed)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics { contentDescription = description; liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (h != null) {
            Text(LengthFormatter.format(h.toDouble(), units), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(badge, color = colour, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        if (hint.isNotEmpty()) Text(hint, color = if (h != null) Color.White else BadgeRed, fontSize = 14.sp)
    }
}

@Composable
private fun heightHint(reason: HeightReason): String = stringResource(
    when (reason) {
        HeightReason.TRACKING_POOR -> R.string.hint_tracking_poor
        HeightReason.NO_PERSON -> R.string.height_hint_no_person
        HeightReason.PARTIAL_BODY -> R.string.height_hint_partial
        HeightReason.LOW_DEPTH_CONFIDENCE -> R.string.height_hint_low_depth
        HeightReason.TOO_CLOSE -> R.string.height_hint_too_close
        HeightReason.TOO_FAR -> R.string.height_hint_too_far
        HeightReason.NOT_UPRIGHT -> R.string.height_hint_not_upright
        HeightReason.NO_FLOOR -> R.string.height_hint_no_floor
        HeightReason.OK -> R.string.height_measuring
    },
)
