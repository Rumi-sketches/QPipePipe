package org.schabi.newpipe.player

import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW
import com.google.android.exoplayer2.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
import com.google.android.exoplayer2.PlaybackException.ERROR_CODE_DECODING_FAILED
import com.google.android.exoplayer2.PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK
import com.google.android.exoplayer2.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
import com.google.android.exoplayer2.PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED
import com.google.android.exoplayer2.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
import com.google.android.exoplayer2.PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE
import com.google.android.exoplayer2.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
import com.google.android.exoplayer2.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
import com.google.android.exoplayer2.PlaybackException.ERROR_CODE_IO_NO_PERMISSION
import com.google.android.exoplayer2.PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
import com.google.android.exoplayer2.PlaybackException.ERROR_CODE_IO_UNSPECIFIED
import com.google.android.exoplayer2.PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED
import com.google.android.exoplayer2.PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
import com.google.android.exoplayer2.PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED
import com.google.android.exoplayer2.PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED
import com.google.android.exoplayer2.PlaybackException.ERROR_CODE_TIMEOUT
import com.google.android.exoplayer2.PlaybackException.ERROR_CODE_UNSPECIFIED
import com.google.android.exoplayer2.upstream.HttpDataSource.InvalidResponseCodeException
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.services.youtube.sabr.exception.SabrAttestationException
import org.schabi.newpipe.player.datasource.SabrLogicException
import org.schabi.newpipe.util.InfoCache
import java.util.Locale

/**
 * The playback error policy of a [Player].
 *
 * Owns what happens when ExoPlayer reports a [PlaybackException]: which errors can be recovered
 * and how, when a recovery cooldown applies, when the user is told about the failure, and what
 * [PlayerError] is pushed to the player listeners.
 *
 * [Player] only keeps the primitives this handler cannot own (the playback actions to recover
 * with, the notification, and the listener fan-out), so it no longer holds any error policy.
 */
class PlayerErrorHandler(private val player: Player) {

    // Cooldown between automatic recoveries from a surface-released decoder-init failure, so a
    // genuinely broken surface can't loop recover->fail forever.
    private var lastSurfaceErrorRecoveryMs = 0L

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
    private val delayedReload = Runnable { player.reloadPlayQueueManager() }
    private var reloadPending = false

    /**
     * Process exceptions produced by [com.google.android.exoplayer2.ExoPlayer].
     *
     * There are multiple types of errors:
     *
     *  * [ERROR_CODE_BEHIND_LIVE_WINDOW]: if the playback on livestreams is lagged too far behind
     *    the current playable window, seek to the latest timestamp and restart the playback. This
     *    error is *catchable*.
     *  * From [ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE] to
     *    [ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED]: if the stream source is validated by the
     *    extractor but not recognized by the player, try to recover playback by signalling an
     *    error on the play queue.
     *  * For [ERROR_CODE_TIMEOUT], [ERROR_CODE_IO_UNSPECIFIED] and
     *    [ERROR_CODE_IO_NETWORK_CONNECTION_FAILED]: keep the recovery record and keep the player
     *    at the current state until it is ready to play by restarting the media source manager.
     *    When the restart fails again, the next ones are delayed and *catchable*, see
     *    [recoverFromNetworkError].
     *  * [ERROR_CODE_DECODING_FAILED]: rebuild the source once per item, which gets a new decoder,
     *    see [recoverFromDecoderFailure]. This recovery is *catchable*.
     *  * On any ExoPlayer specific issue internal to its device interaction, such as
     *    [ERROR_CODE_DECODER_INIT_FAILED]: terminate the playback.
     *  * For any other unspecified issue internal: set a recovery and try to restart the playback.
     *
     * For any error above that is **not** explicitly **catchable**, the player will create a
     * notification so users are aware.
     *
     * Any error code not explicitly covered here is either unrelated to the app's use case (e.g.
     * DRM) or not recoverable (e.g. a renderer error). In both cases, the player should shutdown.
     */
    fun onPlayerError(error: PlaybackException) {
        Log.e(Player.TAG, "ExoPlayer - onPlayerError() called with:", error)
        val positionMs = player.simpleExoPlayer.currentPosition
        val durationMs = player.simpleExoPlayer.duration

        player.saveStreamProgressState()
        var isCatchableException = false

        if (containsTerminalSabrException(error)) {
            // Attestation retries are handled inside the media bridge. An attestation exception
            // reaching the player has exhausted those recovery paths. SABR logic exceptions
            // describe broken source invariants, so rebuilding the same source cannot recover.
            player.onPlaybackShutdown()
        } else {
            when (error.errorCode) {
                ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                    isCatchableException = true
                    player.simpleExoPlayer.seekToDefaultPosition()
                    player.simpleExoPlayer.prepare()
                    // Inform the user that we are reloading the stream by
                    // switching to the buffering state
                    player.onBuffering()
                }

                ERROR_CODE_IO_FILE_NOT_FOUND,
                ERROR_CODE_IO_NO_PERMISSION,
                ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
                ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
                ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                ERROR_CODE_PARSING_MANIFEST_MALFORMED,
                ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> {
                    // Source errors, signal on the play queue and move on:
                    if (!player.exoPlayerIsNull() && player.playQueue != null) {
                        player.onBufferingFailed()
                    }
                }

                ERROR_CODE_IO_UNSPECIFIED -> {
                    val causeMessage = error.cause?.message
                    if (causeMessage != null && causeMessage.contains("Response code: 403")) {
                        showIpBlocked()
                        player.onPlaybackShutdown()
                    } else {
                        isCatchableException = recoverFromNetworkError()
                    }
                }

                ERROR_CODE_IO_BAD_HTTP_STATUS -> if (!isForbidden(error)) {
                    isCatchableException = recoverFromNetworkError()
                } else if (recoverFromForbidden()) {
                    // Expected after a long pause or a network change: reload quietly, without
                    // the error notification and without the detail page closing the player.
                    isCatchableException = true
                } else {
                    showIpBlocked()
                    player.onPlaybackShutdown()
                }

                ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
                ERROR_CODE_TIMEOUT,
                ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                ERROR_CODE_UNSPECIFIED -> isCatchableException = recoverFromNetworkError()

                // The decoder failed in the middle of the stream. A hardware decoder fails like this
                // when the system takes it back or its media process restarts (MediaCodec
                // DEAD_OBJECT), which says nothing about the stream: a new source gets a new
                // decoder and resumes at the same position.
                ERROR_CODE_DECODING_FAILED -> if (recoverFromDecoderFailure()) {
                    isCatchableException = true
                } else {
                    player.onPlaybackShutdown()
                }

                ERROR_CODE_DECODER_INIT_FAILED -> {
                    val surfaceReleased = isSurfaceReleasedError(error)
                    if (surfaceReleased && System.currentTimeMillis() - lastSurfaceErrorRecoveryMs
                        > SURFACE_ERROR_RECOVERY_COOLDOWN_MS
                    ) {
                        // The decoder died because the video surface was released under it
                        // (screen off / surface lifecycle race), NOT because the device lacks a
                        // decoder. Recover like a stream error instead of killing playback with
                        // the misleading "no hardware decoder, use VLC" dialog. Cooldown-bounded
                        // so a genuinely broken surface still falls through to shutdown below.
                        lastSurfaceErrorRecoveryMs = System.currentTimeMillis()
                        player.setRecovery()
                        player.reloadPlayQueueManager()
                    } else {
                        // Only show the dialog when a hosting activity exists AND the failure is
                        // really about decoding capability. getParentActivity() is null in the
                        // background/popup player, and AlertDialog.Builder(null) NPEs -> the app
                        // crashed on a decoder-init failure while backgrounded. The error
                        // notification below still surfaces it.
                        val parentActivity = player.getParentActivity()
                        if (parentActivity != null && !surfaceReleased) {
                            AlertDialog.Builder(parentActivity)
                                .setTitle(R.string.decoder_init_failure)
                                .setMessage(R.string.unable_to_decode_summary)
                                .setPositiveButton(R.string.ok) { _, _ -> }
                                .show()
                        }
                        player.onPlaybackShutdown()
                    }
                }

                // API, remote and renderer errors belong here:
                else -> player.onPlaybackShutdown()
            }
        }

        PlayerDebugTrace.onPlayerError(player, error, positionMs, durationMs, isCatchableException)

        val playerError = toPlayerError(error)
        if (!isCatchableException) {
            showMediaCodecWorkaroundHint(error)
            createErrorNotification(playerError)
        }

        player.listeners.onPlayerError(playerError, isCatchableException)
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
        val item = player.playQueue?.item ?: player.currentItem
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
    }

    /**
     * The user asked to play while a reload waits out a network failure: the failed source can't
     * play, so reload now instead of letting the press do nothing until the wait is over.
     */
    fun reloadIfWaiting() {
        if (reloadPending) {
            player.reloadPlayQueueManager()
        }
    }

    /** Drop a delayed reload: another reload replaced it, or the player is going away. */
    fun cancelDelayedReload() {
        retryHandler.removeCallbacks(delayedReload)
        reloadPending = false
    }

    private fun reloadNow() {
        player.setRecovery()
        player.reloadPlayQueueManager()
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
        val item = player.currentItem ?: player.playQueue?.item
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
        val item = player.currentItem ?: player.playQueue?.item
        if (!decoderRetry.tryRetry(item?.uuid, System.currentTimeMillis())) {
            return false
        }
        reloadNow()
        return true
    }

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

    private fun containsTerminalSabrException(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is SabrAttestationException || current is SabrLogicException) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private fun showMediaCodecWorkaroundHint(error: PlaybackException) {
        if (error.errorCode != ERROR_CODE_DECODING_FAILED
            && error.errorCode != ERROR_CODE_FAILED_RUNTIME_CHECK
        ) {
            return
        }
        try {
            val stackTrace = Log.getStackTraceString(error)
            val hasSetOutputSurface = stackTrace.contains("setOutputSurface")
            val hasAsyncCodecAdapter = stackTrace.contains("AsynchronousMediaCodecAdapter")
                || stackTrace.contains("AsynchronousMediaCodecBufferEnqueuer")
            val message: Int
            val context = player.getContext()
            val prefs = player.getPrefs()
            if (hasSetOutputSurface && !prefs.getBoolean(
                    context.getString(
                        R.string.always_use_exoplayer_set_output_surface_workaround_key
                    ), false
                )
            ) {
                message = R.string.media_codec_surface_workaround_hint
            } else if (hasAsyncCodecAdapter && !prefs.getBoolean(
                    context.getString(R.string.disable_exoplayer_media_codec_async_queueing_key),
                    false
                )
            ) {
                message = R.string.media_codec_async_workaround_hint
            } else {
                return
            }

            AlertDialog.Builder(player.getParentActivity())
                .setTitle(R.string.media_codec_workaround_hint_title)
                .setMessage(message)
                .setPositiveButton(R.string.ok, null)
                .show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * True when a decoder-init failure was caused by the video surface being released under the
     * codec (screen off / surface lifecycle race) rather than by a missing/unsupported decoder.
     */
    private fun isSurfaceReleasedError(error: PlaybackException): Boolean {
        var cause = error.cause
        var depth = 0
        while (cause != null && depth < 8) {
            val message = cause.message
            if (cause is IllegalArgumentException && message != null
                && message.lowercase(Locale.US).contains("surface")
            ) {
                return true
            }
            cause = cause.cause
            depth++
        }
        return false
    }

    private fun toPlayerError(error: PlaybackException): PlayerError {
        val type = if (error is ExoPlaybackException) {
            when (error.type) {
                ExoPlaybackException.TYPE_SOURCE -> PlayerError.Type.SOURCE
                ExoPlaybackException.TYPE_RENDERER -> PlayerError.Type.RENDERER
                ExoPlaybackException.TYPE_UNEXPECTED -> PlayerError.Type.UNEXPECTED
                ExoPlaybackException.TYPE_REMOTE -> PlayerError.Type.REMOTE
                else -> PlayerError.Type.OTHER
            }
        } else {
            PlayerError.Type.OTHER
        }
        return PlayerError(error.errorCode, error.errorCodeName, type, error)
    }

    private fun createErrorNotification(error: PlayerError) {
        val currentMetadata = player.currentMetadata
        val errorInfo = if (currentMetadata == null) {
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
        ErrorUtil.createNotification(player.getContext(), errorInfo)
    }

    companion object {
        private const val SURFACE_ERROR_RECOVERY_COOLDOWN_MS = 10_000L

        // Fresh URLs fail within seconds of the reload; a 403 later than this is a new expiry.
        private const val FORBIDDEN_RETRY_WINDOW_MS = 60_000L

        // A decoder that can't decode the stream fails again within seconds of the reload.
        private const val DECODER_RETRY_WINDOW_MS = 60_000L

        private const val NETWORK_RETRY_FIRST_DELAY_MS = 2_000L
        private const val NETWORK_RETRY_MAX_DELAY_MS = 10_000L

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
    private var itemUuid: String? = null
    private var retriedAtMs = 0L

    /** @return whether to recover from this failure of [itemUuid] at [nowMs] */
    fun tryRetry(itemUuid: String?, nowMs: Long): Boolean {
        if (itemUuid != null && itemUuid == this.itemUuid && nowMs - retriedAtMs < windowMs) {
            return false
        }
        this.itemUuid = itemUuid
        retriedAtMs = nowMs
        return true
    }
}
