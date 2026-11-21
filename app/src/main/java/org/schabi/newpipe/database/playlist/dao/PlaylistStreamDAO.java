package org.schabi.newpipe.database.playlist.dao;

import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.RewriteQueriesToDropUnusedColumns;
import androidx.room.Transaction;

import org.schabi.newpipe.database.BasicDAO;
import org.schabi.newpipe.database.playlist.PlaylistDuplicatesEntry;
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry;
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry;
import org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity;
import org.schabi.newpipe.qpipepipe.PlaylistProgress;
import org.schabi.newpipe.qpipepipe.PlaylistResume;

import java.util.List;

import io.reactivex.rxjava3.core.Flowable;

import static org.schabi.newpipe.database.playlist.PlaylistDuplicatesEntry.PLAYLIST_TIMES_STREAM_IS_CONTAINED;
import static org.schabi.newpipe.database.playlist.PlaylistMetadataEntry.PLAYLIST_STREAM_COUNT;
import static org.schabi.newpipe.database.playlist.model.PlaylistEntity.PLAYLIST_DISPLAY_INDEX;
import static org.schabi.newpipe.database.playlist.model.PlaylistEntity.PLAYLIST_ID;
import static org.schabi.newpipe.database.playlist.model.PlaylistEntity.PLAYLIST_NAME;
import static org.schabi.newpipe.database.playlist.model.PlaylistEntity.PLAYLIST_TABLE;
import static org.schabi.newpipe.database.playlist.model.PlaylistEntity.PLAYLIST_THUMBNAIL_URL;
import static org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity.JOIN_INDEX;
import static org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity.JOIN_PLAYLIST_ID;
import static org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity.JOIN_STREAM_ID;
import static org.schabi.newpipe.database.playlist.model.PlaylistStreamEntity.PLAYLIST_STREAM_JOIN_TABLE;
import static org.schabi.newpipe.database.stream.model.StreamEntity.STREAM_ID;
import static org.schabi.newpipe.database.stream.model.StreamEntity.STREAM_TABLE;
import static org.schabi.newpipe.database.stream.model.StreamEntity.STREAM_THUMBNAIL_URL;
import static org.schabi.newpipe.database.stream.model.StreamEntity.STREAM_URL;
import static org.schabi.newpipe.database.stream.model.StreamStateEntity.JOIN_STREAM_ID_ALIAS;
import static org.schabi.newpipe.database.stream.model.StreamStateEntity.STREAM_PROGRESS_MILLIS;
import static org.schabi.newpipe.database.stream.model.StreamStateEntity.STREAM_STATE_TABLE;

@Dao
public interface PlaylistStreamDAO extends BasicDAO<PlaylistStreamEntity> {
    @Override
    @Query("SELECT * FROM " + PLAYLIST_STREAM_JOIN_TABLE)
    Flowable<List<PlaylistStreamEntity>> getAll();

    @Override
    @Query("DELETE FROM " + PLAYLIST_STREAM_JOIN_TABLE)
    int deleteAll();

    @Override
    default Flowable<List<PlaylistStreamEntity>> listByService(final int serviceId) {
        throw new UnsupportedOperationException();
    }

    @Query("DELETE FROM " + PLAYLIST_STREAM_JOIN_TABLE
            + " WHERE " + JOIN_PLAYLIST_ID + " = :playlistId")
    void deleteBatch(long playlistId);

    @Query("SELECT COALESCE(MAX(" + JOIN_INDEX + "), -1)"
            + " FROM " + PLAYLIST_STREAM_JOIN_TABLE
            + " WHERE " + JOIN_PLAYLIST_ID + " = :playlistId")
    Flowable<Integer> getMaximumIndexOf(long playlistId);

    @RewriteQueriesToDropUnusedColumns
    @Transaction
    @Query("SELECT * FROM " + STREAM_TABLE + " INNER JOIN "
            // get ids of streams of the given playlist
            + "(SELECT " + JOIN_STREAM_ID + "," + JOIN_INDEX
            + " FROM " + PLAYLIST_STREAM_JOIN_TABLE
            + " WHERE " + JOIN_PLAYLIST_ID + " = :playlistId)"

            // then merge with the stream metadata
            + " ON " + STREAM_ID + " = " + JOIN_STREAM_ID

            + " LEFT JOIN "
            + "(SELECT " + JOIN_STREAM_ID + " AS " + JOIN_STREAM_ID_ALIAS + ", "
            + STREAM_PROGRESS_MILLIS
            + " FROM " + STREAM_STATE_TABLE + " )"
            + " ON " + STREAM_ID + " = " + JOIN_STREAM_ID_ALIAS

            + " ORDER BY " + JOIN_INDEX + " ASC")
    Flowable<List<PlaylistStreamEntry>> getOrderedStreamsOf(long playlistId);

    @Transaction
    @Query("SELECT " + PLAYLIST_ID + ", " + PLAYLIST_NAME + ", " + PLAYLIST_THUMBNAIL_URL + ", "
            + PLAYLIST_DISPLAY_INDEX + ", "
            + "COALESCE(COUNT(" + JOIN_PLAYLIST_ID + "), 0) AS " + PLAYLIST_STREAM_COUNT

            + " FROM " + PLAYLIST_TABLE
            + " LEFT JOIN " + PLAYLIST_STREAM_JOIN_TABLE
            + " ON " + PLAYLIST_ID + " = " + JOIN_PLAYLIST_ID
            + " GROUP BY " + PLAYLIST_ID
            + " ORDER BY " + PLAYLIST_NAME + " COLLATE NOCASE ASC")
    Flowable<List<PlaylistMetadataEntry>> getPlaylistMetadata();

    @Transaction
    @Query("SELECT " + PLAYLIST_TABLE + "." + PLAYLIST_ID + ", "
            + PLAYLIST_NAME + ", "
            + PLAYLIST_TABLE + "." + PLAYLIST_THUMBNAIL_URL + ", "
            + PLAYLIST_DISPLAY_INDEX + ", "
            + "COALESCE(COUNT(" + JOIN_PLAYLIST_ID + "), 0) AS " + PLAYLIST_STREAM_COUNT + ", "
            + "COALESCE(SUM(" + STREAM_URL + " = :streamUrl), 0) AS "
                + PLAYLIST_TIMES_STREAM_IS_CONTAINED

            + " FROM " + PLAYLIST_TABLE
            + " LEFT JOIN " + PLAYLIST_STREAM_JOIN_TABLE
            + " ON " + PLAYLIST_TABLE + "." + PLAYLIST_ID + " = " + JOIN_PLAYLIST_ID

            + " LEFT JOIN " + STREAM_TABLE
            + " ON " + STREAM_TABLE + "." + STREAM_ID + " = " + JOIN_STREAM_ID
            + " AND :streamUrl = :streamUrl"

            + " GROUP BY " + JOIN_PLAYLIST_ID
            + " ORDER BY " + PLAYLIST_DISPLAY_INDEX)
    Flowable<List<PlaylistDuplicatesEntry>> getPlaylistDuplicatesMetadata(String streamUrl);

    /**
     * QPipePipe: duration and last started video of every local playlist that has videos.
     * The position and date come from the most recent watch history entry among the playlist
     * videos; watched videos are those played past {@link PlaylistResume#FINISHED_RATIO}.
     */
    @Query("SELECT totals." + JOIN_PLAYLIST_ID + " AS playlist_id, totals.total_duration,"
            + " latest.last_played_position, latest.last_played_at,"
            + " COALESCE(watched.watched_count, 0) AS watched_count"
            + " FROM (SELECT " + JOIN_PLAYLIST_ID + ","
            + " SUM(CASE WHEN streams.duration > 0 THEN streams.duration ELSE 0 END)"
            + " AS total_duration"
            + " FROM " + PLAYLIST_STREAM_JOIN_TABLE
            + " INNER JOIN " + STREAM_TABLE + " ON " + STREAM_ID + " = " + JOIN_STREAM_ID
            + " GROUP BY " + JOIN_PLAYLIST_ID + ") AS totals"
            + " LEFT JOIN (SELECT psj." + JOIN_PLAYLIST_ID + ", MAX(stream_history.access_date)"
            + " AS last_played_at,"
            + " (SELECT COUNT(*) FROM " + PLAYLIST_STREAM_JOIN_TABLE + " AS other"
            + " WHERE other." + JOIN_PLAYLIST_ID + " = psj." + JOIN_PLAYLIST_ID
            + " AND other." + JOIN_INDEX + " < psj." + JOIN_INDEX + ") AS last_played_position"
            + " FROM " + PLAYLIST_STREAM_JOIN_TABLE + " AS psj"
            + " INNER JOIN stream_history ON stream_history.stream_id = psj." + JOIN_STREAM_ID
            + " GROUP BY psj." + JOIN_PLAYLIST_ID + ") AS latest"
            + " ON latest." + JOIN_PLAYLIST_ID + " = totals." + JOIN_PLAYLIST_ID
            + " LEFT JOIN (SELECT psj." + JOIN_PLAYLIST_ID + ", COUNT(*) AS watched_count"
            + " FROM " + PLAYLIST_STREAM_JOIN_TABLE + " AS psj"
            + " INNER JOIN " + STREAM_TABLE + " ON " + STREAM_ID + " = psj." + JOIN_STREAM_ID
            + " INNER JOIN stream_state ON stream_state.stream_id = psj." + JOIN_STREAM_ID
            + " WHERE streams.duration > 0 AND stream_state.progress_time"
            + " >= streams.duration * 1000 * " + PlaylistResume.FINISHED_RATIO
            + " GROUP BY psj." + JOIN_PLAYLIST_ID + ") AS watched"
            + " ON watched." + JOIN_PLAYLIST_ID + " = totals." + JOIN_PLAYLIST_ID)
    Flowable<List<PlaylistProgress>> getPlaylistProgress();
}
