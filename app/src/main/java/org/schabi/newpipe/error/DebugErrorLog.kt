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
 * notification is not shown. Files are never rotated or deleted by the app.
 */
object DebugErrorLog {
    private const val TAG = "DebugErrorLog"
    private const val DIRECTORY = "errors"
    private const val BUILD_NUMBER_ASSET = "debug_build.txt"

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
        writer.execute {
            try {
                val directory = appContext.getExternalFilesDir(DIRECTORY)
                    ?: File(appContext.filesDir, DIRECTORY)
                directory.mkdirs()
                File(directory, fileName(now))
                    .appendText(formatEntry(errorInfo, now, buildLabel(appContext), message))
            } catch (e: Exception) {
                Log.e(TAG, "Could not write the error log", e)
            }
        }
    }

    fun fileName(time: Date): String =
        "errors-" + SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(time) + ".log"

    fun formatEntry(errorInfo: ErrorInfo, time: Date, build: String, message: String): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.ROOT).format(time)
        val entry = StringBuilder()
            .append("===== ").append(timestamp).append(" =====\n")
            .append("Build: ").append(build).append('\n')
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

    private fun buildLabel(context: Context): String {
        val number = runCatching {
            context.assets.open(BUILD_NUMBER_ASSET).bufferedReader().use { it.readLine()?.trim() }
        }.getOrNull()
        val version = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        return if (number.isNullOrEmpty()) version else "$version, debug $number"
    }
}
