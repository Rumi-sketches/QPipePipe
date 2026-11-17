package org.schabi.newpipe.qpipepipe.player

/**
 * Allows one recovery per queue item within [windowMs]: when the same item fails again that soon
 * after a recovery, recovering again would only loop.
 */
internal class OneRetryPerItem(private val windowMs: Long) {
    // When each item last recovered. Only entries still inside the window are kept, so the map
    // stays as small as the items that failed in the last [windowMs].
    private val retriedAtMs = HashMap<String, Long>()

    /** @return whether to recover from this failure of [itemUuid] at [nowMs] */
    fun tryRetry(itemUuid: String?, nowMs: Long): Boolean {
        retriedAtMs.values.removeAll { nowMs - it >= windowMs }
        if (itemUuid == null) {
            return true
        }
        if (itemUuid in retriedAtMs) {
            return false
        }
        retriedAtMs[itemUuid] = nowMs
        return true
    }
}
