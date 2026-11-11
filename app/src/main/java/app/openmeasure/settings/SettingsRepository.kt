package app.openmeasure.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import app.openmeasure.measure.units.UnitSystem
import app.openmeasure.measure.units.defaultSystemForRegion
import java.util.Locale

/** Immutable snapshot of user preferences. */
data class AppSettings(
    val unitSystem: UnitSystem,
    val hapticsEnabled: Boolean,
    val soundEnabled: Boolean,
    val analyticsOptOut: Boolean,
)

/**
 * Persists [AppSettings] in SharedPreferences and exposes them as Compose state.
 * Units default to the region of the device locale until the user picks one.
 */
class SettingsRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val defaultUnit = defaultSystemForRegion(Locale.getDefault().country)

    var settings: AppSettings by mutableStateOf(load())
        private set

    fun setUnitSystem(unit: UnitSystem) = save(settings.copy(unitSystem = unit))

    fun setHapticsEnabled(enabled: Boolean) = save(settings.copy(hapticsEnabled = enabled))

    fun setSoundEnabled(enabled: Boolean) = save(settings.copy(soundEnabled = enabled))

    fun setAnalyticsOptOut(optOut: Boolean) = save(settings.copy(analyticsOptOut = optOut))

    private fun load(): AppSettings = AppSettings(
        unitSystem = prefs.getString(KEY_UNITS, null)
            ?.let { name -> UnitSystem.entries.firstOrNull { it.name == name } }
            ?: defaultUnit,
        hapticsEnabled = prefs.getBoolean(KEY_HAPTICS, true),
        soundEnabled = prefs.getBoolean(KEY_SOUND, true),
        analyticsOptOut = prefs.getBoolean(KEY_ANALYTICS_OPT_OUT, false),
    )

    private fun save(updated: AppSettings) {
        prefs.edit {
            putString(KEY_UNITS, updated.unitSystem.name)
            putBoolean(KEY_HAPTICS, updated.hapticsEnabled)
            putBoolean(KEY_SOUND, updated.soundEnabled)
            putBoolean(KEY_ANALYTICS_OPT_OUT, updated.analyticsOptOut)
        }
        settings = updated
    }

    private companion object {
        const val PREFS_NAME = "openmeasure_settings"
        const val KEY_UNITS = "unit_system"
        const val KEY_HAPTICS = "haptics_enabled"
        const val KEY_SOUND = "sound_enabled"
        const val KEY_ANALYTICS_OPT_OUT = "analytics_opt_out"
    }
}
