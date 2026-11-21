package org.schabi.newpipe.qpipepipe.recentqueues;

import androidx.annotation.NonNull;

import org.schabi.newpipe.player.mediaitem.PlayerMediaItem;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A copy of a play queue taken on the main thread, written to the database on another one. */
final class RecentQueueSnapshot {
    @NonNull
    final RecentQueueEntity queue;
    @NonNull
    final List<PlayerMediaItem> items;
    /** Urls of the videos just added to the linked playlist. */
    @NonNull
    final List<String> addedUrls;

    RecentQueueSnapshot(@NonNull final RecentQueueEntity queue,
                        @NonNull final List<PlayerMediaItem> items,
                        @NonNull final Collection<String> addedUrls) {
        this.queue = queue;
        this.items = items;
        this.addedUrls = new ArrayList<>(addedUrls);
    }

    int countAdded(@NonNull final Collection<String> addedUrls) {
        int count = 0;
        for (final boolean added : addedMarks(addedUrls)) {
            if (added) {
                count++;
            }
        }
        return count;
    }

    @NonNull
    List<RecentQueueStreamEntity> streamsFor(final long queueId,
                                             @NonNull final Collection<String> addedUrls) {
        final boolean[] added = addedMarks(addedUrls);
        final List<RecentQueueStreamEntity> streams = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            streams.add(RecentQueueStreamEntity.from(items.get(i), queueId, i, added[i]));
        }
        return streams;
    }

    /**
     * Marks the first occurrence of each added url: a video enqueued twice went into the
     * playlist once.
     */
    @NonNull
    private boolean[] addedMarks(@NonNull final Collection<String> addedUrls) {
        final Set<String> marked = new HashSet<>();
        final boolean[] added = new boolean[items.size()];
        for (int i = 0; i < items.size(); i++) {
            final String url = items.get(i).getUrl();
            added[i] = addedUrls.contains(url) && marked.add(url);
        }
        return added;
    }
}
