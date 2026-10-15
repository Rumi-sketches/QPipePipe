package org.schabi.newpipe.database.history.model

import androidx.room.ColumnInfo

/**
 * The most recently started stream among a set of urls, with its saved progress
 * (0 when none was saved). See [org.schabi.newpipe.database.history.dao.StreamHistoryDAO.getLatestPlayedAmong].
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
