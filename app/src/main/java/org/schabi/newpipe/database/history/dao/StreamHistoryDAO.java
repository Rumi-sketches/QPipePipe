package org.schabi.newpipe.database.history.dao;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.RewriteQueriesToDropUnusedColumns;

import org.schabi.newpipe.database.history.model.LatestPlayedStream;
import org.schabi.newpipe.database.history.model.StreamHistoryEntity;
import org.schabi.newpipe.database.history.model.StreamHistoryEntry;
import org.schabi.newpipe.database.stream.StreamStatisticsEntry;
import org.schabi.newpipe.qpipepipe.StreamProgress;

import java.util.List;

import io.reactivex.rxjava3.core.Flowable;

import static org.schabi.newpipe.database.history.model.StreamHistoryEntity.JOIN_STREAM_ID;
import static org.schabi.newpipe.database.history.model.StreamHistoryEntity.STREAM_ACCESS_DATE;
import static org.schabi.newpipe.database.history.model.StreamHistoryEntity.STREAM_HISTORY_TABLE;
import static org.schabi.newpipe.database.history.model.StreamHistoryEntity.STREAM_REPEAT_COUNT;
import static org.schabi.newpipe.database.stream.StreamStatisticsEntry.STREAM_LATEST_DATE;
import static org.schabi.newpipe.database.stream.StreamStatisticsEntry.STREAM_WATCH_COUNT;
import static org.schabi.newpipe.database.stream.model.StreamEntity.STREAM_ID;
import static org.schabi.newpipe.database.stream.model.StreamEntity.STREAM_TABLE;
import static org.schabi.newpipe.database.stream.model.StreamStateEntity.JOIN_STREAM_ID_ALIAS;
import static org.schabi.newpipe.database.stream.model.StreamStateEntity.STREAM_PROGRESS_MILLIS;
import static org.schabi.newpipe.database.stream.model.StreamStateEntity.STREAM_STATE_TABLE;

@Dao
public abstract class StreamHistoryDAO implements HistoryDAO<StreamHistoryEntity> {
    @Query("SELECT * FROM " + STREAM_HISTORY_TABLE
            + " WHERE " + STREAM_ACCESS_DATE + " = "
            + "(SELECT MAX(" + STREAM_ACCESS_DATE + ") FROM " + STREAM_HISTORY_TABLE + ")")
    @Override
    @Nullable
    public abstract StreamHistoryEntity getLatestEntry();

    @Override
    @Query("SELECT * FROM " + STREAM_HISTORY_TABLE)
    public abstract Flowable<List<StreamHistoryEntity>> getAll();

    @Override
    @Query("DELETE FROM " + STREAM_HISTORY_TABLE)
    public abstract int deleteAll();

    @Override
    public Flowable<List<StreamHistoryEntity>> listByService(final int serviceId) {
        throw new UnsupportedOperationException();
    }

    @Query("SELECT * FROM " + STREAM_TABLE
            + " INNER JOIN " + STREAM_HISTORY_TABLE
            + " ON " + STREAM_ID + " = " + JOIN_STREAM_ID
            + " ORDER BY " + STREAM_ACCESS_DATE + " DESC")
    public abstract Flowable<List<StreamHistoryEntry>> getHistory();


    @Query("SELECT * FROM " + STREAM_TABLE
            + " INNER JOIN " + STREAM_HISTORY_TABLE
            + " ON " + STREAM_ID + " = " + JOIN_STREAM_ID
            + " ORDER BY " + STREAM_ID + " ASC")
    public abstract Flowable<List<StreamHistoryEntry>> getHistorySortedById();

    @Query("SELECT * FROM " + STREAM_HISTORY_TABLE + " WHERE " + JOIN_STREAM_ID
            + " = :streamId ORDER BY " + STREAM_ACCESS_DATE + " DESC LIMIT 1")
    @Nullable
    public abstract StreamHistoryEntity getLatestEntry(long streamId);

    /**
     * The streams whose url is in {@code urls} and that are in the history, each with the date
     * it was last started. Keep {@code urls} under SQLite's limit of bound parameters (999).
     */
    @Query("SELECT streams.service_id, streams.url, streams.duration, "
            + "MAX(stream_history.access_date) AS access_date, "
            + "IFNULL(stream_state.progress_time, 0) AS progress_time"
            + " FROM streams"
            + " INNER JOIN stream_history ON streams.uid = stream_history.stream_id"
            + " LEFT JOIN stream_state ON streams.uid = stream_state.stream_id"
            + " WHERE streams.url IN (:urls)"
            + " GROUP BY streams.uid")
    public abstract List<LatestPlayedStream> getPlayedAmong(List<String> urls);

    @Query("DELETE FROM " + STREAM_HISTORY_TABLE + " WHERE " + JOIN_STREAM_ID + " = :streamId")
    public abstract int deleteStreamHistory(long streamId);

    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM " + STREAM_TABLE

            // Select the latest entry and watch count for each stream id on history table
            + " INNER JOIN "
            + "(SELECT " + JOIN_STREAM_ID + ", "
            + "  MAX(" + STREAM_ACCESS_DATE + ") AS " + STREAM_LATEST_DATE + ", "
            + "  SUM(" + STREAM_REPEAT_COUNT + ") AS " + STREAM_WATCH_COUNT
            + " FROM " + STREAM_HISTORY_TABLE + " GROUP BY " + JOIN_STREAM_ID + ")"

            + " ON " + STREAM_ID + " = " + JOIN_STREAM_ID

            + " LEFT JOIN "
            + "(SELECT " + JOIN_STREAM_ID + " AS " + JOIN_STREAM_ID_ALIAS + ", "
            + STREAM_PROGRESS_MILLIS
            + " FROM " + STREAM_STATE_TABLE + " )"
            + " ON " + STREAM_ID + " = " + JOIN_STREAM_ID_ALIAS)
    public abstract Flowable<List<StreamStatisticsEntry>> getStatistics();

    /** QPipePipe: saved progress of the streams in {@code urls} that have one (under 999). */
    @Query("SELECT streams.url, streams.duration, stream_state.progress_time"
            + " FROM streams INNER JOIN stream_state ON streams.uid = stream_state.stream_id"
            + " WHERE streams.url IN (:urls)")
    public abstract List<StreamProgress> getProgressOf(List<String> urls);
}
