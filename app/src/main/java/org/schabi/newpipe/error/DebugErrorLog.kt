package org.schabi.newpipe.error

import android.content.Context
import android.util.Log
import org.schabi.newpipe.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.WeakHashMap
import java.util.concurrent.Executors

/**
 * Debug builds only: appends every error reported through [ErrorUtil] or [ErrorPanelHelper] to a
 * daily file in `Android/data/<app id>/files/errors/`, so the trace survives even when the error
 * notification is not shown. Playback events that the user never sees as an error (see
 * [recordPlayback]) go to the same file. Files are never rotated or deleted by the app.
 */
object DebugErrorLog {
    private const val TAG = "DebugErrorLog"
    private const val DIRECTORY = "errors"
    private const val BUILD_NUMBER_ASSET = "debug_build.txt"
    private const val CALLER_FRAMES = 15

    private val writer = Executors.newSingleThreadExecutor()

    // The same ErrorInfo can pass through several entry points (snackbar, then error screen)
    private val recorded: MutableSet<ErrorInfo> =
        Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))

    @JvmStatic
    fun record(context: Context, errorInfo: ErrorInfo) {
        if (!BuildConfig.DEBUG || !recorded.add(errorInfo)) {
            return
        }
        val now = Date()
        val appContext = context.applicationContext
        val message = runCatching { appContext.getString(errorInfo.messageStringId) }.getOrDefault("")
        append(appContext, now) { build -> formatEntry(errorInfo, now, build, message) }
    }

    /**
     * Appends a playback event that is not reported as an error, e.g. a silent reload of the
     * current video or a jump back in it, with the code path that caused it.
     */
    @JvmStatic
    fun recordPlayback(context: Context, event: String, details: List<String>) {
        if (!BuildConfig.DEBUG) {
            return
        }
        val now = Date()
        val caller = Throwable().stackTrace
            .dropWhile { it.className.startsWith(DebugErrorLog::class.java.name) }
            .take(CALLER_FRAMES)
            .map { it.toString() }
        append(context.applicationContext, now) { build ->
            formatPlaybackEntry(event, details, caller, now, build)
        }
    }

    private fun append(appContext: Context, time: Date, entry: (build: String) -> String) {
        writer.execute {
            try {
                val directory = appContext.getExternalFilesDir(DIRECTORY)
                    ?: File(appContext.filesDir, DIRECTORY)
                directory.mkdirs()
                File(directory, fileName(time)).appendText(entry(buildLabel(appContext)))
            } catch (e: Exception) {
                Log.e(TAG, "Could not write the error log", e)
            }
        }
    }

    fun fileName(time: Date): String =
        "errors-" + SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(time) + ".log"

    fun formatEntry(errorInfo: ErrorInfo, time: Date, build: String, message: String): String {
        val entry = header(time, build)
            .append("Action: ").append(errorInfo.userAction.message).append('\n')
            .append("Service: ").append(errorInfo.serviceName).append('\n')
            .append("Request: ").append(errorInfo.request).append('\n')
            .append("Message: ").append(message).append('\n')
        errorInfo.stackTraces.forEachIndexed { index, trace ->
            entry.append("--- trace ").append(index + 1).append('/')
                .append(errorInfo.stackTraces.size).append(" ---\n")
                .append(trace.trimEnd()).append('\n')
        }
        return entry.append('\n').toString()
    }

    fun formatPlaybackEntry(
        event: String,
        details: List<String>,
        caller: List<String>,
        time: Date,
        build: String,
    ): String {
        val entry = header(time, build).append("Playback: ").append(event).append('\n')
        details.forEach { entry.append(it).append('\n') }
        entry.append("--- caller ---\n")
        caller.forEach { entry.append("\tat ").append(it).append('\n') }
        return entry.append('\n').toString()
    }

    private fun header(time: Date, build: String): StringBuilder {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.ROOT).format(time)
        return StringBuilder()
            .append("===== ").append(timestamp).append(" =====\n")
            .append("Build: ").append(build).append('\n')
    }

    private fun buildLabel(context: Context): String {
        val number = runCatching {
            context.assets.open(BUILD_NUMBER_ASSET).bufferedReader().use { it.readLine()?.trim() }
        }.getOrNull()
        val version = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        return if (number.isNullOrEmpty()) version else "$version, debug $number"
    }
}
