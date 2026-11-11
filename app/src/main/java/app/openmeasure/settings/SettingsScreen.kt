package app.openmeasure.settings

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.openmeasure.AppIcons
import app.openmeasure.R
import app.openmeasure.measure.units.UnitSystem

/** Full-screen settings page. Back navigation is handled by the caller via [onBack]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(repository: SettingsRepository, onBack: () -> Unit) {
    val settings = repository.settings
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = AppIcons.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SectionHeader(stringResource(R.string.settings_units))
            RadioRow(
                label = stringResource(R.string.units_metric),
                selected = settings.unitSystem == UnitSystem.METRIC,
                onSelect = { repository.setUnitSystem(UnitSystem.METRIC) },
            )
            RadioRow(
                label = stringResource(R.string.units_imperial),
                selected = settings.unitSystem == UnitSystem.IMPERIAL,
                onSelect = { repository.setUnitSystem(UnitSystem.IMPERIAL) },
            )

            SectionHeader(stringResource(R.string.settings_measuring))
            ToggleRow(
                title = stringResource(R.string.settings_snap),
                description = stringResource(R.string.settings_snap_desc),
                checked = settings.snapEnabled,
                onCheckedChange = repository::setSnapEnabled,
            )
            ToggleRow(
                title = stringResource(R.string.settings_guide),
                description = stringResource(R.string.settings_guide_desc),
                checked = settings.guideEnabled,
                onCheckedChange = repository::setGuideEnabled,
            )

            SectionHeader(stringResource(R.string.settings_feedback))
            ToggleRow(
                title = stringResource(R.string.settings_haptics),
                description = stringResource(R.string.settings_haptics_desc),
                checked = settings.hapticsEnabled,
                onCheckedChange = repository::setHapticsEnabled,
            )
            ToggleRow(
                title = stringResource(R.string.settings_sound),
                description = stringResource(R.string.settings_sound_desc),
                checked = settings.soundEnabled,
                onCheckedChange = repository::setSoundEnabled,
            )

            SectionHeader(stringResource(R.string.settings_privacy))
            ToggleRow(
                title = stringResource(R.string.settings_analytics),
                description = stringResource(R.string.settings_analytics_desc),
                checked = settings.analyticsOptOut,
                onCheckedChange = repository::setAnalyticsOptOut,
            )

            AboutSection()
        }
        }
    }
}

@Composable
private fun AboutSection() {
    val context = LocalContext.current
    val version = remember(context) { appVersionName(context) }
    SectionHeader(stringResource(R.string.settings_about))
    Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.bodyLarge)
    Text(
        text = stringResource(R.string.about_version, version),
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        text = stringResource(R.string.about_on_device),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
    Text(
        text = stringResource(R.string.about_crash_logs),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
    )
}

@Suppress("DEPRECATION")
private fun appVersionName(context: Context): String = try {
    val pm = context.packageManager
    val info = if (Build.VERSION.SDK_INT >= 33) {
        pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        pm.getPackageInfo(context.packageName, 0)
    }
    info.versionName.orEmpty()
} catch (e: PackageManager.NameNotFoundException) {
    ""
}
