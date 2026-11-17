package org.schabi.newpipe.qpipepipe.selection;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.util.Arrays;
import java.util.List;

public class ForkSelectionActionsTest {

    private static StreamInfoItem item(final String url, final StreamType type) {
        return new StreamInfoItem(0, url, url, type);
    }

    @Test
    public void sharedLinksAreOnePerLineInListOrder() {
        final List<StreamInfoItem> items = Arrays.asList(
                item("https://a", StreamType.VIDEO_STREAM),
                item("https://b", StreamType.LIVE_STREAM),
                item("https://c", StreamType.AUDIO_STREAM));
        assertEquals("https://a\nhttps://b\nhttps://c", ForkSelectionActions.joinLinks(items));
    }

    @Test
    public void markAsWatchedSkipsLiveStreamsAndKeepsTheOrder() {
        final StreamInfoItem first = item("https://a", StreamType.VIDEO_STREAM);
        final StreamInfoItem third = item("https://c", StreamType.AUDIO_STREAM);
        final List<StreamInfoItem> items = Arrays.asList(first,
                item("https://b", StreamType.LIVE_STREAM),
                third,
                item("https://d", StreamType.AUDIO_LIVE_STREAM));
        assertEquals(Arrays.asList(first, third), ForkSelectionActions.withoutLiveStreams(items));
    }
}
