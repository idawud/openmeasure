package app.openmeasure.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.openmeasure.AppIcons
import app.openmeasure.R
import app.openmeasure.data.HistoryItem
import app.openmeasure.data.HistoryRepository
import app.openmeasure.settings.SettingsRepository
import kotlinx.coroutines.launch

/** F11 History: newest first, tap for details (note, delete), overflow "Clear all". Full-screen like Settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(settingsRepository: SettingsRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { HistoryRepository(context.applicationContext) }
    val items by remember { repo.observeAll() }.collectAsState(initial = null)
    val units = settingsRepository.settings.unitSystem
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val deletedText = stringResource(R.string.history_deleted)
    val undoText = stringResource(R.string.history_undo)
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }, enabled = !items.isNullOrEmpty()) {
                        Icon(AppIcons.MoreVert, stringResource(R.string.history_more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.history_clear_all)) },
                            onClick = { menuOpen = false; confirmClear = true },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            val list = items
            when {
                list == null -> Unit
                list.isEmpty() -> Text(
                    stringResource(R.string.history_empty),
                    Modifier.widthIn(max = 600.dp).padding(32.dp).align(Alignment.Center),
                    textAlign = TextAlign.Center,
                )
                else -> LazyColumn(Modifier.widthIn(max = 600.dp).fillMaxSize()) {
                    items(list, key = { it.id }) { item ->
                        HistoryRow(item, units) { selectedId = item.id }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    val selected: HistoryItem? = items?.firstOrNull { it.id == selectedId }
    if (selected != null) {
        DetailDialog(
            item = selected, units = units,
            onSaveNote = { note -> scope.launch { repo.updateNote(selected.id, note) } },
            onDelete = { confirmDelete = true },
            onDismiss = { selectedId = null; confirmDelete = false },
        )
        if (confirmDelete) ConfirmDialog(
            title = stringResource(R.string.history_delete_title), body = null, confirm = stringResource(R.string.history_delete),
            onConfirm = {
                confirmDelete = false; selectedId = null
                scope.launch {
                    repo.delete(selected.id)
                    if (snackbar.showSnackbar(deletedText, undoText) == SnackbarResult.ActionPerformed) repo.restore(selected)
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
    if (confirmClear) ConfirmDialog(
        title = stringResource(R.string.history_clear_title), body = stringResource(R.string.history_clear_body),
        confirm = stringResource(R.string.history_clear_all),
        onConfirm = { confirmClear = false; scope.launch { repo.deleteAll() } },
        onDismiss = { confirmClear = false },
    )
}
