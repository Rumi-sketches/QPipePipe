package org.schabi.newpipe.player

import android.net.Uri
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.upstream.HttpDataSource.InvalidResponseCodeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerErrorHandlerTest {
    private fun responseCode(code: Int) = InvalidResponseCodeException(
        code, null, null, emptyMap(), DataSpec(Uri.EMPTY), ByteArray(0)
    )

    // Same nesting as the player reports it (playback exception -> source error -> HTTP error),
    // built from plain exceptions because ExoPlaybackException needs the Android framework.
    private fun sourceError(cause: Exception) = RuntimeException(java.io.IOException(cause))

    @Test
    fun forbiddenIsFoundBehindThePlaybackException() {
        assertTrue(PlayerErrorHandler.isForbidden(sourceError(responseCode(403))))
    }

    @Test
    fun otherHttpStatusesAreNotForbidden() {
        assertFalse(PlayerErrorHandler.isForbidden(sourceError(responseCode(404))))
        assertFalse(PlayerErrorHandler.isForbidden(sourceError(RuntimeException("403"))))
    }

    @Test
    fun firstNetworkFailureReloadsAtOnce() {
        assertEquals(0L, PlayerErrorHandler.networkRetryDelayMs(0))
    }

    @Test
    fun repeatedNetworkFailuresWaitLongerUpToTenSeconds() {
        val delays = (1..6).map { PlayerErrorHandler.networkRetryDelayMs(it) }
        assertEquals(listOf(2_000L, 4_000L, 8_000L, 10_000L, 10_000L, 10_000L), delays)
    }

    @Test
    fun endlessNetworkFailuresNeverOverflowTheDelay() {
        assertEquals(10_000L, PlayerErrorHandler.networkRetryDelayMs(Int.MAX_VALUE))
    }

    @Test
    fun oneRetryPerItemRecoversOnceThenGivesUp() {
        val retry = OneRetryPerItem(60_000L)
        assertTrue(retry.tryRetry("a", 0L))
        assertFalse(retry.tryRetry("a", 59_999L))
    }

    @Test
    fun oneRetryPerItemRecoversAgainAfterTheWindowOrOnAnotherItem() {
        val retry = OneRetryPerItem(60_000L)
        assertTrue(retry.tryRetry("a", 0L))
        assertTrue(retry.tryRetry("a", 60_000L))
        assertTrue(retry.tryRetry("b", 60_001L))
    }

    @Test
    fun oneRetryPerItemAlwaysRecoversAnUnknownItem() {
        val retry = OneRetryPerItem(60_000L)
        assertTrue(retry.tryRetry(null, 0L))
        assertTrue(retry.tryRetry(null, 1L))
    }
}
