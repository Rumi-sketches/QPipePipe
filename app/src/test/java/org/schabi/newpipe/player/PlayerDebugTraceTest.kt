package org.schabi.newpipe.player

import com.google.android.exoplayer2.C
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerDebugTraceTest {
    @Test
    fun timeShowsMinutesSecondsAndMillis() {
        assertEquals("3:03.250 (183250 ms)", PlayerDebugTrace.time(183_250))
        assertEquals("0:00.000 (0 ms)", PlayerDebugTrace.time(0))
    }

    @Test
    fun unknownTimeIsNamed() {
        assertEquals("unknown", PlayerDebugTrace.time(C.TIME_UNSET))
    }
}
