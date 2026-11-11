package app.openmeasure

import android.app.Application
import app.openmeasure.crash.CrashLogger
import app.openmeasure.settings.SettingsRepository
import java.io.File

/** Installs the local-only crash logger before any activity starts. */
class OpenMeasureApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogger(
            crashDir = File(filesDir, CrashLogger.DIR_NAME),
            optedOut = { SettingsRepository.isAnalyticsOptedOut(this) },
        ).install()
    }
}
