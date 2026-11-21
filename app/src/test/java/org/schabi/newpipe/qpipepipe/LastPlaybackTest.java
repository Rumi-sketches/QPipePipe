package org.schabi.newpipe.qpipepipe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

public class LastPlaybackTest {
    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private static StreamInfoItem stream(final String url) {
        final StreamInfoItem item = new StreamInfoItem(0, url, url, StreamType.VIDEO_STREAM);
        item.setUploaderName("uploader");
        return item;
    }

    private File file() {
        return new File(folder.getRoot(), "last_playback.ser");
    }

    @Test
    public void savedQueueComesBackAtTheSameVideo() throws Exception {
        final PlayQueue queue = new SinglePlayQueue(Arrays.asList(
                stream("https://a"), stream("https://b"), stream("https://c")), 1);

        LastPlayback.write(file(), LastPlayback.serialize(queue));
        final PlayQueue restored = LastPlayback.read(file());

        assertNotNull(restored);
        assertEquals(3, restored.size());
        assertEquals(1, restored.getIndex());
        assertEquals("https://b", restored.getItem().getUrl());
    }

    @Test
    public void savingAgainReplacesTheQueue() throws Exception {
        LastPlayback.write(file(), LastPlayback.serialize(new SinglePlayQueue(
                Collections.singletonList(stream("https://old")), 0)));
        LastPlayback.write(file(), LastPlayback.serialize(new SinglePlayQueue(
                Collections.singletonList(stream("https://new")), 0)));

        assertEquals("https://new", LastPlayback.read(file()).getItem().getUrl());
        assertFalse(new File(file().getPath() + ".tmp").exists());
    }

    @Test
    public void nothingSavedMeansNothingToResume() throws Exception {
        assertNull(LastPlayback.read(file()));
    }

    @Test
    public void finishedQueueIsForgotten() throws Exception {
        LastPlayback.write(file(), LastPlayback.serialize(new SinglePlayQueue(
                Collections.singletonList(stream("https://a")), 0)));
        assertTrue(file().exists());

        LastPlayback.clear(file());

        assertNull(LastPlayback.read(file()));
    }

    @Test
    public void unreadableFileIsReported() throws Exception {
        LastPlayback.write(file(), new byte[] {1, 2, 3});
        assertThrows(IOException.class, () -> LastPlayback.read(file()));
    }
}
