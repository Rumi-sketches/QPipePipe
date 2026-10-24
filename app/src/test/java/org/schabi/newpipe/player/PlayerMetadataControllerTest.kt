package org.schabi.newpipe.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.player.mediasource.FailedMediaSource.MediaSourceResolutionException
import org.schabi.newpipe.player.mediasource.FailedMediaSource.StreamInfoLoadException
import java.net.UnknownHostException

class PlayerMetadataControllerTest {
    @Test
    fun sourceErrorsAreReportedByTheMetadata() {
        val extraction = StreamInfoLoadException(RuntimeException("extraction failed"))
        val resolution = MediaSourceResolutionException("no streams")
        assertEquals(
            listOf(extraction, resolution),
            PlayerMetadataController.errorsToReport(listOf(extraction, resolution))
        )
    }

    // A failed load without network is reported (and retried) by the player error handler: the
    // metadata reporting it too notified once more on every retry.
    @Test
    fun networkErrorsAreLeftToThePlayerErrorHandler() {
        val offline = Exception(UnknownHostException("Unable to resolve host \"www.youtube.com\""))
        assertTrue(PlayerMetadataController.errorsToReport(listOf(offline)).isEmpty())
    }
}
