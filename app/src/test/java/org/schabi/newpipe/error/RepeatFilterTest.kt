package org.schabi.newpipe.error

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RepeatFilterTest {
    private val filter = RepeatFilter(60_000)
    private val clock: (Long) -> String = { "t$it" }

    @Test
    fun firstEntryIsWritten() {
        assertEquals("reload body\n", filter.filter("reload", "reload body\n", 0, clock))
    }

    @Test
    fun loopIsCountedAndReportedWithTheNextEntry() {
        // Offline: a reload and its error every 3 s
        for (time in 0L until 30_000L step 3_000L) {
            val reload = filter.filter("reload", "reload body\n", time, clock)
            val error = filter.filter("error", "error body\n", time + 1, clock)
            if (time == 0L) {
                assertEquals("reload body\n", reload)
                assertEquals("error body\n", error)
            } else {
                assertNull(reload)
                assertNull(error)
            }
        }

        assertEquals(
            "Repeated 9 more times until t27000: reload\n"
                + "Repeated 9 more times until t27001: error\n"
                + "back online\n",
            filter.filter("online", "back online\n", 31_000, clock)
        )
        // The counts are reported once
        assertEquals("other\n", filter.filter("other", "other\n", 32_000, clock))
    }

    @Test
    fun sameEntryIsWrittenAgainAfterTheWindow() {
        filter.filter("reload", "reload body\n", 0, clock)
        assertNull(filter.filter("reload", "reload body\n", 59_999, clock))
        assertEquals(
            "Repeated 1 more time until t59999: reload\nreload body\n",
            filter.filter("reload", "reload body\n", 60_000, clock)
        )
    }

    @Test
    fun differentEntriesAreAllWritten() {
        assertEquals("a\n", filter.filter("a", "a\n", 0, clock))
        assertEquals("b\n", filter.filter("b", "b\n", 1, clock))
    }
}
