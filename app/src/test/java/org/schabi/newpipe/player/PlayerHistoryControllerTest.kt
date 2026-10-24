package org.schabi.newpipe.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.player.mediaitem.PlayerMediaItem
import java.net.UnknownHostException

class PlayerHistoryControllerTest {
    private val item = PlayerMediaItem(
        uuid = "uuid", serviceId = 0, url = "https://www.youtube.com/watch?v=aqz-KE-bpKQ",
        title = "Big Buck Bunny", uploader = "Blender", uploaderUrl = null, duration = 634,
        thumbnailUrl = null, streamType = StreamType.VIDEO_STREAM
    )

    @Test
    fun aLoadedItemSavesItsPosition() {
        assertTrue(PlayerHistoryController.hasPlayedPosition(item))
    }

    // Offline, the failed item reports position 0: saving it restarted the video from the
    // beginning once the network came back, and reset its watch progress.
    @Test
    fun anItemThatFailedToLoadKeepsTheSavedPosition() {
        val offline = item.withErrors(listOf(Exception(UnknownHostException("www.youtube.com"))))
        assertFalse(PlayerHistoryController.hasPlayedPosition(offline))
    }
}
