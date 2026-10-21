package org.schabi.newpipe.error

/**
 * Collapses the entries of [DebugErrorLog] that repeat while a failure loops, e.g. a reload and its
 * network error every 3 s while the phone is offline. An entry equal to one written less than
 * [windowMs] earlier is only counted; the counts go out with the next entry written, one line per
 * repeated entry. Counts still pending when nothing else is ever written are lost.
 *
 * Not thread safe: [DebugErrorLog] uses it from its single writer thread.
 */
class RepeatFilter(private val windowMs: Long) {
    private class Written(val title: String, val timeMs: Long) {
        var repeats = 0
        var lastRepeatMs = 0L
    }

    private val written = LinkedHashMap<String, Written>()

    /**
     * The text to append for an entry whose [body] holds everything but its timestamp: the pending
     * repeat counts followed by [body], or `null` when the entry is a repeat to skip. [clock]
     * formats the time of the last repeat.
     */
    fun filter(title: String, body: String, timeMs: Long, clock: (Long) -> String): String? {
        val previous = written[body]
        if (previous != null && timeMs - previous.timeMs < windowMs) {
            previous.repeats++
            previous.lastRepeatMs = timeMs
            return null
        }

        val text = StringBuilder()
        written.values.filter { it.repeats > 0 }.forEach {
            text.append("Repeated ").append(it.repeats)
                .append(if (it.repeats == 1) " more time until " else " more times until ")
                .append(clock(it.lastRepeatMs)).append(": ").append(it.title).append('\n')
            it.repeats = 0
        }
        written.values.removeAll { timeMs - it.timeMs >= windowMs }
        written[body] = Written(title, timeMs)
        return text.append(body).toString()
    }
}
