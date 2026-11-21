package org.schabi.newpipe.database.history.model

import androidx.room.ColumnInfo

/**
 * A stream in the watch history, with the date it was last started and its saved progress
 * (0 when none was saved). See [org.schabi.newpipe.database.history.dao.StreamHistoryDAO.getPlayedAmong].
 */
data class LatestPlayedStream(
    @ColumnInfo(name = "service_id")
    val serviceId: Int,

    @ColumnInfo(name = "url")
    val url: String,

    @ColumnInfo(name = "duration")
    val durationSeconds: Long,

    @ColumnInfo(name = "access_date")
    val accessDateMillis: Long,

    @ColumnInfo(name = "progress_time")
    val progressMillis: Long
)
