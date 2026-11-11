package app.openmeasure

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.openmeasure.measure.coaching.CoachingState
import app.openmeasure.measure.snap.SnapKind
import app.openmeasure.settings.SettingsRepository

/** Camera overlay: crosshair, placed points, lines or rectangle (F9) with labels, mode bar, "+" and reset buttons. */
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
    BoxWithConstraints(Modifier.fillMaxSize()) {
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
            s.guide?.let { drawGuides(it) }
            s.box?.let { drawBoxEdges(it) }
            s.room?.let { drawRoomEdges(it) }
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
            s.angle?.let { drawAngleArc(it) }
            drawRuler(s.segments, textMeasurer)
            s.dots.forEachIndexed { i, d ->
                if (d == null) return@forEachIndexed
                val grown = if (s.ids.getOrNull(i) == s.draggingId) 4.dp.toPx() else 0f
                drawCircle(Color.Black, 9.dp.toPx() + grown, Offset(d.x, d.y))
                drawCircle(Color.White, 6.dp.toPx() + grown, Offset(d.x, d.y))
            }
            val snap = s.snapPoint?.takeIf { s.snapKind != SnapKind.NONE }
            if (ready) {
                val c = Offset(size.width / 2, size.height / 2)
                if (snap != null && s.draggingId == null) {
                    // Snapped: small crosshair stays at the aim point, the marker shows where the point goes.
                    drawLine(Color.White, c, Offset(snap.x, snap.y), strokeWidth = 1.5.dp.toPx())
                    drawCircle(Color.White, radius = 3f, center = c)
                } else {
                    drawCircle(Color.Green, radius = 18f, center = c, style = Stroke(3f))
                    drawCircle(Color.Green, radius = 3f, center = c)
                }
            }
            if (snap != null) drawSnapMarker(s.snapKind, snap)
        }
        if (s.segments.isNotEmpty()) {
            // Last placed dot anchors a label when its segment is off screen (e.g. no hit yet).
            val fallback = s.dots.lastOrNull { it != null }
                ?: app.openmeasure.measure.projection.ScreenPoint(0f, 0f)
            MeasureLabelsLayer(s.segments, fallback, settings.unitSystem)
        }
        s.angle?.let { AngleLabelView(it) }
        s.rect?.let { RectAreaLabel(it, settings.unitSystem) }
        s.box?.let { BoxVolumeLabel(it, settings.unitSystem) }
        s.totalMeters?.let {
            PathTotalChip(it, settings.unitSystem, Modifier.align(Alignment.TopCenter).padding(top = 24.dp))
        }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 72.dp))
        s.box?.let { BoxStepHint(it.step, Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 24.dp, start = 16.dp, end = 16.dp)) }
        s.room?.let { RoomStepHint(it, Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 24.dp, start = 16.dp, end = 16.dp)) }
        s.room?.takeIf { it.preview != null }?.let {
            RoomMiniPlan(it, settings.unitSystem, Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.safeDrawing).padding(start = 8.dp, top = 96.dp))
        }
        DetectFailHint(s.detectFailTick, Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 24.dp))
        s.guide?.takeIf { ready }?.let {
            GuideChip(it.state, Modifier.align(Alignment.Center).padding(top = 72.dp))
        }
        if (ready || s.draggingId != null) SnapLabel(s.snapKind, s.snapPoint)
        if (!ready) CoachingHint(Modifier.align(Alignment.Center))
        // Narrow or short windows (split screen, landscape): capture moves to the top-end corner and
        // the place button joins the action row so controls never overlap.
        val narrow = maxWidth < 420.dp
        val short = maxHeight < 420.dp
        val insets = WindowInsets.safeDrawing.union(WindowInsets.displayCutout)
        CaptureButton(
            layer, cameraView, settings.hapticsEnabled,
            Modifier.align(if (narrow || short) Alignment.TopEnd else Alignment.BottomEnd)
                .windowInsetsPadding(insets).padding(if (narrow || short) 8.dp else 24.dp),
        )
        Column(
            Modifier.align(Alignment.BottomCenter).windowInsetsPadding(insets).padding(if (short) 8.dp else 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            s.save?.let { cand ->
                HistorySaveButton(
                    snackbar, Modifier.padding(bottom = if (short) 4.dp else 8.dp), photo = cameraView to layer,
                ) { uri -> cand.toItem(uri, System.currentTimeMillis()) }
            }
            s.room?.let { RoomActions(it, controller, onViewPlan = { planSnapshot = it.result; planOpen = true }, Modifier.padding(bottom = 8.dp)) }
            RectModeBar(s.rectMode, s.angleMode, s.boxMode, s.roomMode, ready, controller, Modifier.padding(bottom = if (short) 4.dp else 8.dp))
            val ceilingStep = s.room?.step == RoomStep.CEILING
            val cornerMode = s.rectMode || (s.roomMode && s.room?.step == RoomStep.CORNERS)
            val placeDesc = stringResource(
                if (ceilingStep) R.string.room_place_ceiling_desc else if (cornerMode) R.string.measure_place_corner_desc else R.string.measure_place_point_desc,
            )
            val placeButton: @Composable () -> Unit = {
                Button(
                    onClick = { controller.place() },
                    enabled = ready && s.canAdd,
                    modifier = Modifier.semantics { contentDescription = placeDesc },
                ) { Text(stringResource(if (ceilingStep) R.string.room_place_ceiling else if (cornerMode) R.string.measure_place_corner else R.string.measure_place_point)) }
            }
            if (!short) placeButton()
            Row(horizontalArrangement = Arrangement.spacedBy(if (narrow) 6.dp else 12.dp)) {
                if (short) placeButton()
                val undoDesc = stringResource(R.string.measure_undo_desc)
                Button(
                    onClick = { controller.undo() },
                    enabled = s.canUndo,
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = undoDesc },
                ) { Text(stringResource(R.string.measure_undo)) }
                if (s.pointCount > 0 && !s.rectMode && !s.angleMode && !s.boxMode && !s.roomMode) {
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
        planSnapshot?.takeIf { planOpen }?.let { RoomPlanScreen(it, settings.unitSystem) { planOpen = false } }
    }
}

/** Max on-screen distance a point may be pulled by snapping. */
private val SNAP_RADIUS = 24.dp
