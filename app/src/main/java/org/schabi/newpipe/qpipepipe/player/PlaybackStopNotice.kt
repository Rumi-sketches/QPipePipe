package org.schabi.newpipe.qpipepipe.player

import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.schabi.newpipe.R
import org.schabi.newpipe.error.DebugErrorLog
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.PlayerError

/**
 * A notification and a toast that say why playback stopped, in place of the bug report
 * notification: the cause is known and nothing in the app is broken. The trace still goes to the
 * debug error log. All notices share one notification, so a new one replaces the previous one.
 */
internal class PlaybackStopNotice private constructor(
    private val player: Player,
    @DrawableRes private val icon: Int,
    @StringRes private val title: Int,
    @StringRes private val text: Int,
    @StringRes private val toast: Int,
    private val toastLength: Int,
) {
    companion object {
        private const val NOTIFICATION_ID = 5340682

        /** No connection: the network retries resume playback once it is back. */
        fun noConnection(player: Player) = PlaybackStopNotice(player, R.drawable.ic_cloud_off,
            R.string.no_internet_connection, R.string.no_internet_connection_resume,
            R.string.no_internet_connection, Toast.LENGTH_SHORT)

        /** YouTube refuses fresh stream URLs too: the IP is blocked for a while. */
        fun ipBlocked(player: Player) = PlaybackStopNotice(player, R.drawable.ic_blocking,
            R.string.network_error, R.string.ip_blocked_summary, R.string.ip_blocked_summary,
            Toast.LENGTH_LONG)
    }

    fun show(error: PlayerError) {
        val context = player.getContext()
        DebugErrorLog.record(context, errorInfo(error))
        val text = context.getString(text)
        val notification = NotificationCompat.Builder(
            context, context.getString(R.string.error_report_channel_id)
        )
            .setSmallIcon(icon)
            .setContentTitle(context.getString(title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOnlyAlertOnce(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        // The notification is silent, so a toast says it too, like the error notification does.
        Toast.makeText(context, toast, toastLength).show()
    }

    fun dismiss() {
        NotificationManagerCompat.from(player.getContext()).cancel(NOTIFICATION_ID)
    }

    // The same report the error handler's error notification carries.
    private fun errorInfo(error: PlayerError): ErrorInfo {
        val currentMetadata = player.currentMetadata
        return if (currentMetadata == null) {
            ErrorInfo(
                error, UserAction.PLAY_STREAM,
                "Player error[type=" + error.errorCodeName
                    + "] occurred, currentMetadata is null"
            )
        } else {
            ErrorInfo(
                error, UserAction.PLAY_STREAM,
                "Player error[type=" + error.errorCodeName
                    + "] occurred while playing " + currentMetadata.url,
                currentMetadata.serviceId
            )
        }
    }
}
