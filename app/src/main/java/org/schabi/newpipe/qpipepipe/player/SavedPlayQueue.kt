package org.schabi.newpipe.qpipepipe.player

import android.os.Bundle
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.util.SavedState
import org.schabi.newpipe.util.StateSaver
import java.util.Queue

/**
 * Writes the play queue of the video page to a file in the cache, so that it survives the OS
 * killing the app in background (FORK.md, "The play queue survives the OS killing the app").
 * Upstream saves only the url of the open video, and the video came back alone.
 */
class SavedPlayQueue : StateSaver.WriteRead {
    private var savedState: SavedState? = null
    private var toWrite: PlayQueue? = null
    private var read: PlayQueue? = null

    fun save(outState: Bundle, queue: PlayQueue?, changingConfigurations: Boolean) {
        // StateSaver keeps an existing file as it is: drop the old snapshot and write the queue
        // as it is now (current video, added or removed items)
        StateSaver.onDestroy(savedState)
        toWrite = queue?.takeUnless { it.isEmpty }
        savedState = StateSaver.tryToSave(changingConfigurations, null, outState, this)
        toWrite = null
    }

    /** The saved queue, only if it was playing the restored video. */
    fun restore(savedInstanceState: Bundle, serviceId: Int, url: String?): PlayQueue? {
        read = null
        savedState = StateSaver.tryToRestore(savedInstanceState, this)
        val queue = read
        read = null
        val item = queue?.item ?: return null
        return queue.takeIf { item.serviceId == serviceId && item.url == url }
    }

    override fun generateSuffix() = ".queue"

    override fun writeTo(objectsToSave: Queue<Any>) {
        toWrite?.let { objectsToSave.add(it) }
    }

    override fun readFrom(savedObjects: Queue<Any>) {
        read = savedObjects.poll() as? PlayQueue
    }
}
