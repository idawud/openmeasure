package app.openmeasure.history

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.openmeasure.PlanCanvas
import app.openmeasure.PlanColors
import app.openmeasure.R
import app.openmeasure.data.HistoryItem
import app.openmeasure.data.MeasurementKind
import app.openmeasure.measure.geometry.Vec3
import app.openmeasure.measure.room.PlanSvg
import app.openmeasure.measure.room.RoomPlan
import app.openmeasure.measure.units.UnitSystem
import app.openmeasure.roomSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Rebuilds the plan of a saved ROOM entry (corners in tap order + stored ceiling height); null otherwise. */
internal fun roomPlanOf(item: HistoryItem): RoomPlan? {
    if (item.kind != MeasurementKind.ROOM || item.points.size < 3) return null
    return RoomPlan.fromCorners(item.points.map { Vec3(it.x, it.y, it.z) }).withCeilingHeight(item.heightMeters)
        .takeIf { it.isValid }
}

/** "4 walls · Perimeter 14.00 m · Ceiling height 2.50 m · Volume 30.000 m³" for PDF rows; null if not a room. */
internal fun roomDimsText(context: Context, item: HistoryItem, units: UnitSystem): String? {
    val plan = roomPlanOf(item) ?: return null
    val walls = context.resources.getQuantityString(R.plurals.room_walls, plan.wallCount, plan.wallCount)
    // The area is already the row's headline value.
    return (listOf(walls) + PlanSvg.summaryLines(plan, units, RoomExport.svgText(context)).drop(1)).joinToString(" · ")
}

/** History detail extras for a room: the drawn plan, summary lines and SVG / PDF share. */
@Composable
internal fun RoomHistoryDetail(item: HistoryItem, units: UnitSystem) {
    val plan = remember(item) { roomPlanOf(item) } ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var failed by remember { mutableStateOf(false) }
    val summary = roomSummary(plan, null, units, trustPlan = true)
    PlanCanvas(
        plan, units, Modifier.fillMaxWidth().height(220.dp).semantics { contentDescription = summary },
        colors = PlanColors(wall = MaterialTheme.colorScheme.onSurface, fill = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
    )
    PlanSvg.summaryLines(plan, units, RoomExport.svgText(context)).drop(1).forEach {
        Text(it, style = MaterialTheme.typography.bodyMedium)
    }
    fun share(mime: String, write: suspend () -> Uri) {
        scope.launch {
            try {
                context.startActivity(ExportActions.fileShareIntent(context, write(), mime))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed = true
            }
        }
    }
    Row {
        TextButton(onClick = { share(RoomExport.SVG_MIME) { RoomExport.svg(context, plan, units) } }, Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.room_share_svg))
        }
        TextButton(onClick = { share(RoomExport.PDF_MIME) { RoomExport.pdf(context, plan, units) } }, Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.room_share_pdf))
        }
    }
    if (failed) Text(stringResource(R.string.history_export_failed), color = MaterialTheme.colorScheme.error)
}
