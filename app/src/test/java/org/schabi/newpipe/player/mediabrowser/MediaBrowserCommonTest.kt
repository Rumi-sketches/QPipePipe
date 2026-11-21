package org.schabi.newpipe.player.mediabrowser

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaBrowserCommonTest {
    @Test
    fun changedKeysReportsOnlyKeysWhoseValueDiffers() {
        val old = mapOf(1L to listOf(10L to 0), 2L to listOf(20L to 0), 3L to listOf(30L to 0))
        val new = mapOf(1L to listOf(10L to 0), 2L to listOf(21L to 0), 4L to listOf(40L to 0))

        assertEquals(setOf(2L, 3L, 4L), changedKeys(old, new))
    }

    @Test
    fun changedKeysIsEmptyWhenNothingChanged() {
        val contents = mapOf(1L to listOf(10L to 0, 11L to 1))

        assertEquals(emptySet<Long>(), changedKeys(contents, contents.toMap()))
    }

    @Test
    fun changedKeysDetectsReordering() {
        val old = mapOf(1L to listOf(10L to 0, 11L to 1))
        val new = mapOf(1L to listOf(11L to 0, 10L to 1))

        assertEquals(setOf(1L), changedKeys(old, new))
    }
}
