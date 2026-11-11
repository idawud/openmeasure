package app.openmeasure

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.openmeasure.level.LevelScreen
import app.openmeasure.settings.SettingsRepository
import app.openmeasure.settings.SettingsScreen

/** Bottom tabs. Only [MEASURE] is live; the others are placeholders or disabled. */
enum class MainTab(val labelRes: Int, val icon: ImageVector, val enabled: Boolean) {
    MEASURE(R.string.tab_measure, AppIcons.Measure, enabled = true),
    LEVEL(R.string.tab_level, AppIcons.Level, enabled = true),
    HEIGHT(R.string.tab_height, AppIcons.Height, enabled = false),
}

/** Top-level navigation: tab switcher plus a gear icon that opens Settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(settingsRepository: SettingsRepository) {
    if (!settingsRepository.settings.accuracyDisclaimerAccepted) {
        AccuracyDisclaimerDialog(onAccept = settingsRepository::acceptAccuracyDisclaimer)
    }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    if (showSettings) {
        SettingsScreen(repository = settingsRepository, onBack = { showSettings = false })
        return
    }

    var selected by rememberSaveable { mutableStateOf(MainTab.MEASURE) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            imageVector = AppIcons.Settings,
                            contentDescription = stringResource(R.string.open_settings),
                        )
                    }
                },
            )
        },
        bottomBar = {
            Column {
                Text(
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
                            enabled = tab.enabled,
                            onClick = { selected = tab },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (selected) {
                MainTab.MEASURE -> ArScreen(settingsRepository)
                MainTab.LEVEL -> LevelScreen(settingsRepository)
                MainTab.HEIGHT -> Placeholder()
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
