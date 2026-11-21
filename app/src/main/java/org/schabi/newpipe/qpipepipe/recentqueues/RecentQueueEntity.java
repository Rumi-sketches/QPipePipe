package org.schabi.newpipe.qpipepipe.recentqueues;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * A play queue the user played, kept so it can be resumed or saved after the player is gone.
 * A queue started from a local playlist is linked to it ({@link #sourcePlaylistId}) and keeps
 * one entry per playlist; any other queue keeps one entry per queue session.
 */
@Entity(tableName = RecentQueueEntity.TABLE,
        indices = {@Index(value = RecentQueueEntity.QUEUE_KEY, unique = true)})
public class RecentQueueEntity {
    public static final String TABLE = "recent_queues";
    public static final String ID = "uid";
    public static final String QUEUE_KEY = "queue_key";
    public static final String UPDATED_AT = "updated_at";

    /** Value of {@link #sourcePlaylistId} for queues not started from a local playlist. */
    public static final long NO_PLAYLIST = -1;

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = ID)
    public long uid;

    /** "playlist:&lt;id&gt;" for queues linked to a local playlist, "queue:&lt;session&gt;" otherwise. */
    @NonNull
    @ColumnInfo(name = QUEUE_KEY)
    public String queueKey = "";

    @ColumnInfo(name = "source_playlist_id")
    public long sourcePlaylistId = NO_PLAYLIST;

    /** Name of the linked playlist when the queue was last saved. */
    @Nullable
    @ColumnInfo(name = "source_playlist_name")
    public String sourcePlaylistName;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    @ColumnInfo(name = UPDATED_AT)
    public long updatedAt;

    /** Index of the video that was playing. */
    @ColumnInfo(name = "current_index")
    public int currentIndex;

    @ColumnInfo(name = "stream_count")
    public int streamCount;

    /** Sum of the known durations, in seconds. */
    @ColumnInfo(name = "total_duration")
    public long totalDuration;

    @Nullable
    @ColumnInfo(name = "thumbnail_url")
    public String thumbnailUrl;

    /** How many of the videos were added to the linked playlist while the queue played. */
    @ColumnInfo(name = "added_count")
    public int addedCount;

    public boolean isLinkedToPlaylist() {
        return sourcePlaylistId != NO_PLAYLIST;
    }

    @NonNull
    static String keyForPlaylist(final long playlistId) {
        return "playlist:" + playlistId;
    }

    @NonNull
    static String keyForSession(@NonNull final String sessionId) {
        return "queue:" + sessionId;
    }
}
