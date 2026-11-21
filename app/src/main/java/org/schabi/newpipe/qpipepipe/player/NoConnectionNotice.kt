package org.schabi.newpipe.qpipepipe.player

import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.schabi.newpipe.R
import org.schabi.newpipe.error.DebugErrorLog
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.PlayerError

/**
 * The "No internet connection" notification and toast that replace the bug report notification
 * when playback stopped for a missing connection.
 */
internal class NoConnectionNotice(private val player: Player) {

    /**
     * Tell the user that playback stopped because there is no internet connection, instead of
     * the bug report notification: nothing is broken, and the network retries resume playback by
     * themselves once the connection is back. The trace still goes to the debug error log.
     */
    fun show(error: PlayerError) {
        val context = player.getContext()
        DebugErrorLog.record(context, errorInfo(error))
        val notification = NotificationCompat.Builder(
            context, context.getString(R.string.error_report_channel_id)
        )
            .setSmallIcon(R.drawable.ic_cloud_off)
            .setContentTitle(context.getString(R.string.no_internet_connection))
            .setContentText(context.getString(R.string.no_internet_connection_resume))
            .setOnlyAlertOnce(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        // The notification is silent, so a toast says it too, like the error notification does.
        Toast.makeText(context, R.string.no_internet_connection, Toast.LENGTH_SHORT).show()
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

    private companion object {
        const val NOTIFICATION_ID = 5340682
    }
}
