package org.schabi.newpipe.qpipepipe

import androidx.room.ColumnInfo

/**
 * Saved playback progress of a stream, found by url. See
 * [org.schabi.newpipe.database.history.dao.StreamHistoryDAO.getProgressOf].
 */
data class StreamProgress(
    @ColumnInfo(name = "url")
    val url: String,

    @ColumnInfo(name = "duration")
    val durationSeconds: Long,

    @ColumnInfo(name = "progress_time")
    val progressMillis: Long
)
