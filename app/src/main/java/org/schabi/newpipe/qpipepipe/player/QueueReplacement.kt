package org.schabi.newpipe.qpipepipe.player

import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import org.schabi.newpipe.R
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.SinglePlayQueue
import org.schabi.newpipe.qpipepipe.QPipePipePreferences
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.SparseItemUtil

/**
 * Asks before the video page replaces a queue of several videos that is playing: Play now,
 * Enqueue next or Enqueue (FORK.md, "Clear-queue confirmation with enqueue options").
 *
 * The video page calls [beforeLoading] when the user opens another video, and [beforePlaying]
 * when a player is about to play the queue on screen. Play now chosen while loading is
 * remembered in [confirmed], so the video plays once loaded without asking again.
 */
class QueueReplacement(
    private val fragment: Fragment,
    private val player: () -> Player?,
) {
    companion object {
        /** Whether opening the video at [newUrl] would replace [active], which is playing. */
        @JvmStatic
        fun asksBeforeLoading(active: PlayQueue?, playing: Boolean, newUrl: String?): Boolean {
            val activeItem = active?.item ?: return false
            return playing && active.size() > 1 && activeItem.url != newUrl
        }

        /** Whether playing [queue] would replace [active], which is playing. */
        @JvmStatic
        fun asksBeforePlaying(active: PlayQueue?, playing: Boolean, queue: PlayQueue?): Boolean =
            playing && active != null && active.size() > 1 && active != queue
    }

    /** The user chose Play now for the video being loaded. */
    var confirmed = false
        private set

    /** Forgets a Play now that will not lead to playback (the load failed). */
    fun forget() {
        confirmed = false
    }

    /**
     * Runs [onAllow], which loads the video at [newUrl], unless the user is asked and picks
     * an enqueue option or cancels.
     */
    fun beforeLoading(newServiceId: Int, newUrl: String?, newQueue: PlayQueue?,
                      onAllow: () -> Unit) {
        confirmed = false
        if (isEnabled() && asksBeforeLoading(activeQueue(), isPlaying(), newUrl)) {
            ask(newServiceId, newUrl, newQueue) {
                confirmed = true
                onAllow()
            }
        } else {
            onAllow()
        }
    }

    /**
     * Runs [onAllow], which plays [queue], unless the user is asked and picks an enqueue option
     * or cancels. [queue] is the one on screen when the user acted: the player pushes its own
     * queue to the page when it connects, possibly while the dialog is open.
     */
    fun beforePlaying(serviceId: Int, url: String?, queue: PlayQueue?, onAllow: () -> Unit) {
        val alreadyConfirmed = confirmed
        confirmed = false
        if (!alreadyConfirmed && isEnabled()
            && asksBeforePlaying(activeQueue(), isPlaying(), queue)) {
            ask(serviceId, url, queue, onAllow)
        } else {
            onAllow()
        }
    }

    private fun isEnabled() =
        QPipePipePreferences.isClearingQueueConfirmationRequired(fragment.requireContext())

    private fun activeQueue(): PlayQueue? = player()?.playQueue

    // The player is idle after the user closed it with the back button
    private fun isPlaying() = player()?.isStopped == false

    private fun ask(serviceId: Int, url: String?, queue: PlayQueue?, onPlayNow: () -> Unit) {
        val items = arrayOf<CharSequence>(
            fragment.getString(R.string.clear_queue_confirmation_play_now),
            fragment.getString(R.string.enqueue_next_stream),
            fragment.getString(R.string.enqueue_stream),
        )
        AlertDialog.Builder(fragment.requireActivity())
            .setTitle(R.string.clear_queue_confirmation_description)
            .setItems(items) { _, which ->
                when (which) {
                    1 -> enqueue(serviceId, url, queue, playNext = true)
                    2 -> enqueue(serviceId, url, queue, playNext = false)
                    else -> onPlayNow()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Enqueues [queue], or the stream at [url] once its info is loaded. */
    private fun enqueue(serviceId: Int, url: String?, queue: PlayQueue?, playNext: Boolean) {
        val activity = fragment.activity ?: return
        val enqueue = { toEnqueue: PlayQueue ->
            if (playNext) {
                NavigationHelper.enqueueNextOnPlayer(activity, toEnqueue)
            } else {
                NavigationHelper.enqueueOnPlayer(activity, toEnqueue)
            }
        }
        when {
            queue != null -> enqueue(queue)
            url != null -> SparseItemUtil.fetchStreamInfoAndSaveToDatabase(activity, serviceId,
                url) { enqueue(SinglePlayQueue(it)) }
        }
    }
}
