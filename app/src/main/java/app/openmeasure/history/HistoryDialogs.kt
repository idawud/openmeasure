package app.openmeasure.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.openmeasure.R
import app.openmeasure.data.HistoryItem
import app.openmeasure.measure.units.UnitSystem

/** Detail dialog: value, date, confidence, thumbnail, editable note, delete. */
@Composable
internal fun DetailDialog(
    item: HistoryItem,
    units: UnitSystem,
    onSaveNote: (String) -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    val shareDesc = stringResource(R.string.history_share_item_desc)
    var note by rememberSaveable(item.id) { mutableStateOf(item.note.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(valueText(item, units)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Thumbnail(item.photoUri, item.kind, Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 240.dp), sizePx = 640)
                Text(
                    "${kindLabel(item.kind)} · ${stringResource(R.string.history_confidence, confidenceLabel(item.confidenceLevel))}",
                    Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium,
                )
                boxDimsText(item, units)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Text(dateText(item.createdAtMillis), style = MaterialTheme.typography.bodySmall)
                if (item.points.isNotEmpty()) {
                    Text(stringResource(R.string.history_points, item.points.size), style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.history_note)) },
                    placeholder = { Text(stringResource(R.string.history_note_hint)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    minLines = 2,
                )
                TextButton(
                    onClick = onShare,
                    Modifier.heightIn(min = 48.dp).semantics { contentDescription = shareDesc },
                ) { Text(stringResource(R.string.history_share_item)) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSaveNote(note); onDismiss() }, Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.history_save_note))
            }
        },
        dismissButton = {
            TextButton(onClick = onDelete, Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.history_delete), color = MaterialTheme.colorScheme.error)
            }
        },
    )
}

@Composable
internal fun ConfirmDialog(title: String, body: String?, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = body?.let { { Text(it) } },
        confirmButton = {
            TextButton(onClick = onConfirm, Modifier.heightIn(min = 48.dp)) { Text(confirm, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.history_cancel)) }
        },
    )
}
