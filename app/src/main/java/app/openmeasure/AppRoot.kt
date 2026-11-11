package app.openmeasure

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.openmeasure.ar.ArSessionManager
import app.openmeasure.history.HistoryScreen
import app.openmeasure.level.LevelScreen
import app.openmeasure.settings.SettingsRepository
import app.openmeasure.settings.SettingsScreen

/** Bottom tabs. [HEIGHT] is enabled only once the shared AR session reports depth support. */
enum class MainTab(val labelRes: Int, val icon: ImageVector) {
    MEASURE(R.string.tab_measure, AppIcons.Measure),
    LEVEL(R.string.tab_level, AppIcons.Level),
    HEIGHT(R.string.tab_height, AppIcons.Height);

    fun enabled(depthSupported: Boolean) = this != HEIGHT || depthSupported
}

/** Top-level navigation: tab switcher plus a gear icon that opens Settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(settingsRepository: SettingsRepository) {
    if (!settingsRepository.settings.accuracyDisclaimerAccepted) {
        AccuracyDisclaimerDialog(onAccept = settingsRepository::acceptAccuracyDisclaimer)
    }
    // One AR session for the whole app (Measure + Height); screens pause it, this closes it.
    val arManager = remember { ArSessionManager() }
    DisposableEffect(arManager) { onDispose { arManager.close() } }
    val depthSupported by arManager.depthSupported.collectAsState()
    // Remembered above the Settings early return so the session and depth flag survive opening Settings.
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    if (showHistory) {
        HistoryScreen(settingsRepository, onBack = { showHistory = false })
        return
    }
    if (showSettings) {
        SettingsScreen(repository = settingsRepository, onBack = { showSettings = false })
        return
    }

    var selected by rememberSaveable { mutableStateOf(MainTab.MEASURE) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        AppScaffold(settingsRepository, arManager, depthSupported, wide, selected, { selected = it }, { showSettings = true }, { showHistory = true })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScaffold(
    settingsRepository: SettingsRepository,
    arManager: ArSessionManager,
    depthSupported: Boolean,
    wide: Boolean,
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(imageVector = AppIcons.History, contentDescription = stringResource(R.string.open_history))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = AppIcons.Settings,
                            contentDescription = stringResource(R.string.open_settings),
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (!wide) Column {
                if (!depthSupported) Text(
                    text = stringResource(R.string.height_depth_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                )
                NavigationBar {
                    MainTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selected == tab,
                            enabled = tab.enabled(depthSupported),
                            onClick = { onSelect(tab) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (wide) {
                NavigationRail {
                    MainTab.entries.forEach { tab ->
                        NavigationRailItem(
                            selected = selected == tab,
                            enabled = tab.enabled(depthSupported),
                            onClick = { onSelect(tab) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                when (selected) {
                    MainTab.MEASURE -> ArScreen(settingsRepository, arManager)
                    MainTab.LEVEL -> LevelScreen(settingsRepository)
                    MainTab.HEIGHT -> if (depthSupported) ArScreen(settingsRepository, arManager, heightMode = true) else Placeholder()
                }
            }
        }
    }
}

@Composable
private fun Placeholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = stringResource(R.string.height_depth_note), style = MaterialTheme.typography.bodyLarge)
    }
}
