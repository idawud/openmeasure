package app.openmeasure

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.openmeasure.measure.angle.AngleContent
import app.openmeasure.measure.angle.AngleLabel
import app.openmeasure.measure.box.VolumeContent
import app.openmeasure.measure.confidence.ConfidenceLevel
import app.openmeasure.measure.label.LabelContent
import app.openmeasure.measure.rect.AreaContent
import app.openmeasure.measure.units.AreaFormatter
import app.openmeasure.measure.units.LengthFormatter
import app.openmeasure.measure.units.UnitSystem
import app.openmeasure.measure.units.VolumeFormatter
import app.openmeasure.ui.ConfidenceDot
import app.openmeasure.ui.Dimens
import app.openmeasure.ui.OmColors
import app.openmeasure.ui.OmText
import app.openmeasure.ui.PillButton
import kotlinx.coroutines.launch

internal data class DetailRow(val label: String, val value: String, val level: ConfidenceLevel?)

/** What the sheet shows. Built only from displayable contents (Length / Area / Volume / Angle), so no number slips past confidence gating. */
internal data class DetailModel(val title: String, val level: ConfidenceLevel?, val rows: List<DetailRow>) {
    fun copyText() = buildString {
        append(title)
        rows.forEach { append('\n').append(it.label).append(": ").append(it.value) }
    }
}

private fun worst(levels: List<ConfidenceLevel>): ConfidenceLevel? = levels.maxByOrNull { it.ordinal }

/** Null when nothing displayable exists. [segmentLabel] formats "Segment n". */
internal fun buildDetail(s: MeasureUiState, units: UnitSystem, segmentLabel: (Int) -> String): DetailModel? {
    val rows = s.segments.mapIndexedNotNull { i, su ->
        (su.label as? LabelContent.Length)?.let { DetailRow(segmentLabel(i + 1), LengthFormatter.format(it.meters, units), it.level) }
    }
    val lengths = s.segments.mapNotNull { (it.label as? LabelContent.Length)?.level }
    s.rect?.area?.let { a ->
        if (a is AreaContent.Area) return DetailModel(AreaFormatter.format(a.squareMeters, units), a.level, rows)
    }
    s.box?.volume?.let { v ->
        if (v is VolumeContent.Volume) return DetailModel(VolumeFormatter.format(v.cubicMeters, units), v.level, rows)
    }
    s.angle?.content?.let { c ->
        if (c is AngleContent.Angle) return DetailModel(AngleLabel.format(c.degrees), c.level, emptyList())
    }
    if (rows.isEmpty()) return null
    val total = s.totalMeters
    return if (total != null) DetailModel(LengthFormatter.format(total, units), worst(lengths), rows)
    else DetailModel(rows.first().value, rows.first().level, rows)
}

/** Compact bottom sheet: value as title, per-segment values, confidence, and Copy / Save / Share capture. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MeasureDetailSheet(
    model: DetailModel,
    canSave: Boolean,
    saver: HistorySaver,
    capture: CaptureAction,
    snackbar: SnackbarHostState,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val copied = stringResource(R.string.detail_copied)
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = OmColors.SurfaceHigh, contentColor = Color.White) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = Dimens.maxContent).fillMaxWidth().navigationBarsPadding()
                    .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp),
            ) {
                Text(model.title, style = OmText.sheetValue, modifier = Modifier.semantics { heading() })
                model.level?.let {
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ConfidenceDot(confidenceColor(it))
                        Text(
                            stringResource(R.string.history_confidence, confidenceText(it)),
                            style = MaterialTheme.typography.bodyMedium, color = OmColors.TextSecondary,
                        )
                    }
                }
                model.rows.forEach { row ->
                    HorizontalDivider(Modifier.padding(top = 12.dp), color = OmColors.Outline)
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(row.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = OmColors.TextSecondary)
                        Text(row.value, style = OmText.measure)
                        row.level?.let {
                            ConfidenceDot(confidenceColor(it), Modifier.padding(start = 10.dp))
                            Text(confidenceText(it), Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val sheetPill = OmColors.Surface
                    PillButton(stringResource(R.string.detail_copy), {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText(model.title, model.copyText()))
                        scope.launch { snackbar.showSnackbar(copied) }
                        onDismiss()
                    }, container = sheetPill)
                    PillButton(
                        stringResource(R.string.history_save), { saver.save(); onDismiss() },
                        enabled = canSave && !saver.busy, container = sheetPill,
                        description = stringResource(R.string.history_save_desc),
                    )
                    PillButton(
                        stringResource(R.string.detail_share_capture), { onDismiss(); capture.run(shareNow = true) },
                        enabled = !capture.busy, filled = true,
                    )
                }
            }
        }
    }
}
