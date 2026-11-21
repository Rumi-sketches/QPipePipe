package org.schabi.newpipe.player

import com.google.android.exoplayer2.C
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerRecoveryPositionTest {
    @Test
    fun keepsThePositionWhenTheDurationIsUnknown() {
        // A source being rebuilt or failed reports C.TIME_UNSET: the video must not restart at 0
        assertEquals(183_000L, Player.recoveryPositionOf(183_000, C.TIME_UNSET))
    }

    @Test
    fun capsThePositionAtAKnownDuration() {
        assertEquals(567_000L, Player.recoveryPositionOf(570_000, 567_000))
        assertEquals(183_000L, Player.recoveryPositionOf(183_000, 567_000))
    }

    @Test
    fun nothingToResumeAtTheStart() {
        assertEquals(0L, Player.recoveryPositionOf(0, 567_000))
        assertEquals(0L, Player.recoveryPositionOf(C.TIME_UNSET, C.TIME_UNSET))
    }
}
