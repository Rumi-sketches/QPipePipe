package org.schabi.newpipe.qpipepipe.recentqueues;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.player.mediaitem.PlayerMediaItem;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class RecentQueueRulesTest {
    private static final long NO_PLAYLIST = -1;

    private static PlayerMediaItem item(final String url) {
        return new PlayerMediaItem.Builder()
                .url(url)
                .title(url)
                .uploader("uploader")
                .duration(100)
                .streamType(StreamType.VIDEO_STREAM)
                .build();
    }

    @Test
    public void queueIsKeptOnlyOnceTheUserBuiltIt() {
        assertFalse(RecentQueueRecorder.isWorthKeeping(NO_PLAYLIST, false));
        assertTrue(RecentQueueRecorder.isWorthKeeping(NO_PLAYLIST, true));
    }

    @Test
    public void playlistQueueIsAlwaysKept() {
        assertTrue(RecentQueueRecorder.isWorthKeeping(7, false));
    }

    @Test
    public void playlistQueuesShareOneEntryWhateverTheSession() {
        assertEquals(RecentQueueRecorder.keyOf(7, "first"),
                RecentQueueRecorder.keyOf(7, "second"));
    }

    @Test
    public void otherQueuesHaveOneEntryPerSession() {
        assertEquals(RecentQueueRecorder.keyOf(NO_PLAYLIST, "first"),
                RecentQueueRecorder.keyOf(NO_PLAYLIST, "first"));
        assertFalse(RecentQueueRecorder.keyOf(NO_PLAYLIST, "first")
                .equals(RecentQueueRecorder.keyOf(NO_PLAYLIST, "second")));
    }

    @Test
    public void resumingAQueueContinuesItsSession() {
        final RecentQueueEntity queue = new RecentQueueEntity();
        queue.queueKey = RecentQueueRecorder.keyOf(NO_PLAYLIST, "session-1");
        assertEquals("session-1", RecentQueueActions.sessionOf(queue));
    }

    @Test
    public void snapshotMarksOnlyTheAddedVideos() {
        final RecentQueueSnapshot snapshot = new RecentQueueSnapshot(new RecentQueueEntity(),
                Arrays.asList(item("https://a"), item("https://b"), item("https://c")),
                Collections.singletonList("https://c"));

        assertEquals(1, snapshot.countAdded(snapshot.addedUrls));
        final List<RecentQueueStreamEntity> streams = snapshot.streamsFor(5, snapshot.addedUrls);
        assertEquals(3, streams.size());
        assertEquals(5, streams.get(2).queueId);
        assertEquals(2, streams.get(2).position);
        assertFalse(streams.get(0).addedToPlaylist);
        assertTrue(streams.get(2).addedToPlaylist);
    }

    @Test
    public void videoEnqueuedTwiceIsMarkedAddedOnce() {
        final RecentQueueSnapshot snapshot = new RecentQueueSnapshot(new RecentQueueEntity(),
                Arrays.asList(item("https://a"), item("https://c"), item("https://c")),
                Collections.singletonList("https://c"));

        assertEquals(1, snapshot.countAdded(snapshot.addedUrls));
        final List<RecentQueueStreamEntity> streams = snapshot.streamsFor(5, snapshot.addedUrls);
        assertTrue(streams.get(1).addedToPlaylist);
        assertFalse(streams.get(2).addedToPlaylist);
    }

    @Test
    public void durationIsInHoursAndMinutes() {
        assertEquals("0:59", RecentQueueActions.hoursAndMinutes(59 * 60 + 30));
        assertEquals("77:23", RecentQueueActions.hoursAndMinutes(77 * 3600 + 23 * 60 + 28));
    }

    @Test
    public void streamKeepsWhatIsNeededToPlayIt() {
        final RecentQueueStreamEntity stream =
                RecentQueueStreamEntity.from(item("https://a"), 1, 0, false);
        assertEquals("https://a", stream.toStreamInfoItem().getUrl());
        assertEquals(100, stream.toStreamInfoItem().getDuration());
        assertEquals(StreamType.VIDEO_STREAM, stream.toStreamInfoItem().getStreamType());
    }
}
