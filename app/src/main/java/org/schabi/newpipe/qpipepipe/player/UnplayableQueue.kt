package org.schabi.newpipe.qpipepipe.player

import android.widget.Toast
import org.schabi.newpipe.R
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.RepeatMode
import org.schabi.newpipe.player.mediaitem.PlayerMediaItem
import org.schabi.newpipe.player.mediasource.FailedMediaSource.FailedMediaSourceException
import org.schabi.newpipe.player.playqueue.PlayQueue

/**
 * Keeps a queue of videos that can't be loaded (extraction failed, video removed) from looping.
 *
 * Upstream gives such an item a FailedMediaSource that plays 2 seconds of silence and moves on,
 * and reports it with a toast and a bug report notification every time the player lands on it.
 * With "repeat all" and only broken items, that went on forever, an alert every 2 seconds. Now:
 *
 *  * each broken item is reported once per queue;
 *  * once a whole round of a repeat-all queue found only broken items, playback pauses and a
 *    single message says that none of them can be played.
 *
 * An item that failed for a missing connection is not broken: [PlaybackRecovery] owns it.
 * The player calls [onLanding] each time it lands on an item.
 */
class UnplayableQueue(
    private val queue: () -> PlayQueue?,
    private val repeatsAll: () -> Boolean,
    private val stop: () -> Unit,
) {
    private var queueSeen: PlayQueue? = null
    private val reported = HashSet<String>()
    private val brokenThisRound = HashSet<String>()

    /**
     * The player landed on [item].
     *
     * @return whether the item's load errors may be reported: false when they were already
     * reported in this queue, or when the queue stops instead
     */
    fun onLanding(item: PlayerMediaItem): Boolean {
        val current = queue() ?: return true
        if (current !== queueSeen) {
            queueSeen = current
            reported.clear()
            brokenThisRound.clear()
        }
        if (item.errors.isEmpty()) {
            // Something loaded: a new round starts. The placeholder of an item still loading has
            // no stream info and does not count.
            if (item.maybeStreamInfo.isPresent) {
                brokenThisRound.clear()
            }
            return true
        }
        if (item.errors.none { it is FailedMediaSourceException }) {
            return true // no network: PlaybackRecovery reports and retries it
        }

        brokenThisRound.add(item.uuid)
        val firstReport = reported.add(item.uuid)
        if (repeatsAll() && current.isComplete &&
            current.streams.all { it.uuid in brokenThisRound }
        ) {
            brokenThisRound.clear() // pressing play again starts a new round
            stop()
            return false
        }
        // The item is skipped by the FailedMediaSource silence, as upstream does. A visible
        // reason or a countdown before the skip belongs here.
        return firstReport
    }

    companion object {
        @JvmStatic
        fun forPlayer(player: Player) = UnplayableQueue(
            queue = { player.playQueue },
            repeatsAll = { player.repeatMode == RepeatMode.ALL },
            stop = {
                player.pause()
                Toast.makeText(player.context, R.string.queue_unplayable, Toast.LENGTH_LONG)
                    .show()
            },
        )
    }
}
