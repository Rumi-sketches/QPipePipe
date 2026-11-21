package org.schabi.newpipe.qpipepipe.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #33: a download that failed with a network or server error stayed in the queue and the
 * service restarted it at once, forever. These tests drive the backoff the way
 * `DownloadManagerService` does after each error: record the failure, then run the queue, which
 * skips a waiting download until the delayed run.
 */
class DownloadRetryBackoffTest {
    private var now = 0L
    private val backoff = DownloadRetryBackoff { now }
    private val mission = Any()

    /**
     * Starts [mission] again and again, each attempt failing [attemptMs] later with nothing
     * downloaded, and returns how many times it started within [windowMs].
     */
    private fun startsWithin(windowMs: Long, attemptMs: Long = 100): List<Long> {
        val startedAt = mutableListOf<Long>()
        var nextStart = 0L
        while (nextStart < windowMs) {
            now = nextStart
            startedAt += now
            now += attemptMs
            val delay = backoff.onFailure(mission, done = 0, willRetry = true)
            // runMissions right after the error restarts it unless it waits for the delayed run
            nextStart = if (backoff.isWaiting(mission)) now + delay else now
        }
        return startedAt
    }

    @Test
    fun `a download failing on a network error is not restarted in a burst`() {
        val starts = startsWithin(windowMs = 10 * 60_000L)

        // after 2, 4, 8 ... 256 s the ninth start is at 511 s, the next 5 minutes later:
        // 9 starts in 10 minutes, not 6000
        assertEquals(9, starts.size)
        assertEquals(2_100L, starts[1] - starts[0])
        assertEquals(4_100L, starts[2] - starts[1])
    }

    @Test
    fun `the wait doubles up to five minutes`() {
        val delays = (1..12).map { backoff.onFailure(mission, done = 0, willRetry = true) }

        assertEquals(
            listOf(2L, 4, 8, 16, 32, 64, 128, 256, 300, 300, 300, 300).map { it * 1000 },
            delays,
        )
    }

    @Test
    fun `a download waits until its delay is over`() {
        val delay = backoff.onFailure(mission, done = 0, willRetry = true)

        now += delay - 1
        assertTrue(backoff.isWaiting(mission))
        now += 1
        assertFalse(backoff.isWaiting(mission))
    }

    @Test
    fun `progress since the last failure starts the waits over`() {
        repeat(4) { backoff.onFailure(mission, done = 1_000, willRetry = true) }

        assertEquals(2_000L, backoff.onFailure(mission, done = 5_000, willRetry = true))
        assertEquals(4_000L, backoff.onFailure(mission, done = 5_000, willRetry = true))
    }

    @Test
    fun `a network change or start all ends the waits at once`() {
        repeat(4) { backoff.onFailure(mission, done = 0, willRetry = true) }

        assertTrue(backoff.endWaits())
        assertFalse(backoff.isWaiting(mission))
        // the delayed run still restarts it
        assertTrue(backoff.hasFailures())
        assertEquals(2_000L, backoff.onFailure(mission, done = 0, willRetry = true))
    }

    @Test
    fun `ending the waits with nothing waiting says so`() {
        assertFalse(backoff.endWaits())

        val delay = backoff.onFailure(mission, done = 0, willRetry = true)
        now += delay
        assertFalse(backoff.endWaits())
    }

    @Test
    fun `pause all cancels the restart the wait owes`() {
        backoff.onFailure(mission, done = 0, willRetry = true)
        assertTrue(backoff.hasFailures())

        backoff.forget()

        // the delayed run finds nothing to restart
        assertFalse(backoff.hasFailures())
        assertFalse(backoff.isWaiting(mission))
        assertEquals(2_000L, backoff.onFailure(mission, done = 0, willRetry = true))
    }

    @Test
    fun `an error the queue does not retry never waits`() {
        repeat(3) { backoff.onFailure(mission, done = 0, willRetry = true) }

        assertEquals(0L, backoff.onFailure(mission, done = 0, willRetry = false))
        assertFalse(backoff.isWaiting(mission))
        assertEquals(2_000L, backoff.onFailure(mission, done = 0, willRetry = true))
    }

    @Test
    fun `each download has its own waits`() {
        val other = Any()
        repeat(3) { backoff.onFailure(mission, done = 0, willRetry = true) }

        assertEquals(2_000L, backoff.onFailure(other, done = 0, willRetry = true))
        assertTrue(backoff.isWaiting(mission))
    }
}
