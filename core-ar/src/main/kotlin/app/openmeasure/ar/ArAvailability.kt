package app.openmeasure.ar

import android.content.Context
import com.google.ar.core.ArCoreApk

/** Result of checking whether ARCore can run on this device. */
sealed interface ArAvailabilityResult {
    /** ARCore is installed and usable. */
    data object Supported : ArAvailabilityResult

    /** Device supports ARCore but the service must be installed or updated. */
    data object NeedsInstall : ArAvailabilityResult

    /** Device cannot run ARCore, or the state could not be determined. */
    data object Unsupported : ArAvailabilityResult
}

object ArAvailability {
    /** Synchronous check. Call from a background thread if it may block. */
    fun check(context: Context): ArAvailabilityResult =
        when (ArCoreApk.getInstance().checkAvailability(context)) {
            ArCoreApk.Availability.SUPPORTED_INSTALLED -> ArAvailabilityResult.Supported
            ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD,
            ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> ArAvailabilityResult.NeedsInstall
            else -> ArAvailabilityResult.Unsupported
        }
}
