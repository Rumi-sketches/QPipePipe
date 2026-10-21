package org.schabi.newpipe.player

import android.content.Context
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_AUTO_TRANSITION
import com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_INTERNAL
import com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_REMOVE
import com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_SEEK
import com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
import com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_SKIP
import com.google.android.exoplayer2.PlaybackException
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.error.DebugErrorLog
import org.schabi.newpipe.player.playqueue.PlayQueue
import java.util.Locale

/**
 * Debug builds only: writes to [DebugErrorLog] the playback events that move the position without
 * the user seeing an error, so that a video starting over can be traced to the code path that did
 * it. Release builds skip every call (`BuildConfig.DEBUG`).
 */
object PlayerDebugTrace {
    /** Jumps back shorter than this are ordinary rewinds (the car and notification use 10 s). */
    private const val REWIND_THRESHOLD_MS = 30_000L

    /** [Player.reloadPlayQueueManager] rebuilds the source of the current video. */
    @JvmStatic
    fun onReload(player: Player) {
        if (!BuildConfig.DEBUG || player.exoPlayerIsNull()) {
            return
        }
        val position = player.simpleExoPlayer.currentPosition
        // The first load of a queue has nothing to lose: not worth an entry
        if (position <= 0 && recoveryOf(player) == PlayQueue.RECOVERY_UNSET) {
            return
        }
        record(player, "reload of the current video", position, player.simpleExoPlayer.duration)
    }

    /**
     * Every ExoPlayer error, recovered or not, with the position it happened at. Errors that show a
     * notification also get their usual entry; this one adds where playback was.
     */
    @JvmStatic
    fun onPlayerError(
        player: Player,
        error: PlaybackException,
        positionMs: Long,
        durationMs: Long,
        catchable: Boolean,
    ) {
        if (!BuildConfig.DEBUG) {
            return
        }
        record(
            player,
            "error ${error.errorCodeName} (${if (catchable) "silent" else "notified"}): $error"
                + (error.cause?.let { " / cause: $it" } ?: ""),
            positionMs, durationMs
        )
    }

    /**
     * A jump back of at least [REWIND_THRESHOLD_MS] inside the same video. A video that has ended
     * goes back to its start on replay: not worth an entry.
     */
    @JvmStatic
    fun onPositionDiscontinuity(
        player: Player,
        oldIndex: Int,
        oldPositionMs: Long,
        newIndex: Int,
        newPositionMs: Long,
        reason: Int,
    ) {
        if (!BuildConfig.DEBUG || oldIndex != newIndex
            || oldPositionMs - newPositionMs < REWIND_THRESHOLD_MS
            || player.currentState.isCompleted
        ) {
            return
        }
        record(
            player,
            "jump back from ${time(oldPositionMs)} to ${time(newPositionMs)} (${reasonName(reason)})",
            newPositionMs, durationOf(player)
        )
    }

    /**
     * After a reload the video that was already playing starts again from its default position (0
     * for a video). A video just opened starts there too: callers skip that case.
     */
    @JvmStatic
    fun onStartFromDefault(player: Player) {
        if (!BuildConfig.DEBUG || player.exoPlayerIsNull()) {
            return
        }
        record(
            player, "video restarted from the default position after a reload: no recovery position",
            player.simpleExoPlayer.currentPosition, player.simpleExoPlayer.duration
        )
    }

    /**
     * The video screen takes the player's queue in place of the one it was showing (it does on
     * every connection to the player): what the user plays from the screen afterwards is the
     * player's queue.
     */
    @JvmStatic
    fun onScreenQueueReplaced(context: Context, shown: PlayQueue?, playerQueue: PlayQueue) {
        // The same videos at another index: the player has moved on while the screen was away
        if (!BuildConfig.DEBUG || shown == null
            || shown.streams.map { it.url } == playerQueue.streams.map { it.url }
        ) {
            return
        }
        DebugErrorLog.recordPlayback(
            context, "video screen queue replaced by the player's",
            listOf("Screen: " + describe(shown), "Player: " + describe(playerQueue))
        )
    }

    private fun describe(queue: PlayQueue): String =
        "${queue.index + 1}/${queue.size()} " + (queue.item?.let { "${it.title} (${it.url})" } ?: "none")

    private fun record(player: Player, event: String, positionMs: Long, durationMs: Long) {
        val playQueue = player.playQueue
        val item = playQueue?.item
        val recovery = recoveryOf(player)
        DebugErrorLog.recordPlayback(
            player.context, event,
            listOf(
                "Video: " + (item?.let { "${it.title} (${it.url})" } ?: "none"),
                "Queue: " + (playQueue?.let { "${it.index + 1}/${it.size()}" } ?: "none"),
                "Position: ${time(positionMs)} of ${time(durationMs)}",
                "Recovery: " + if (recovery == PlayQueue.RECOVERY_UNSET) "unset" else time(recovery),
                "State: ${player.currentState}",
            )
        )
    }

    private fun recoveryOf(player: Player): Long {
        val playQueue = player.playQueue ?: return PlayQueue.RECOVERY_UNSET
        return playQueue.item?.let { playQueue.getRecoveryPosition(it) } ?: PlayQueue.RECOVERY_UNSET
    }

    private fun durationOf(player: Player): Long =
        if (player.exoPlayerIsNull()) C.TIME_UNSET else player.simpleExoPlayer.duration

    /** `m:ss.SSS (ms)`, or `unknown` for [C.TIME_UNSET] and other negative values. */
    fun time(ms: Long): String {
        if (ms < 0) {
            return "unknown"
        }
        return String.format(Locale.ROOT, "%d:%02d.%03d (%d ms)",
            ms / 60_000, ms / 1000 % 60, ms % 1000, ms)
    }

    private fun reasonName(reason: Int): String = when (reason) {
        DISCONTINUITY_REASON_AUTO_TRANSITION -> "auto transition"
        DISCONTINUITY_REASON_SEEK -> "seek"
        DISCONTINUITY_REASON_SEEK_ADJUSTMENT -> "seek adjustment"
        DISCONTINUITY_REASON_SKIP -> "skip"
        DISCONTINUITY_REASON_REMOVE -> "remove"
        DISCONTINUITY_REASON_INTERNAL -> "internal"
        else -> "reason $reason"
    }
}
