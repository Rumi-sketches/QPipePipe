package org.schabi.newpipe.qpipepipe.player

import org.schabi.newpipe.player.playqueue.PlayQueue

/**
 * QPipePipe: "play next" with several videos (the multiple selection) puts all of them right
 * after the current one, in their order. Upstream moved only the last appended item, which is
 * enough for the single video its own entries send.
 */
object EnqueueNext {
    /** Moves the [count] items just appended to [queue] right after [currentIndex]. */
    @JvmStatic
    fun moveAppendedAfter(queue: PlayQueue, count: Int, currentIndex: Int) {
        // the append may drop an auto-queued tail, so count back from the end
        val firstAppended = queue.size() - count
        for (i in 0 until count) {
            queue.move(firstAppended + i, currentIndex + 1 + i)
        }
    }
}
