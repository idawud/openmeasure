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
    val accuracyDisclaimerAccepted: Boolean,
    val snapEnabled: Boolean = true,
    val guideEnabled: Boolean = true,
    val rulerEnabled: Boolean = true,
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

    fun setSnapEnabled(enabled: Boolean) = save(settings.copy(snapEnabled = enabled))

    fun setGuideEnabled(enabled: Boolean) = save(settings.copy(guideEnabled = enabled))

    fun setRulerEnabled(enabled: Boolean) = save(settings.copy(rulerEnabled = enabled))

    fun acceptAccuracyDisclaimer() = save(settings.copy(accuracyDisclaimerAccepted = true))

    private fun load(): AppSettings = AppSettings(
        unitSystem = prefs.getString(KEY_UNITS, null)
            ?.let { name -> UnitSystem.entries.firstOrNull { it.name == name } }
            ?: defaultUnit,
        hapticsEnabled = prefs.getBoolean(KEY_HAPTICS, true),
        soundEnabled = prefs.getBoolean(KEY_SOUND, true),
        analyticsOptOut = prefs.getBoolean(KEY_ANALYTICS_OPT_OUT, false),
        accuracyDisclaimerAccepted = prefs.getBoolean(KEY_DISCLAIMER_ACCEPTED, false),
        snapEnabled = prefs.getBoolean(KEY_SNAP, true),
        guideEnabled = prefs.getBoolean(KEY_GUIDE, true),
        rulerEnabled = prefs.getBoolean(KEY_RULER, true),
    )

    private fun save(updated: AppSettings) {
        prefs.edit {
            putString(KEY_UNITS, updated.unitSystem.name)
            putBoolean(KEY_HAPTICS, updated.hapticsEnabled)
            putBoolean(KEY_SOUND, updated.soundEnabled)
            putBoolean(KEY_ANALYTICS_OPT_OUT, updated.analyticsOptOut)
            putBoolean(KEY_DISCLAIMER_ACCEPTED, updated.accuracyDisclaimerAccepted)
            putBoolean(KEY_SNAP, updated.snapEnabled)
            putBoolean(KEY_GUIDE, updated.guideEnabled)
            putBoolean(KEY_RULER, updated.rulerEnabled)
        }
        settings = updated
    }

    companion object {
        private const val PREFS_NAME = "openmeasure_settings"
        private const val KEY_UNITS = "unit_system"
        private const val KEY_HAPTICS = "haptics_enabled"
        private const val KEY_SOUND = "sound_enabled"
        private const val KEY_ANALYTICS_OPT_OUT = "analytics_opt_out"
        private const val KEY_DISCLAIMER_ACCEPTED = "accuracy_disclaimer_accepted"
        private const val KEY_SNAP = "snap_to_edges"
        private const val KEY_GUIDE = "guide_lines"
        private const val KEY_RULER = "ruler_ticks"

        /** Reads the opt-out flag without building a repository; safe to call from a crash handler. */
        fun isAnalyticsOptedOut(context: Context): Boolean =
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ANALYTICS_OPT_OUT, false)
    }
}
