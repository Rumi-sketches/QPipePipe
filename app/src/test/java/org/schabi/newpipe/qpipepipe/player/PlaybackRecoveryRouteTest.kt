package org.schabi.newpipe.qpipepipe.player

import android.net.Uri
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.upstream.HttpDataSource.InvalidResponseCodeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.schabi.newpipe.qpipepipe.player.PlaybackRecovery.Route
import java.io.IOException

/**
 * Which errors the recovery takes over from the player error handler's own dispatch: the same
 * branches of `PlayerErrorHandler.onPlayerError` the fork answered before it moved here.
 */
class PlaybackRecoveryRouteTest {
    private fun responseCode(code: Int) = InvalidResponseCodeException(
        code, null, null, emptyMap(), DataSpec(Uri.EMPTY), ByteArray(0)
    )

    // Same nesting as the player reports it, built from plain exceptions because
    // PlaybackException needs the Android framework.
    private fun sourceError(cause: Exception) = RuntimeException(IOException(cause))

    private fun route(errorCode: Int, error: Throwable = RuntimeException()) =
        PlaybackRecovery.routeOf(errorCode, error)

    @Test
    fun networkFailuresGoToTheNetworkRetries() {
        listOf(
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
            PlaybackException.ERROR_CODE_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_UNSPECIFIED,
        ).forEach { assertEquals("code $it", Route.NETWORK, route(it)) }
    }

    @Test
    fun badHttpStatusIsAForbiddenRecoveryOnlyFor403() {
        assertEquals(
            Route.FORBIDDEN,
            route(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, sourceError(responseCode(403)))
        )
        assertEquals(
            Route.NETWORK,
            route(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, sourceError(responseCode(404)))
        )
    }

    // A 403 known only from the cause message keeps upstream's IP-blocked dialog and stop. Only
    // the direct cause counts, as in upstream's branch: the error's own message is not read.
    @Test
    fun unspecifiedIoWith403InTheCauseMessageStaysWithTheHandler() {
        val error = RuntimeException("Source error", IOException("Response code: 403"))
        assertNull(route(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, error))
        assertEquals(
            Route.NETWORK,
            route(
                PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                RuntimeException("Response code: 403", IOException("Source error"))
            )
        )
    }

    @Test
    fun decodingFailuresGoToTheDecoderRecovery() {
        assertEquals(Route.DECODER, route(PlaybackException.ERROR_CODE_DECODING_FAILED))
    }

    @Test
    fun otherErrorsStayWithTheHandler() {
        listOf(
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
            PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
            PlaybackException.ERROR_CODE_REMOTE_ERROR,
        ).forEach { assertNull("code $it", route(it)) }
    }
}
