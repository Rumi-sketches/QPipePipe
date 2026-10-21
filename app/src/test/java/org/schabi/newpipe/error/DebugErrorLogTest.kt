package org.schabi.newpipe.error

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

class DebugErrorLogTest {
    private val time: Date = Calendar.getInstance().apply {
        set(2026, Calendar.OCTOBER, 19, 17, 42, 13)
        set(Calendar.MILLISECOND, 512)
    }.time

    @Test
    fun oneFilePerDay() {
        assertEquals("errors-2026-10-19.log", DebugErrorLog.fileName(time))
    }

    @Test
    fun entryHasContextAndEveryTrace() {
        val errorInfo = ErrorInfo(
            arrayOf("java.lang.IllegalStateException: first\n\tat a.b(C.kt:1)\n", "second trace"),
            UserAction.REQUESTED_STREAM,
            "YouTube (Anonymous)",
            "https://www.youtube.com/watch?v=abc",
            0
        )

        val entry = DebugErrorLog.formatEntry(errorInfo, time, "5.4.0-q2 (1109024), debug 3", "Could not play")
        val lines = entry.lines()

        assertTrue(lines[0].matches(Regex("""===== 2026-10-19 17:42:13\.512 [+-]\d{4} =====""")))
        assertEquals(
            listOf(
                "Build: 5.4.0-q2 (1109024), debug 3",
                "Action: requested stream",
                "Service: YouTube (Anonymous)",
                "Request: https://www.youtube.com/watch?v=abc",
                "Message: Could not play",
                "--- trace 1/2 ---",
                "java.lang.IllegalStateException: first",
                "\tat a.b(C.kt:1)",
                "--- trace 2/2 ---",
                "second trace",
                "",
                ""
            ),
            lines.drop(1)
        )
    }

    @Test
    fun playbackEntryHasDetailsAndCaller() {
        val entry = DebugErrorLog.formatPlaybackEntry(
            "reload of the current video",
            listOf("Video: Title (https://www.youtube.com/watch?v=abc)", "Recovery: unset"),
            listOf("a.B.c(B.kt:1)", "d.E.f(E.java:2)"),
            time,
            "5.4.0-q2 (1109024), debug 5"
        )
        val lines = entry.lines()

        assertTrue(lines[0].matches(Regex("""===== 2026-10-19 17:42:13\.512 [+-]\d{4} =====""")))
        assertEquals(
            listOf(
                "Build: 5.4.0-q2 (1109024), debug 5",
                "Playback: reload of the current video",
                "Video: Title (https://www.youtube.com/watch?v=abc)",
                "Recovery: unset",
                "--- caller ---",
                "\tat a.B.c(B.kt:1)",
                "\tat d.E.f(E.java:2)",
                "",
                ""
            ),
            lines.drop(1)
        )
    }
}
