package org.schabi.newpipe.qpipepipe.player

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.PlayerUnplayableQpipepipeBinding
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.mediaitem.PlayerMediaItem

/**
 * Upstream skips a video that can't be loaded after 2 seconds of silence, with nothing on screen
 * but an error toast. The video player now holds on it and shows why it can't be played, then
 * moves to the next video after [COUNTDOWN_SECONDS]; the user can move on at once or stop the
 * countdown and stay. The background player has nothing to show it on and skips as upstream.
 */
class UnplayableNotice(private val player: Player) : UnplayableQueue.Notice {
    private val handler = Handler(Looper.getMainLooper())
    private var binding: PlayerUnplayableQpipepipeBinding? = null
    private var item: PlayerMediaItem? = null
    private var secondsLeft = 0

    private val tick = object : Runnable {
        override fun run() {
            secondsLeft--
            if (secondsLeft <= 0) {
                playNext()
            } else {
                showSecondsLeft()
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun show(item: PlayerMediaItem, hasNext: Boolean) {
        if (player.audioPlayerSelected() || player.exoPlayerIsNull()) {
            return
        }
        handler.removeCallbacks(tick)
        this.item = item
        player.pause() // the silence would skip it in 2 seconds

        val views = binding ?: inflate()
        views.unplayableReason.setText(UnplayableReason.of(item.errors))
        views.unplayableNext.isVisible = hasNext
        views.unplayableCancel.isVisible = hasNext
        views.unplayableCountdown.isVisible = hasNext
        followQueuePanel()
        // Its place: play has nothing to play here, and the notice has the way to the next video
        player.binding.playButtons.isInvisible = true
        if (hasNext) {
            secondsLeft = COUNTDOWN_SECONDS
            showSecondsLeft()
            handler.postDelayed(tick, 1000)
        }
    }

    override fun hide() {
        handler.removeCallbacks(tick)
        item = null
        binding?.let {
            it.root.isVisible = false
            player.binding.playButtons.isInvisible = false
        }
    }

    private fun inflate(): PlayerUnplayableQpipepipeBinding {
        // Over the controls, which stay open while the player is paused here, and under the
        // queue panel
        val root = player.binding.root
        val views = PlayerUnplayableQpipepipeBinding.inflate(
            LayoutInflater.from(root.context), root, false
        )
        root.addView(views.root, root.indexOfChild(player.binding.itemsListPanel))
        // The queue panel is see-through: the notice steps aside while it is open
        root.viewTreeObserver.addOnGlobalLayoutListener { followQueuePanel() }
        views.unplayableNext.setOnClickListener { playNext() }
        views.unplayableCancel.setOnClickListener { stopCountdown() }
        binding = views
        return views
    }

    private fun followQueuePanel() {
        binding?.root?.isVisible = item != null && !player.binding.itemsListPanel.isVisible
    }

    private fun showSecondsLeft() {
        binding?.unplayableCountdown?.text =
            player.context.getString(R.string.unplayable_next_in, secondsLeft)
    }

    private fun stopCountdown() {
        handler.removeCallbacks(tick)
        binding?.let {
            it.unplayableCountdown.visibility = View.GONE
            it.unplayableCancel.visibility = View.GONE
        }
    }

    private fun playNext() {
        val broken = item
        hide()
        // The player may have moved on or closed meanwhile: then there is nothing to skip.
        if (broken == null || player.exoPlayerIsNull() ||
            player.playQueue?.item?.uuid != broken.uuid
        ) {
            return
        }
        player.playNext()
        // The pause was the notice's, not the user's: "next video" means it plays
        player.play()
    }

    private companion object {
        const val COUNTDOWN_SECONDS = 7
    }
}
