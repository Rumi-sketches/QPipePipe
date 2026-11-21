package org.schabi.newpipe.qpipepipe.player

import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.player.mediasource.FailedMediaSource.StreamInfoLoadException

/** Issue #41: the player says why a queue item can't be played. */
class UnplayableReasonTest {

    private fun reasonFor(cause: Throwable) =
        UnplayableReason.of(listOf(StreamInfoLoadException(ExtractionException(cause))))

    @Test
    fun theExtractorsReasonIsFoundUnderThePlayersWrapping() {
        assertEquals(R.string.private_content_new, reasonFor(PrivateContentException("private")))
        assertEquals(R.string.content_not_available,
            reasonFor(ContentNotAvailableException("This video is unavailable")))
    }

    // What YouTube gives for a removed video today (G6kUfycDI-c): the extractor drops the reason.
    @Test
    fun withoutAReasonTheVideoSimplyCantBePlayed() {
        assertEquals(R.string.unplayable_video,
            reasonFor(ExtractionException("Initial WEB player response is not valid")))
    }
}
