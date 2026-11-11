package app.openmeasure

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.openmeasure.ar.ArCameraView
import app.openmeasure.measure.coaching.CoachingState
import app.openmeasure.measure.guide.GuideState
import app.openmeasure.measure.snap.SnapKind
import app.openmeasure.settings.SettingsRepository
import app.openmeasure.ui.Dimens
import app.openmeasure.ui.LocalTabBarHeight
import app.openmeasure.ui.OmColors
import app.openmeasure.ui.OmSnackbarHost
import app.openmeasure.ui.rememberReduceMotion

/** Camera overlay: reticle, placed points, lines / rectangle / angle / box / room with pill labels, and the iOS-style controls. */
@Composable
fun MeasureOverlay(controller: MeasureController, settingsRepository: SettingsRepository, cameraView: ArCameraView) {
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
    // F8: settings -> GL thread; haptic tick only on snap acquire.
    val snapRadiusPx = with(LocalDensity.current) { SNAP_RADIUS.toPx() }
    SideEffect {
        controller.snapEnabled = settings.snapEnabled
        controller.guideEnabled = settings.guideEnabled
        controller.rulerUnits = if (settings.rulerEnabled) settings.unitSystem else null
        controller.snapRadiusPx = snapRadiusPx
    }
    LaunchedEffect(s.snapTick) {
        if (s.snapTick > 0 && settings.hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
    LaunchedEffect(s.guideTick) {
        if (s.guideTick > 0 && settings.hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
    val layer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val snackbar = remember { SnackbarHostState() }
    // F15: the floor plan opens automatically when the room is done, as a snapshot (stable while tracking changes).
    var planOpen by rememberSaveable { mutableStateOf(false) }
    var planSnapshot by remember { mutableStateOf<RoomResult?>(null) }
    val roomDone = s.room?.step == RoomStep.DONE
    LaunchedEffect(roomDone) {
        if (roomDone) { planSnapshot = stateHolder.value.room?.result; planOpen = planSnapshot != null } else planOpen = false
    }
    // Tapping a label freezes the state it was built from into the detail sheet.
    var frozen by remember { mutableStateOf<MeasureUiState?>(null) }
    val openDetail: () -> Unit = { frozen = stateHolder.value }
    val capture = rememberCaptureAction(layer, cameraView, settings.hapticsEnabled, snackbar)
    val candidate = s.save
    val saver = rememberHistorySaver(snackbar, cameraView to layer) { uri -> candidate?.toItem(uri, System.currentTimeMillis()) }
    val controls = MeasureControls(s, controller, ready, settings.hapticsEnabled, capture, saver)
    val aligned = (s.snapKind != SnapKind.NONE && s.snapPoint != null) || (s.guide?.state ?: GuideState.NONE) != GuideState.NONE
    val reticleColor = animateColorAsState(if (aligned) OmColors.Accent else Color.White, tween(150), label = "reticleColor")
    val pulse: State<Float> = if (rememberReduceMotion()) remember { mutableStateOf(1f) } else {
        rememberInfiniteTransition(label = "reticle").animateFloat(
            1f, 1.07f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "reticlePulse",
        )
    }
    val units = settings.unitSystem

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val short = maxHeight < 420.dp
        val tabH = LocalTabBarHeight.current
        val below = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = Dimens.topRowHeight, start = Dimens.margin, end = Dimens.margin)
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
            ) { drawMeasureScene(s, ready, textMeasurer, reticleColor, pulse) }
            if (s.segments.isNotEmpty()) {
                // Last placed dot anchors a label when its segment is off screen (e.g. no hit yet).
                val fallback = s.dots.lastOrNull { it != null } ?: app.openmeasure.measure.projection.ScreenPoint(0f, 0f)
                MeasureLabelsLayer(s.segments, fallback, units, onLabelClick = openDetail)
            }
            s.angle?.let { AngleLabelView(it, onClick = openDetail) }
            s.rect?.let { RectAreaLabel(it, units, onClick = openDetail) }
            s.box?.let { BoxVolumeLabel(it, units, onClick = openDetail) }
            s.totalMeters?.let { PathTotalChip(it, units, below, openDetail) }
        }
        OmSnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 120.dp))
        MeasureTopBar(s, controller, Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.safeDrawing).padding(8.dp))
        s.box?.let { BoxStepHint(it.step, below) }
        s.room?.let { RoomStepHint(it, below) }
        s.room?.takeIf { it.preview != null }?.let {
            RoomMiniPlan(it, units, Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.safeDrawing).padding(start = 8.dp, top = 112.dp))
        }
        DetectFailHint(s.detectFailTick, below)
        s.guide?.takeIf { ready }?.let { GuideChip(it.state, Modifier.align(Alignment.Center).padding(top = 72.dp)) }
        if (ready || s.draggingId != null) SnapLabel(s.snapKind, s.snapPoint)
        if (!ready) CoachingHint(Modifier.align(Alignment.Center))

        // Modes (only while nothing is placed) and room actions sit above the controls; short windows
        // (landscape / split screen) move the controls to the right edge so nothing overlaps.
        val above: @Composable () -> Unit = {
            s.room?.let { RoomActions(it, controller, onViewPlan = { planSnapshot = it.result; planOpen = true }) }
            if (s.pointCount == 0) ModePicker(MeasureMode.of(s), controller)
        }
        if (short) {
            Column(
                Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(start = Dimens.margin, end = 96.dp, bottom = tabH + 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { above() }
            MeasureControlsColumn(
                controls,
                Modifier.align(Alignment.CenterEnd).windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = Dimens.topRowHeight, end = 8.dp).verticalScroll(rememberScrollState()),
            )
        } else {
            Column(
                Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(start = Dimens.margin, end = Dimens.margin, bottom = tabH + 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
            ) {
                above()
                MeasureControlsRow(controls)
            }
        }
        frozen?.let { snap ->
            val segmentName = stringResource(R.string.detail_segment)
            val model = remember(snap, units) { buildDetail(snap, units) { String.format(java.util.Locale.getDefault(), segmentName, it) } }
            val sheetCandidate = snap.save
            val sheetSaver = rememberHistorySaver(snackbar, cameraView to layer) { uri -> sheetCandidate?.toItem(uri, System.currentTimeMillis()) }
            if (model != null) MeasureDetailSheet(model, sheetCandidate != null, sheetSaver, capture, snackbar) { frozen = null }
            else LaunchedEffect(snap) { frozen = null }
        }
        planSnapshot?.takeIf { planOpen }?.let { RoomPlanScreen(it, units) { planOpen = false } }
    }
}

/** Max on-screen distance a point may be pulled by snapping. */
private val SNAP_RADIUS = 24.dp
