package org.schabi.newpipe.qpipepipe.download

import android.os.SystemClock
import java.util.WeakHashMap

/**
 * Spaces out the automatic restarts of a download that failed with a network or server error
 * (issue #33).
 *
 * Upstream keeps such a download in the queue and the service starts it again at once, so on a
 * network without a working internet (captive Wi-Fi, a hotspot without data, DNS failing, a server
 * answering 5xx) it restarted as fast as the error came, until the network went away or the user
 * paused it. Now the queue waits 2, 4, 8 ... seconds, up to [MAX_DELAY_MS], before restarting it;
 * the waits start over once the download made progress. A new network or "start all" ends every
 * wait, and starting one download by hand never waits.
 */
class DownloadRetryBackoff @JvmOverloads constructor(
    private val nowMs: () -> Long = { SystemClock.elapsedRealtime() },
) {
    private class Wait(var failures: Int, var done: Long, var retryAtMs: Long)

    // Weak keys, compared by identity: a deleted or finished download drops out by itself.
    private val waits = WeakHashMap<Any, Wait>()

    /**
     * Records a failure of [mission] after it had downloaded [done] bytes.
     *
     * @param willRetry whether the queue restarts the mission by itself (it stayed enqueued)
     * @return how long the queue waits before restarting it, 0 when it doesn't restart it
     */
    @Synchronized
    fun onFailure(mission: Any, done: Long, willRetry: Boolean): Long {
        if (!willRetry) {
            waits.remove(mission)
            return 0
        }
        val wait = waits[mission]?.takeIf { done <= it.done }
            ?: Wait(0, done, 0).also { waits[mission] = it }
        wait.failures++
        wait.done = done
        val delay = delayMs(wait.failures)
        wait.retryAtMs = nowMs() + delay
        return delay
    }

    /** Whether the queue must not restart [mission] yet. */
    @Synchronized
    fun isWaiting(mission: Any): Boolean = waits[mission]?.let { nowMs() < it.retryAtMs } ?: false

    /**
     * Ends every wait and forgets the failures: the network changed or the user started the
     * downloads.
     *
     * @return whether a download was waiting
     */
    @Synchronized
    fun clear(): Boolean {
        val now = nowMs()
        val anyWaiting = waits.values.any { now < it.retryAtMs }
        waits.clear()
        return anyWaiting
    }

    companion object {
        const val FIRST_DELAY_MS = 2_000L
        const val MAX_DELAY_MS = 300_000L

        /** The wait after the [failures]-th failure in a row: 2 s, doubling up to 5 minutes. */
        @JvmStatic
        fun delayMs(failures: Int): Long =
            (FIRST_DELAY_MS shl (failures - 1).coerceIn(0, 20)).coerceAtMost(MAX_DELAY_MS)
    }
}
