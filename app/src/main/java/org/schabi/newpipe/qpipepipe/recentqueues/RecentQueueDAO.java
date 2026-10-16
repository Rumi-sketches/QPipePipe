package org.schabi.newpipe.qpipepipe.recentqueues;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.List;

import io.reactivex.rxjava3.core.Flowable;

@Dao
public abstract class RecentQueueDAO {

    @Query("SELECT * FROM " + RecentQueueEntity.TABLE
            + " ORDER BY " + RecentQueueEntity.UPDATED_AT + " DESC")
    public abstract Flowable<List<RecentQueueEntity>> getAll();

    @Query("SELECT * FROM " + RecentQueueEntity.TABLE + " WHERE uid = :queueId")
    public abstract Flowable<List<RecentQueueEntity>> getQueue(long queueId);

    @Query("SELECT * FROM " + RecentQueueStreamEntity.TABLE + " WHERE queue_id = :queueId"
            + " ORDER BY " + RecentQueueStreamEntity.POSITION)
    public abstract Flowable<List<RecentQueueStreamEntity>> getStreams(long queueId);

    @Query("SELECT url FROM " + RecentQueueStreamEntity.TABLE
            + " WHERE queue_id = :queueId")
    abstract List<String> getUrls(long queueId);

    @Query("SELECT url FROM " + RecentQueueStreamEntity.TABLE
            + " WHERE queue_id = :queueId AND added_to_playlist = 1")
    abstract List<String> getUrlsAddedToPlaylist(long queueId);

    @Nullable
    @Query("SELECT * FROM " + RecentQueueEntity.TABLE
            + " WHERE " + RecentQueueEntity.QUEUE_KEY + " = :queueKey")
    abstract RecentQueueEntity getByKey(String queueKey);

    @Insert
    abstract long insert(RecentQueueEntity queue);

    @Update
    abstract void update(RecentQueueEntity queue);

    @Query("UPDATE " + RecentQueueStreamEntity.TABLE + " SET added_to_playlist = 0"
            + " WHERE queue_id = :queueId AND url IN (:urls)")
    abstract void unmarkStreams(long queueId, List<String> urls);

    @Query("SELECT COUNT(*) FROM " + RecentQueueStreamEntity.TABLE
            + " WHERE queue_id = :queueId AND added_to_playlist = 1")
    abstract int countAdded(long queueId);

    /** The videos at {@code urls} were taken back out of the playlist of the queue. */
    @Transaction
    void unmarkAdded(@NonNull final String queueKey, @NonNull final List<String> urls) {
        final RecentQueueEntity queue = getByKey(queueKey);
        if (queue == null) {
            return;
        }
        unmarkStreams(queue.uid, urls);
        queue.addedCount = countAdded(queue.uid);
        update(queue);
    }

    @Query("UPDATE " + RecentQueueEntity.TABLE
            + " SET " + RecentQueueEntity.QUEUE_KEY + " = :queueKey,"
            + " source_playlist_id = :playlistId, source_playlist_name = :playlistName"
            + " WHERE " + RecentQueueEntity.ID + " = :queueId")
    abstract void linkToPlaylist(long queueId, String queueKey, long playlistId,
                                 String playlistName);

    @Query("DELETE FROM " + RecentQueueStreamEntity.TABLE + " WHERE queue_id = :queueId")
    abstract void deleteStreams(long queueId);

    @Insert
    abstract void insertStreams(List<RecentQueueStreamEntity> streams);

    @Query("DELETE FROM " + RecentQueueEntity.TABLE + " WHERE uid = :queueId")
    public abstract int delete(long queueId);

    @Query("DELETE FROM " + RecentQueueEntity.TABLE)
    public abstract int deleteAll();

    /**
     * Writes {@code snapshot} over the queue with the same key, keeping its creation date and
     * the videos already marked as added to the playlist.
     *
     * @param mayCreate whether to add the queue when it has no entry yet
     */
    @Transaction
    void save(@NonNull final RecentQueueSnapshot snapshot, final boolean mayCreate) {
        final RecentQueueEntity existing = getByKey(snapshot.queue.queueKey);
        if (existing == null && !mayCreate) {
            return;
        }
        final RecentQueueEntity queue = snapshot.queue;
        final List<String> addedUrls = snapshot.addedUrls;
        if (existing != null) {
            addedUrls.addAll(getUrlsAddedToPlaylist(existing.uid));
        }
        queue.addedCount = snapshot.countAdded(addedUrls);
        if (existing == null) {
            queue.createdAt = queue.updatedAt;
            queue.uid = insert(queue);
        } else {
            queue.uid = existing.uid;
            queue.createdAt = existing.createdAt;
            update(queue);
            deleteStreams(existing.uid);
        }
        insertStreams(snapshot.streamsFor(queue.uid, addedUrls));
    }
}
