package app.openmeasure

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.openmeasure.history.ExportActions
import app.openmeasure.ui.OmColors
import app.openmeasure.ui.PillButton
import app.openmeasure.history.RoomExport
import app.openmeasure.measure.box.VolumeContent
import app.openmeasure.measure.label.LabelContent
import app.openmeasure.measure.rect.AreaContent
import app.openmeasure.measure.room.RoomWarning
import app.openmeasure.measure.units.AreaFormatter
import app.openmeasure.measure.units.LengthFormatter
import app.openmeasure.measure.units.UnitSystem
import app.openmeasure.measure.units.VolumeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * F15 full-screen floor plan for a closed room [result] (a snapshot, so it does not flicker with tracking):
 * plan with wall dimensions, area / perimeter / ceiling / volume with confidence, per-wall list, Save and Share.
 * Side-by-side in wide windows, stacked otherwise (rotation / resize safe).
 */
@Composable
fun RoomPlanScreen(result: RoomResult, units: UnitSystem, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val snackbar = remember { SnackbarHostState() }
    val summary = roomSummary(result.plan, result.labels.area, units)
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(AppIcons.ChevronBack, stringResource(R.string.room_close_plan)) }
                    Text(
                        stringResource(R.string.room_plan_title), style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val half = maxHeight * 0.5f
                    val colors = PlanColors(wall = MaterialTheme.colorScheme.onSurface, fill = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    val canvas = @Composable { m: Modifier ->
                        PlanCanvas(result.plan, units, m.semantics { contentDescription = summary }, result.labels, colors = colors)
                    }
                    if (maxWidth > maxHeight && maxWidth >= 560.dp) {
                        Row(Modifier.fillMaxSize()) {
                            canvas(Modifier.weight(1f).fillMaxSize().padding(8.dp))
                            Column(Modifier.width(320.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                                RoomSummary(result, units, snackbar)
                            }
                        }
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            canvas(Modifier.fillMaxWidth().weight(1f).padding(8.dp))
                            Column(Modifier.fillMaxWidth().heightIn(max = half).verticalScroll(rememberScrollState()).padding(16.dp)) {
                                RoomSummary(result, units, snackbar)
                            }
                        }
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun ColumnScope.RoomSummary(result: RoomResult, units: UnitSystem, snackbar: SnackbarHostState) {
    val l = result.labels
    val plan = result.plan
    when (val a = l.area) {
        is AreaContent.Area -> Text(
            stringResource(R.string.room_area, AreaFormatter.format(a.squareMeters, units)) + " · " +
                stringResource(R.string.history_confidence, levelText(a.level)),
            style = MaterialTheme.typography.titleMedium,
        )
        is AreaContent.Hint -> Text(hintText(a.reason), color = MaterialTheme.colorScheme.error)
        AreaContent.Invalid -> Text(stringResource(R.string.room_invalid), color = MaterialTheme.colorScheme.error)
    }
    (l.perimeter as? LabelContent.Length)?.let { Text(stringResource(R.string.room_perimeter, LengthFormatter.format(it.meters, units))) }
    when (val c = l.ceiling) {
        is LabelContent.Length -> Text(
            stringResource(R.string.room_ceiling, LengthFormatter.format(c.meters, units)) + " · " +
                stringResource(R.string.history_confidence, levelText(c.level)),
        )
        is LabelContent.Hint -> Text(hintText(c.reason))
        null -> Text(
            stringResource(if (RoomWarning.CEILING_TOO_LOW in plan.warnings) R.string.room_ceiling_too_low else R.string.room_no_ceiling),
            style = MaterialTheme.typography.bodySmall,
        )
    }
    (l.volume as? VolumeContent.Volume)?.let { Text(stringResource(R.string.room_volume, VolumeFormatter.format(it.cubicMeters, units))) }
    if (plan.isValid) Text(stringResource(if (plan.isRectilinear) R.string.room_rectilinear else R.string.room_irregular), style = MaterialTheme.typography.bodySmall)
    if (RoomWarning.UNEVEN_FLOOR in plan.warnings) Text(stringResource(R.string.room_uneven), color = MaterialTheme.colorScheme.error)
    l.walls.forEachIndexed { i, w ->
        val text = when (w) {
            is LabelContent.Length -> stringResource(R.string.room_wall, i + 1, LengthFormatter.format(w.meters, units), levelText(w.level))
            is LabelContent.Hint -> stringResource(R.string.room_wall_hint, i + 1, hintText(w.reason))
        }
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
    RoomActionsRow(result, units, snackbar)
}

@Composable
private fun RoomActionsRow(result: RoomResult, units: UnitSystem, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val failed = stringResource(R.string.history_export_failed)
    fun share(mime: String, write: suspend () -> Uri) {
        scope.launch {
            try {
                context.startActivity(ExportActions.fileShareIntent(context, write(), mime))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                snackbar.showSnackbar(failed)
            }
        }
    }
    val candidate = SaveCandidate.ofRoom(result)
    // A ceiling the scorer refused to show is left out of the export.
    val plan = if (result.labels.ceiling is LabelContent.Length) result.plan else result.plan.withCeilingHeight(null)
    FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HistorySaveButton(snackbar, enabled = candidate != null, container = OmColors.SurfaceHigh) { candidate?.toItem(null, System.currentTimeMillis()) }
        // Exports only carry numbers the user was allowed to see (displayable area).
        PillButton(
            stringResource(R.string.room_share_svg), { share(RoomExport.SVG_MIME) { RoomExport.svg(context, plan, units) } },
            enabled = candidate != null, container = OmColors.SurfaceHigh,
        )
        PillButton(
            stringResource(R.string.room_share_pdf), { share(RoomExport.PDF_MIME) { RoomExport.pdf(context, plan, units) } },
            enabled = candidate != null, container = OmColors.SurfaceHigh,
        )
    }
}

@Composable
private fun levelText(level: app.openmeasure.measure.confidence.ConfidenceLevel): String = stringResource(
    when (level) {
        app.openmeasure.measure.confidence.ConfidenceLevel.HIGH -> R.string.confidence_high
        app.openmeasure.measure.confidence.ConfidenceLevel.MEDIUM -> R.string.confidence_medium
        else -> R.string.confidence_low
    },
)
