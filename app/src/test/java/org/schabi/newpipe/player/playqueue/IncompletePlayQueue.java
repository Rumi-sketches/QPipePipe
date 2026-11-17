package org.schabi.newpipe.player.playqueue;

import org.schabi.newpipe.player.mediaitem.PlayerMediaItem;

import java.util.List;

/** A queue that has more items to fetch, like the first page of a remote playlist. */
public final class IncompletePlayQueue extends PlayQueue {
    public IncompletePlayQueue(final List<PlayerMediaItem> items) {
        super(0, items);
    }

    @Override
    public boolean isComplete() {
        return false;
    }

    @Override
    public void fetch() {
        // nothing to fetch in a test
    }
}
