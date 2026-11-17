package org.schabi.newpipe.qpipepipe.player;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.player.mediaitem.PlayerMediaItem;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** "Play next" as PlayerStartController applies it to the queue of the running player. */
public class EnqueueNextTest {

    private static StreamInfoItem item(final String url) {
        final StreamInfoItem item = new StreamInfoItem(0, url, url, StreamType.VIDEO_STREAM);
        item.setUploaderName("u");
        // the selection hands StreamEntity rows, turned into items like ForkSelectionActions
        return new StreamEntity(item).toStreamInfoItem();
    }

    private static PlayQueue queue(final int index, final String... urls) {
        final List<StreamInfoItem> items = new ArrayList<>();
        for (final String url : urls) {
            items.add(item(url));
        }
        return new SinglePlayQueue(items, index);
    }

    private static List<String> urls(final PlayQueue queue) {
        final List<String> result = new ArrayList<>();
        for (final PlayerMediaItem i : queue.getStreams()) {
            result.add(i.getUrl());
        }
        return result;
    }

    /** The enqueue-next branch of PlayerStartController.handleIntent. */
    private static void enqueueNext(final PlayQueue playing, final PlayQueue newQueue) {
        final int currentIndex = playing.getIndex();
        playing.append(newQueue.getStreams());
        EnqueueNext.moveAppendedAfter(playing, newQueue.size(), currentIndex);
    }

    @Test
    public void severalVideosGoRightAfterTheCurrentOneInTheirOrder() {
        final PlayQueue playing = queue(0, "X", "Y", "Z");
        enqueueNext(playing, queue(0, "A", "B", "C"));
        assertEquals(Arrays.asList("X", "A", "B", "C", "Y", "Z"), urls(playing));
        assertEquals(0, playing.getIndex());
    }

    @Test
    public void currentInTheMiddleKeepsPlaying() {
        final PlayQueue playing = queue(1, "X", "Y", "Z");
        enqueueNext(playing, queue(0, "A", "B"));
        assertEquals(Arrays.asList("X", "Y", "A", "B", "Z"), urls(playing));
        assertEquals(1, playing.getIndex());
    }

    @Test
    public void oneVideoBehavesAsUpstream() {
        final PlayQueue playing = queue(0, "X", "Y");
        enqueueNext(playing, queue(0, "A"));
        assertEquals(Arrays.asList("X", "A", "Y"), urls(playing));
    }

    @Test
    public void lastVideoPlayingAppendsInOrder() {
        final PlayQueue playing = queue(1, "X", "Y");
        enqueueNext(playing, queue(0, "A", "B"));
        assertEquals(Arrays.asList("X", "Y", "A", "B"), urls(playing));
    }
}
