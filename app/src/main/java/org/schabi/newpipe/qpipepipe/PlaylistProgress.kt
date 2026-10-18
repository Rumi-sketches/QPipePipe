package org.schabi.newpipe.qpipepipe

import androidx.room.ColumnInfo

/**
 * Total duration of a local playlist and the position and date of its video started most
 * recently, for the playlist rows in Playlists. See
 * [org.schabi.newpipe.database.playlist.dao.PlaylistStreamDAO.getPlaylistProgress].
 */
data class PlaylistProgress(
    @ColumnInfo(name = "playlist_id")
    val playlistId: Long,

    /** In seconds, counting only the videos with a known duration. */
    @ColumnInfo(name = "total_duration")
    val totalDuration: Long,

    /** 0-based position of the video started most recently, null when none was started. */
    @ColumnInfo(name = "last_played_position")
    val lastPlayedPosition: Int?,

    /** When that video was started, in epoch milliseconds; null when none was started. */
    @ColumnInfo(name = "last_played_at")
    val lastPlayedAt: Long?,

    /** Videos played past [PlaylistResume.FINISHED_RATIO] of their length. */
    @ColumnInfo(name = "watched_count")
    val watchedCount: Int
)
