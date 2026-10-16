package org.schabi.newpipe.qpipepipe

import androidx.room.ColumnInfo

/**
 * Total duration of a local playlist and the position of its video started most recently, for
 * the line under the playlist in Playlists. See
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
    val lastPlayedPosition: Int?
)
