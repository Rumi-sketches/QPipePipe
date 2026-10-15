package org.schabi.newpipe.qpipepipe;

import static org.junit.Assert.assertEquals;
import static org.schabi.newpipe.qpipepipe.PlaylistResume.NO_RESUME;

import org.junit.Test;
import org.schabi.newpipe.database.history.model.LatestPlayedStream;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PlaylistResumeTest {
    private static final int SERVICE = 0;

    private static final List<StreamInfoItem> PLAYLIST = Arrays.asList(
            item("https://a"), item("https://b"), item("https://c"));

    private static StreamInfoItem item(final String url) {
        return new StreamInfoItem(SERVICE, url, url, StreamType.VIDEO_STREAM);
    }

    /** A 100 second video started at {@code url}, watched up to {@code progressSeconds}. */
    private static LatestPlayedStream played(final String url, final long progressSeconds) {
        return new LatestPlayedStream(SERVICE, url, 100, 0, progressSeconds * 1000);
    }

    @Test
    public void nothingStartedMeansNoResume() {
        assertEquals(NO_RESUME, PlaylistResume.resumeIndex(PLAYLIST, null));
    }

    @Test
    public void partlyWatchedVideoIsResumed() {
        assertEquals(1, PlaylistResume.resumeIndex(PLAYLIST, played("https://b", 89)));
    }

    @Test
    public void startedWithoutSavedProgressIsResumed() {
        assertEquals(1, PlaylistResume.resumeIndex(PLAYLIST, played("https://b", 0)));
    }

    @Test
    public void videoWatchedPastNinetyPercentResumesFromTheNext() {
        assertEquals(2, PlaylistResume.resumeIndex(PLAYLIST, played("https://b", 90)));
    }

    @Test
    public void watchedLastVideoMeansNoResume() {
        assertEquals(NO_RESUME, PlaylistResume.resumeIndex(PLAYLIST, played("https://c", 100)));
    }

    @Test
    public void unknownDurationIsNeverWatched() {
        final LatestPlayedStream live = new LatestPlayedStream(SERVICE, "https://b", 0, 0, 5000);
        assertEquals(1, PlaylistResume.resumeIndex(PLAYLIST, live));
    }

    @Test
    public void sameUrlOnAnotherServiceDoesNotMatch() {
        final LatestPlayedStream other = new LatestPlayedStream(SERVICE + 1, "https://b", 100,
                0, 0);
        assertEquals(NO_RESUME, PlaylistResume.resumeIndex(PLAYLIST, other));
    }

    @Test
    public void emptyPlaylistMeansNoResume() {
        assertEquals(NO_RESUME,
                PlaylistResume.resumeIndex(Collections.emptyList(), played("https://a", 10)));
    }
}
