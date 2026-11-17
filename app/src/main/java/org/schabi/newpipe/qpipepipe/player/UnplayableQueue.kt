package org.schabi.newpipe.qpipepipe.player

import android.content.Context
import android.widget.Toast
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.exceptions.ExtractionException
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
 *  * the player holds on a broken item and shows why it can't be played, with a countdown to
 *    the next item when there is one ([UnplayableNotice]);
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
    private val notice: Notice,
) {
    /** What the player shows about the broken item it is on. */
    interface Notice {
        /** Shows why [item] can't be played, counting down to the next item if [hasNext]. */
        fun show(item: PlayerMediaItem, hasNext: Boolean)

        fun hide()
    }

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
            notice.hide()
            return true
        }
        if (item.errors.none { it is FailedMediaSourceException }) {
            notice.hide()
            return true // no network: PlaybackRecovery reports and retries it
        }

        brokenThisRound.add(item.uuid)
        val firstReport = reported.add(item.uuid)
        if (repeatsAll() && current.isComplete &&
            current.streams.all { it.uuid in brokenThisRound }
        ) {
            brokenThisRound.clear() // pressing play again starts a new round
            notice.hide()
            stop()
            return false
        }
        val index = current.indexOf(item)
        notice.show(item, hasNext = repeatsAll() || !current.isComplete ||
            (index >= 0 && index < current.size() - 1))
        return firstReport
    }

    companion object {
        /**
         * Whether loading a queue item failed because of the item itself (removed, private,
         * extraction broken), so the player holds on it as broken; otherwise the load is retried
         * and [PlaybackRecovery] handles it. Upstream's rule (an [ExtractionException] or an
         * [IllegalStateException] from building the source), except for a missing connection:
         * the extractor wraps a network error of a later request (player metadata, signature
         * decoding) in a ParsingException, and that video is not broken.
         *
         * Those requests go to other servers than the video page (the signature decoder is
         * `api.pipepipe.dev`), so a network error alone doesn't say the connection is missing:
         * with the phone online it means that server is down, and retrying would loop forever on
         * "No internet connection". Only when Android sees no internet is it a missing connection.
         */
        @JvmStatic
        fun isBrokenSource(context: Context, error: Throwable): Boolean =
            isBrokenSource(error) { ValidatedInternet.isAvailable(context) }

        @JvmStatic
        internal fun isBrokenSource(error: Throwable, isOnline: () -> Boolean): Boolean =
            (error is ExtractionException || error is IllegalStateException) &&
                !(PlaybackRecovery.isNoConnection(error) && !isOnline())

        /**
         * Whether the video page still starts [queue] when its first video can't be loaded.
         * Upstream showed only the page's error then, and the rest of a playlist never played:
         * now the player gets the queue, holds on the broken video with its reason and moves on.
         * A single video keeps the page's error, which already says what went wrong.
         */
        @JvmStatic
        fun playsDespiteLoadError(queue: PlayQueue?): Boolean = queue != null && queue.size() > 1

        @JvmStatic
        fun forPlayer(player: Player) = UnplayableQueue(
            queue = { player.playQueue },
            repeatsAll = { player.repeatMode == RepeatMode.ALL },
            stop = {
                player.pause()
                Toast.makeText(player.context, R.string.queue_unplayable, Toast.LENGTH_LONG)
                    .show()
            },
            notice = UnplayableNotice(player),
        )
    }
}
