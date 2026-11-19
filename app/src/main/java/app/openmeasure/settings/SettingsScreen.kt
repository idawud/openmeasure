package app.openmeasure.settings

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.openmeasure.AccuracyDisclaimerDialog
import app.openmeasure.R
import app.openmeasure.measure.units.UnitSystem
import app.openmeasure.ui.OmColors
import app.openmeasure.ui.ScreenScaffold

/** Unit choices in picker order: automatic families first, then the fixed units. */
private val UNIT_CHOICES = listOf(
    UnitSystem.METRIC, UnitSystem.MILLIMETERS, UnitSystem.CENTIMETERS, UnitSystem.METERS,
    UnitSystem.IMPERIAL, UnitSystem.INCHES, UnitSystem.FEET,
)

private fun unitLabelRes(unit: UnitSystem): Int = when (unit) {
    UnitSystem.METRIC -> R.string.unit_auto_metric
    UnitSystem.MILLIMETERS -> R.string.unit_millimetres
    UnitSystem.CENTIMETERS -> R.string.unit_centimetres
    UnitSystem.METERS -> R.string.unit_metres
    UnitSystem.IMPERIAL -> R.string.unit_auto_imperial
    UnitSystem.INCHES -> R.string.unit_inches
    UnitSystem.FEET -> R.string.unit_feet
}

/** Full-screen settings page. Back navigation is handled by the caller via [onBack]. */
@Composable
fun SettingsScreen(repository: SettingsRepository, onBack: () -> Unit) {
    val settings = repository.settings
    BackHandler(onBack = onBack)
    ScreenScaffold(title = stringResource(R.string.settings_title), onBack = onBack) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionHeader(stringResource(R.string.settings_units))
            SettingsGroup {
                UNIT_CHOICES.forEachIndexed { i, unit ->
                    if (i > 0) RowDivider()
                    RadioRow(
                        label = stringResource(unitLabelRes(unit)),
                        selected = settings.unitSystem == unit,
                        onSelect = { repository.setUnitSystem(unit) },
                    )
                }
            }

            SectionHeader(stringResource(R.string.settings_measuring))
            SettingsGroup {
                ToggleRow(
                    title = stringResource(R.string.settings_snap),
                    description = stringResource(R.string.settings_snap_desc),
                    checked = settings.snapEnabled,
                    onCheckedChange = repository::setSnapEnabled,
                )
            }

            SectionHeader(stringResource(R.string.settings_feedback))
            SettingsGroup {
                ToggleRow(
                    title = stringResource(R.string.settings_haptics),
                    description = stringResource(R.string.settings_haptics_desc),
                    checked = settings.hapticsEnabled,
                    onCheckedChange = repository::setHapticsEnabled,
                )
                RowDivider()
                ToggleRow(
                    title = stringResource(R.string.settings_sound),
                    description = stringResource(R.string.settings_sound_desc),
                    checked = settings.soundEnabled,
                    onCheckedChange = repository::setSoundEnabled,
                )
            }

            SectionHeader(stringResource(R.string.settings_privacy))
            SettingsGroup {
                ToggleRow(
                    title = stringResource(R.string.settings_analytics),
                    description = stringResource(R.string.settings_analytics_desc),
                    checked = settings.analyticsOptOut,
                    onCheckedChange = repository::setAnalyticsOptOut,
                )
            }

            AboutSection()
        }
    }
}

@Composable
private fun AboutSection() {
    val context = LocalContext.current
    val version = remember(context) { appVersionName(context) }
    var showDisclaimer by rememberSaveable { mutableStateOf(false) }
    SectionHeader(stringResource(R.string.settings_about))
    SettingsGroup {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(R.string.about_version, version),
                style = MaterialTheme.typography.bodyMedium, color = OmColors.TextSecondary,
            )
            Text(
                text = stringResource(R.string.about_on_device),
                style = MaterialTheme.typography.bodyMedium, color = OmColors.TextSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = stringResource(R.string.about_crash_logs),
                style = MaterialTheme.typography.bodyMedium, color = OmColors.TextSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        RowDivider()
        Text(
            text = stringResource(R.string.disclaimer_title),
            style = MaterialTheme.typography.bodyLarge, color = OmColors.Accent,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { showDisclaimer = true }
                .padding(horizontal = 16.dp, vertical = 16.dp),
        )
    }
    Spacer(Modifier.height(24.dp))
    if (showDisclaimer) AccuracyDisclaimerDialog(onDismiss = { showDisclaimer = false })
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
