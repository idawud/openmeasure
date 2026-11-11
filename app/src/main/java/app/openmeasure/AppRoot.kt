package app.openmeasure

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.openmeasure.ar.ArSessionManager
import app.openmeasure.history.HistoryScreen
import app.openmeasure.level.LevelScreen
import app.openmeasure.settings.SettingsRepository
import app.openmeasure.settings.SettingsScreen
import app.openmeasure.ui.CircleIconButton
import app.openmeasure.ui.Dimens
import app.openmeasure.ui.HintBanner
import app.openmeasure.ui.LocalTabBarHeight
import app.openmeasure.ui.OmColors

/**
 * Top-level shell: full-bleed content (camera / level) with History + Settings corner buttons and the tab row floating over it.
 * First launch shows the welcome page instead.
 */
@Composable
fun AppRoot(settingsRepository: SettingsRepository) {
    if (!settingsRepository.settings.accuracyDisclaimerAccepted) {
        WelcomeScreen(onContinue = settingsRepository::acceptAccuracyDisclaimer)
        return
    }
    // One AR session for the whole app (Measure + Height); screens pause it, this closes it.
    val arManager = remember { ArSessionManager() }
    DisposableEffect(arManager) { onDispose { arManager.close() } }
    val depthSupported by arManager.depthSupported.collectAsState()
    // Remembered above the early returns so the session and depth flag survive opening Settings / History.
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
    BoxWithConstraints(Modifier.fillMaxSize().background(OmColors.Black)) {
        val wide = maxWidth >= Dimens.wideBreakpoint
        CompositionLocalProvider(LocalTabBarHeight provides Dimens.tabBarHeight) {
            when (selected) {
                MainTab.MEASURE -> ArScreen(settingsRepository, arManager)
                MainTab.LEVEL -> LevelScreen(settingsRepository)
                MainTab.HEIGHT ->
                    if (depthSupported) ArScreen(settingsRepository, arManager, heightMode = true)
                    else HintBanner(stringResource(R.string.height_depth_note), Modifier.align(Alignment.Center).padding(Dimens.margin))
            }
            Row(Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 4.dp, vertical = 8.dp)) {
                CircleIconButton(AppIcons.History, stringResource(R.string.open_history), { showHistory = true })
                CircleIconButton(AppIcons.Settings, stringResource(R.string.open_settings), { showSettings = true })
            }
            MainTabBar(selected, depthSupported, wide, { selected = it }, Modifier.align(Alignment.BottomCenter))
        }
    }
}
