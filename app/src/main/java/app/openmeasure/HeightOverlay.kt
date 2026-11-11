package app.openmeasure

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.openmeasure.ar.ArCameraView
import app.openmeasure.ar.ArSessionManager
import app.openmeasure.ar.PoseHeightService
import app.openmeasure.data.HistoryItem
import app.openmeasure.data.MeasurementKind
import app.openmeasure.measure.confidence.ConfidenceLevel
import app.openmeasure.measure.height.HeightReason
import app.openmeasure.measure.height.SmoothedHeight
import app.openmeasure.measure.units.LengthFormatter
import app.openmeasure.measure.units.SpokenLength
import app.openmeasure.measure.units.UnitSystem
import app.openmeasure.settings.SettingsRepository
import app.openmeasure.ui.Dimens
import app.openmeasure.ui.HintBanner
import app.openmeasure.ui.LocalTabBarHeight
import app.openmeasure.ui.OmColors
import app.openmeasure.ui.OmSnackbarHost
import app.openmeasure.ui.OmText
import app.openmeasure.ui.PillButton

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

    val tabH = LocalTabBarHeight.current
    val captureValue = captured
    val saver = rememberHistorySaver(snackbar) {
        captureValue?.let {
            HistoryItem(
                createdAtMillis = System.currentTimeMillis(), kind = MeasurementKind.HEIGHT, heightMeters = it.toDouble(),
                confidenceLevel = capturedLevel.name,
            )
        }
    }
    Box(Modifier.fillMaxSize()) {
        OmSnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 180.dp))
        val bones = state.skeletonView
        Canvas(Modifier.fillMaxSize()) {
            for (i in 0 until bones.size - 3 step 4) {
                val a = Offset(bones[i], bones[i + 1])
                val b = Offset(bones[i + 2], bones[i + 3])
                drawLine(OmColors.LineOutline, a, b, strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
                drawLine(Color.White, a, b, strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
            }
        }
        Column(
            Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = Dimens.topRowHeight, start = Dimens.margin, end = Dimens.margin),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HintBanner(stringResource(R.string.height_guidance))
            HeightReadout(s, units)
        }
        Column(
            Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = tabH + 8.dp, start = Dimens.margin, end = Dimens.margin),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            captured?.let { h ->
                val text = LengthFormatter.format(h.toDouble(), units)
                val spoken = stringResource(R.string.spoken_height_captured, SpokenLength.speak(h.toDouble(), units) ?: text)
                LabelPill(
                    main = text, level = capturedLevel, description = spoken, mainStyle = OmText.measureLarge,
                    secondary = stringResource(R.string.height_captured_caption),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val canCapture = s.stable && s.heightMeters != null
                PillButton(
                    stringResource(R.string.height_capture), { captured = s.heightMeters; capturedLevel = s.confidenceLevel },
                    enabled = canCapture, filled = true,
                )
                if (captured != null) {
                    PillButton(
                        stringResource(R.string.history_save), saver::save, enabled = !saver.busy,
                        description = stringResource(R.string.history_save_desc),
                    )
                    PillButton(stringResource(R.string.height_clear), { captured = null; service.reset() })
                }
            }
        }
    }
}

@Composable
private fun HeightReadout(s: SmoothedHeight, units: UnitSystem, modifier: Modifier = Modifier) {
    val h = s.heightMeters
    val badge = confidenceText(s.confidenceLevel)
    val hint = when {
        h != null && !s.stable -> stringResource(R.string.height_hold_still)
        h != null -> ""
        s.isCollecting -> stringResource(R.string.height_measuring)
        else -> heightHint(s.reason)
    }
    if (h == null) {
        // No number may be shown: hint only.
        if (hint.isNotEmpty()) HintPill(hint, modifier)
        return
    }
    val text = LengthFormatter.format(h.toDouble(), units)
    val description = stringResource(R.string.spoken_height, SpokenLength.speak(h.toDouble(), units) ?: text, badge)
    LabelPill(
        main = text, level = s.confidenceLevel, description = description, modifier = modifier,
        mainStyle = OmText.sheetValue, notes = if (hint.isNotEmpty()) listOf(hint) else emptyList(),
    )
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
