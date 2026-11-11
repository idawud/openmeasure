package app.openmeasure.crash

import java.io.File
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/**
 * Local-only crash capture. When [optedOut] is false, writes each uncaught stack trace to
 * [crashDir] (keeping the newest [MAX_LOGS]), then delegates to the previous handler so the
 * normal crash behaviour still happens. No network, no third-party code.
 */
class CrashLogger(
    private val crashDir: File,
    private val optedOut: () -> Boolean,
) : Thread.UncaughtExceptionHandler {

    private var previous: Thread.UncaughtExceptionHandler? = null

    fun install() {
        previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            if (!optedOut()) write(thread, throwable, System.currentTimeMillis())
        } catch (e: Throwable) {
            // Logging must never hide the original crash.
        }
        val next = previous
        if (next != null) {
            next.uncaughtException(thread, throwable)
        } else {
            exitProcess(CRASH_EXIT_CODE)
        }
    }

    internal fun write(thread: Thread, throwable: Throwable, timeMillis: Long) {
        crashDir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date(timeMillis))
        val trace = StringWriter().also { throwable.printStackTrace(java.io.PrintWriter(it)) }.toString()
        File(crashDir, "$FILE_PREFIX$stamp$FILE_SUFFIX").writeText("Thread: ${thread.name}\n\n$trace")
        prune()
    }

    private fun prune() {
        // Names embed a fixed-width timestamp, so lexical order is chronological.
        crashDir.listFiles { f -> f.name.startsWith(FILE_PREFIX) && f.name.endsWith(FILE_SUFFIX) }
            ?.sortedBy { it.name }
            ?.dropLast(MAX_LOGS)
            ?.forEach { it.delete() }
    }

    companion object {
        const val DIR_NAME = "crashes"
        const val MAX_LOGS = 5
        private const val FILE_PREFIX = "crash-"
        private const val FILE_SUFFIX = ".txt"
        private const val CRASH_EXIT_CODE = 10
    }
}
