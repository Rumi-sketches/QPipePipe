package org.schabi.newpipe.qpipepipe.recentqueues;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.player.mediaitem.PlayerMediaItem;

/**
 * One video of a {@link RecentQueueEntity}. The stream data is copied here because this
 * database is separate from upstream's and cannot reference its streams table.
 */
@Entity(tableName = RecentQueueStreamEntity.TABLE,
        primaryKeys = {RecentQueueStreamEntity.QUEUE_ID, RecentQueueStreamEntity.POSITION},
        indices = {@Index(RecentQueueStreamEntity.QUEUE_ID)},
        foreignKeys = @ForeignKey(entity = RecentQueueEntity.class,
                parentColumns = RecentQueueEntity.ID,
                childColumns = RecentQueueStreamEntity.QUEUE_ID,
                onDelete = ForeignKey.CASCADE))
public class RecentQueueStreamEntity {
    public static final String TABLE = "recent_queue_streams";
    public static final String QUEUE_ID = "queue_id";
    public static final String POSITION = "position";

    @ColumnInfo(name = QUEUE_ID)
    public long queueId;

    @ColumnInfo(name = POSITION)
    public int position;

    @ColumnInfo(name = "service_id")
    public int serviceId;

    @NonNull
    @ColumnInfo(name = "url")
    public String url = "";

    @NonNull
    @ColumnInfo(name = "title")
    public String title = "";

    @NonNull
    @ColumnInfo(name = "uploader")
    public String uploader = "";

    @Nullable
    @ColumnInfo(name = "uploader_url")
    public String uploaderUrl;

    /** In seconds, 0 or less when unknown. */
    @ColumnInfo(name = "duration")
    public long duration;

    @Nullable
    @ColumnInfo(name = "thumbnail_url")
    public String thumbnailUrl;

    @NonNull
    @ColumnInfo(name = "stream_type")
    public String streamType = StreamType.VIDEO_STREAM.name();

    /** Whether this video was added to the linked playlist while the queue played. */
    @ColumnInfo(name = "added_to_playlist")
    public boolean addedToPlaylist;

    @NonNull
    static RecentQueueStreamEntity from(@NonNull final PlayerMediaItem item, final long queueId,
                                        final int position, final boolean addedToPlaylist) {
        final RecentQueueStreamEntity entity = new RecentQueueStreamEntity();
        entity.queueId = queueId;
        entity.position = position;
        entity.serviceId = item.getServiceId();
        entity.url = item.getUrl();
        entity.title = item.getTitle();
        entity.uploader = item.getUploader();
        entity.uploaderUrl = item.getUploaderUrl();
        entity.duration = item.getDuration();
        entity.thumbnailUrl = item.getThumbnailUrl();
        entity.streamType = item.getStreamType().name();
        entity.addedToPlaylist = addedToPlaylist;
        return entity;
    }

    @NonNull
    public StreamInfoItem toStreamInfoItem() {
        StreamType type;
        try {
            type = StreamType.valueOf(streamType);
        } catch (final IllegalArgumentException e) {
            type = StreamType.VIDEO_STREAM;
        }
        final StreamInfoItem item = new StreamInfoItem(serviceId, url, title, type);
        item.setDuration(duration);
        item.setUploaderName(uploader);
        item.setUploaderUrl(uploaderUrl);
        item.setThumbnailUrl(thumbnailUrl);
        return item;
    }
}
