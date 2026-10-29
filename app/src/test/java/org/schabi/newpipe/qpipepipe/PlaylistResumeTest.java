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

    /**
     * A 100 second video at {@code url}, last started at {@code accessDate} and watched up to
     * {@code progressSeconds}.
     */
    private static LatestPlayedStream played(final String url, final long accessDate,
                                             final long progressSeconds) {
        return new LatestPlayedStream(SERVICE, url, 100, accessDate, progressSeconds * 1000);
    }

    private static int resumeIndex(final LatestPlayedStream... played) {
        return PlaylistResume.resumeIndex(PLAYLIST, Arrays.asList(played));
    }

    @Test
    public void nothingStartedMeansNoResume() {
        assertEquals(NO_RESUME, resumeIndex());
    }

    @Test
    public void partlyWatchedVideoIsResumed() {
        assertEquals(1, resumeIndex(played("https://b", 1, 89)));
    }

    @Test
    public void videoWatchedPastNinetyPercentResumesFromTheNext() {
        assertEquals(2, resumeIndex(played("https://b", 1, 90)));
    }

    @Test
    public void watchedLastVideoMeansNoResume() {
        assertEquals(NO_RESUME, resumeIndex(played("https://c", 1, 100)));
    }

    @Test
    public void videoFurthestDownTheListWinsOverTheMostRecent() {
        assertEquals(1, resumeIndex(played("https://b", 1, 50), played("https://a", 2, 50)));
    }

    @Test
    public void videoPlayedUnderFivePercentIsIgnored() {
        assertEquals(1, resumeIndex(played("https://b", 1, 50), played("https://c", 2, 4)));
    }

    @Test
    public void videoPlayedFivePercentCounts() {
        assertEquals(2, resumeIndex(played("https://b", 1, 50), played("https://c", 2, 5)));
    }

    @Test
    public void repeatedVideoResumesAtItsLastPlace() {
        final List<StreamInfoItem> repeated = Arrays.asList(
                item("https://a"), item("https://b"), item("https://a"), item("https://c"));
        assertEquals(2, PlaylistResume.resumeIndex(repeated,
                Collections.singletonList(played("https://a", 1, 40))));
    }

    @Test
    public void onlyBrieflyOpenedVideosMeanNoResume() {
        assertEquals(NO_RESUME, resumeIndex(played("https://b", 1, 0), played("https://c", 2, 4)));
    }

    @Test
    public void unknownDurationCountsAndIsNeverWatched() {
        final LatestPlayedStream live = new LatestPlayedStream(SERVICE, "https://b", 0, 1, 0);
        assertEquals(1, resumeIndex(live));
    }

    @Test
    public void sameUrlOnAnotherServiceDoesNotMatch() {
        final LatestPlayedStream other = new LatestPlayedStream(SERVICE + 1, "https://b", 100,
                2, 50 * 1000);
        assertEquals(0, resumeIndex(played("https://a", 1, 50), other));
    }

    @Test
    public void emptyPlaylistMeansNoResume() {
        assertEquals(NO_RESUME, PlaylistResume.resumeIndex(Collections.emptyList(),
                Collections.singletonList(played("https://a", 1, 10))));
    }
}
