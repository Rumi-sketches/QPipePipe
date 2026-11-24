package org.schabi.newpipe.qpipepipe.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.player.playqueue.SinglePlayQueue

/** When the video page asks before replacing the queue that is playing. */
class QueueReplacementTest {
    private fun url(n: Int) = "https://www.youtube.com/watch?v=video$n"

    private fun queueOf(vararg videos: Int): PlayQueue = SinglePlayQueue(
        videos.map {
            StreamInfoItem(0, url(it), "video $it", StreamType.VIDEO_STREAM)
                .apply { uploaderName = "uploader" }
        },
        0
    )

    @Test
    fun opening_another_video_while_a_queue_plays_asks() {
        assertTrue(QueueReplacement.asksBeforeLoading(queueOf(1, 2), true, url(3)))
    }

    @Test
    fun opening_the_video_that_plays_does_not_ask() {
        assertFalse(QueueReplacement.asksBeforeLoading(queueOf(1, 2), true, url(1)))
    }

    @Test
    fun a_single_video_or_a_stopped_player_is_replaced_without_asking() {
        assertFalse(QueueReplacement.asksBeforeLoading(queueOf(1), true, url(3)))
        assertFalse(QueueReplacement.asksBeforeLoading(queueOf(1, 2), false, url(3)))
        assertFalse(QueueReplacement.asksBeforeLoading(null, true, url(3)))
    }

    @Test
    fun playing_another_queue_asks_and_the_same_queue_does_not() {
        val active = queueOf(1, 2)
        assertTrue(QueueReplacement.asksBeforePlaying(active, true, queueOf(3)))
        assertFalse(QueueReplacement.asksBeforePlaying(active, true, active))
        assertFalse(QueueReplacement.asksBeforePlaying(active, false, queueOf(3)))
        assertFalse(QueueReplacement.asksBeforePlaying(queueOf(1), true, queueOf(3)))
    }
}
