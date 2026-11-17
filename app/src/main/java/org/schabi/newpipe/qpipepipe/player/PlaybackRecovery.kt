package org.schabi.newpipe.qpipepipe.player

import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.PlaybackException.ERROR_CODE_DECODING_FAILED
import com.google.android.exoplayer2.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
import com.google.android.exoplayer2.PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE
import com.google.android.exoplayer2.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
import com.google.android.exoplayer2.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
import com.google.android.exoplayer2.PlaybackException.ERROR_CODE_IO_UNSPECIFIED
import com.google.android.exoplayer2.PlaybackException.ERROR_CODE_TIMEOUT
import com.google.android.exoplayer2.PlaybackException.ERROR_CODE_UNSPECIFIED
import com.google.android.exoplayer2.upstream.HttpDataSource.InvalidResponseCodeException
import org.schabi.newpipe.R
import org.schabi.newpipe.error.DebugErrorLog
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.ktx.hasAssignableCause
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.PlayerError
import org.schabi.newpipe.player.mediaitem.PlayerMediaItem
import org.schabi.newpipe.util.InfoCache
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * QPipePipe's recovery from playback errors that upstream's
 * [org.schabi.newpipe.player.PlayerErrorHandler] would stop on, or loop on:
 *
 *  * a 403 after a long pause or a network change: drop the cached stream info and reload once,
 *    see [recoverFromForbidden];
 *  * network failures: reload at once, then after longer and longer waits, and say "No internet
 *    connection" instead of reporting a bug, see [recoverFromNetworkError];
 *  * a decoder that fails mid-stream: rebuild the source once, see [recoverFromDecoderFailure].
 *
 * The error handler asks [handles] before its own dispatch and lets [recover] take the errors this
 * class owns; [showsNoConnection] picks the message when the error stops playback. The player
 * reaches the rest ([onPlaybackReady], [reloadIfWaiting], [cancelDelayedReload], [release])
 * through the handler's `recovery`.
 *
 * [reload], [shutdown] and [currentItem] are the player actions only its package can reach.
 */
class PlaybackRecovery(
    private val player: Player,
    private val reload: () -> Unit,
    private val shutdown: () -> Unit,
    private val currentItem: () -> PlayerMediaItem?,
) {
    // One recovery per item from a 403, so fresh URLs that are refused too stop playback instead
    // of looping reload->403.
    private val forbiddenRetry = OneRetryPerItem(FORBIDDEN_RETRY_WINDOW_MS)

    // One recovery per item from a decoder that failed while decoding, so a stream the decoder
    // really can't decode stops playback instead of looping reload->fail.
    private val decoderRetry = OneRetryPerItem(DECODER_RETRY_WINDOW_MS)

    // Network failures in a row on the same queue item, with no playback getting ready in
    // between. Without a connection (a tunnel, no coverage in the car) every reload fails as
    // soon as DNS does, so the reloads wait longer and longer instead of looping reload->fail
    // with a toast and a notification each time.
    private var networkFailures = 0
    private var networkFailureItemUuid: String? = null
    private val retryHandler = Handler(Looper.getMainLooper())
    private val delayedReload = Runnable { reload() }
    private var reloadPending = false

    // Set by [recover] when the error it could not recover from is a missing connection that the
    // network retries will try again, read once by [showsNoConnection] right after.
    private var noConnectionToShow = false

    /** Whether [recover] owns this error instead of the error handler's own dispatch. */
    fun handles(error: PlaybackException): Boolean = routeOf(error.errorCode, error) != null

    /**
     * Recover from an error that [handles] accepted, or stop playback when recovering would only
     * loop.
     *
     * @return whether the error is caught: playback goes on without telling the user
     */
    fun recover(error: PlaybackException): Boolean {
        noConnectionToShow = false
        return when (routeOf(error.errorCode, error)) {
            Route.NETWORK -> {
                val caught = recoverFromNetworkError()
                // The retries will try this source again, which is what the message promises.
                noConnectionToShow = !caught && isNoConnection(error)
                caught
            }

            // Expected after a long pause or a network change: reload quietly, without the error
            // notification and without the detail page closing the player.
            Route.FORBIDDEN -> if (recoverFromForbidden()) {
                true
            } else {
                showIpBlocked()
                shutdown()
                false
            }

            // The decoder failed in the middle of the stream. A hardware decoder fails like this
            // when the system takes it back or its media process restarts (MediaCodec
            // DEAD_OBJECT), which says nothing about the stream: a new source gets a new decoder
            // and resumes at the same position.
            Route.DECODER -> if (recoverFromDecoderFailure()) {
                true
            } else {
                shutdown()
                false
            }

            null -> false
        }
    }

    /**
     * Tell the user that playback stopped because there is no internet connection, when that is
     * why [recover] stopped it. Otherwise another failure takes over, and the connection message
     * no longer describes what happened.
     *
     * @return whether the message was shown, in place of the error notification
     */
    fun showsNoConnection(error: PlayerError): Boolean {
        val show = noConnectionToShow
        noConnectionToShow = false
        if (show) {
            showNoConnection(error)
        } else {
            dismissNoConnection()
        }
        return show
    }

    /**
     * Rebuild the current source after a network failure: at once the first time, then after
     * longer and longer waits while the reloads keep failing on the same item, so playback comes
     * back by itself once the connection does.
     *
     * @return whether the failure repeats one the user was already told about
     */
    private fun recoverFromNetworkError(): Boolean {
        // The queue item is the one being loaded; the current item can still be the previous one.
        val item = player.playQueue?.item ?: currentItem()
        if (item?.uuid != networkFailureItemUuid) {
            networkFailureItemUuid = item?.uuid
            networkFailures = 0
        }
        val delayMs = networkRetryDelayMs(networkFailures)
        networkFailures++
        if (delayMs == 0L) {
            reloadNow()
        } else {
            Log.w(Player.TAG, "Network failure #$networkFailures, reloading in $delayMs ms")
            player.setRecovery()
            // A failure while a reload is already waiting replaces it instead of adding another.
            retryHandler.removeCallbacks(delayedReload)
            retryHandler.postDelayed(delayedReload, delayMs)
            reloadPending = true
        }
        return networkFailures > 1
    }

    /**
     * Playback got ready, so the network failures before it are over. The source can recover
     * without the delayed reload (the source manager retries a failed item by itself), and that
     * reload would then rebuild a source that is playing fine.
     */
    fun onPlaybackReady() {
        networkFailures = 0
        cancelDelayedReload()
        dismissNoConnection()
    }

    /**
     * The user asked to play while a reload waits out a network failure: the failed source can't
     * play, so reload now instead of letting the press do nothing until the wait is over.
     */
    fun reloadIfWaiting() {
        if (reloadPending) {
            reload()
        }
    }

    /** Drop a delayed reload: another reload replaced it, or the player is going away. */
    fun cancelDelayedReload() {
        retryHandler.removeCallbacks(delayedReload)
        reloadPending = false
    }

    /** The player is going away: nothing is left to reload, and nothing will resume by itself. */
    fun release() {
        cancelDelayedReload()
        dismissNoConnection()
    }

    private fun reloadNow() {
        player.setRecovery()
        reload()
    }

    /**
     * YouTube answers 403 when the stream URLs are no longer valid for this client: they expired,
     * or the network (and so the IP they are bound to) changed. Drop the cached stream info so the
     * reload extracts fresh URLs instead of rebuilding the source from the same stale ones. A
     * second 403 on the same item right after that means fresh URLs are refused too.
     *
     * @return whether a recovery was started
     */
    private fun recoverFromForbidden(): Boolean {
        val item = currentItem() ?: player.playQueue?.item
        if (!forbiddenRetry.tryRetry(item?.uuid, System.currentTimeMillis())) {
            return false
        }
        if (item != null) {
            InfoCache.getInstance().removeInfo(item.serviceId, item.url, InfoItem.InfoType.STREAM)
        }
        reloadNow()
        return true
    }

    /**
     * Rebuild the source after the decoder failed mid-stream, once per item: a second failure of
     * the same item soon after means a new decoder doesn't help.
     *
     * @return whether a recovery was started
     */
    private fun recoverFromDecoderFailure(): Boolean {
        val item = currentItem() ?: player.playQueue?.item
        if (!decoderRetry.tryRetry(item?.uuid, System.currentTimeMillis())) {
            return false
        }
        reloadNow()
        return true
    }

    // The same dialog the error handler shows for a 403 reported without an HTTP status.
    private fun showIpBlocked() {
        try {
            AlertDialog.Builder(player.getParentActivity())
                .setTitle(R.string.network_error)
                .setMessage(R.string.ip_blocked_summary)
                .setPositiveButton(R.string.ok) { _, _ -> }
                .show()
        } catch (e: Exception) {
            // when there is no context, e.g. background playing
            e.printStackTrace()
        }
    }

    /**
     * Tell the user that playback stopped because there is no internet connection, instead of
     * the bug report notification: nothing is broken, and the reloads above resume playback by
     * themselves once the connection is back. The trace still goes to the debug error log.
     */
    private fun showNoConnection(error: PlayerError) {
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
        NotificationManagerCompat.from(context).notify(NO_CONNECTION_NOTIFICATION_ID, notification)
        // The notification is silent, so a toast says it too, like the error notification does.
        Toast.makeText(context, R.string.no_internet_connection, Toast.LENGTH_SHORT).show()
    }

    private fun dismissNoConnection() {
        NotificationManagerCompat.from(player.getContext()).cancel(NO_CONNECTION_NOTIFICATION_ID)
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

    /** The recoveries this class owns, by the error they answer. */
    internal enum class Route { NETWORK, FORBIDDEN, DECODER }

    companion object {
        private const val NO_CONNECTION_NOTIFICATION_ID = 5340682

        // Fresh URLs fail within seconds of the reload; a 403 later than this is a new expiry.
        private const val FORBIDDEN_RETRY_WINDOW_MS = 60_000L

        // A decoder that can't decode the stream fails again within seconds of the reload.
        private const val DECODER_RETRY_WINDOW_MS = 60_000L

        private const val NETWORK_RETRY_FIRST_DELAY_MS = 2_000L
        private const val NETWORK_RETRY_MAX_DELAY_MS = 10_000L

        /**
         * Which recovery answers an error with [errorCode] and cause chain [error], or null when
         * the error handler's own dispatch keeps it. A 403 reported without an HTTP status (only
         * in the cause message) keeps upstream's IP-blocked dialog and stop.
         */
        @JvmStatic
        internal fun routeOf(errorCode: Int, error: Throwable): Route? = when (errorCode) {
            ERROR_CODE_IO_UNSPECIFIED ->
                if (error.cause?.message?.contains("Response code: 403") == true) {
                    null
                } else {
                    Route.NETWORK
                }

            ERROR_CODE_IO_BAD_HTTP_STATUS ->
                if (isForbidden(error)) Route.FORBIDDEN else Route.NETWORK

            ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
            ERROR_CODE_TIMEOUT,
            ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            ERROR_CODE_UNSPECIFIED -> Route.NETWORK

            ERROR_CODE_DECODING_FAILED -> Route.DECODER

            else -> null
        }

        /**
         * How long to wait before reloading after [previousFailures] network failures in a row:
         * nothing the first time, then 2, 4, 8 and from there on 10 seconds.
         */
        @JvmStatic
        fun networkRetryDelayMs(previousFailures: Int): Long {
            if (previousFailures <= 0) {
                return 0L
            }
            val doublings = (previousFailures - 1).coerceAtMost(30)
            return (NETWORK_RETRY_FIRST_DELAY_MS shl doublings)
                .coerceAtMost(NETWORK_RETRY_MAX_DELAY_MS)
        }

        /**
         * Whether playback failed because the device has no internet connection: the server
         * could not be resolved, reached or answered in time. A server that answers with an
         * error is not a missing connection.
         */
        @JvmStatic
        fun isNoConnection(error: Throwable): Boolean {
            if (error is PlaybackException && (
                    error.errorCode == ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                        || error.errorCode == ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                    )
            ) {
                return true
            }
            return error.hasAssignableCause(
                UnknownHostException::class.java,
                ConnectException::class.java,
                NoRouteToHostException::class.java,
                SocketTimeoutException::class.java
            )
        }

        /**
         * The message a media session (the car, the system media controls) shows while the
         * player holds [error].
         */
        @JvmStatic
        fun mediaSessionErrorMessage(context: Context, error: Throwable): String =
            context.getString(
                if (isNoConnection(error)) R.string.no_internet_connection
                else R.string.player_stream_failure
            )

        /** Whether the server refused the media request with 403 Forbidden. */
        @JvmStatic
        fun isForbidden(error: Throwable): Boolean {
            var current: Throwable? = error
            while (current != null) {
                if (current is InvalidResponseCodeException && current.responseCode == 403) {
                    return true
                }
                current = current.cause
            }
            return false
        }
    }
}

/**
 * Allows one recovery per queue item within [windowMs]: when the same item fails again that soon
 * after a recovery, recovering again would only loop.
 */
internal class OneRetryPerItem(private val windowMs: Long) {
    // When each item last recovered. Only entries still inside the window are kept, so the map
    // stays as small as the items that failed in the last [windowMs].
    private val retriedAtMs = HashMap<String, Long>()

    /** @return whether to recover from this failure of [itemUuid] at [nowMs] */
    fun tryRetry(itemUuid: String?, nowMs: Long): Boolean {
        retriedAtMs.values.removeAll { nowMs - it >= windowMs }
        if (itemUuid == null) {
            return true
        }
        if (itemUuid in retriedAtMs) {
            return false
        }
        retriedAtMs[itemUuid] = nowMs
        return true
    }
}
