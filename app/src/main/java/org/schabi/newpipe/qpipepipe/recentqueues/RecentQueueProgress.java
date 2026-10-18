package org.schabi.newpipe.qpipepipe.recentqueues;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.schabi.newpipe.database.history.dao.StreamHistoryDAO;
import org.schabi.newpipe.qpipepipe.PlaylistResume;
import org.schabi.newpipe.qpipepipe.StreamProgress;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Watch progress of the videos of a queue. Queues live in their own database, so the progress
 * is looked up by url in the watch history.
 */
final class RecentQueueProgress {
    /** SQLite allows 999 parameters per query. */
    private static final int URLS_PER_QUERY = 500;

    private RecentQueueProgress() {
    }

    /** @return the saved progress of the videos at {@code urls} that have one, by url */
    @WorkerThread
    @NonNull
    static Map<String, StreamProgress> load(@NonNull final StreamHistoryDAO historyDao,
                                            @NonNull final List<String> urls) {
        final Map<String, StreamProgress> progress = new HashMap<>();
        for (int from = 0; from < urls.size(); from += URLS_PER_QUERY) {
            for (final StreamProgress p : historyDao.getProgressOf(
                    urls.subList(from, Math.min(from + URLS_PER_QUERY, urls.size())))) {
                progress.put(p.getUrl(), p);
            }
        }
        return progress;
    }

    /** Watched means played past {@link PlaylistResume#FINISHED_RATIO} of its length. */
    static boolean isWatched(@Nullable final StreamProgress progress) {
        return progress != null && progress.getDurationSeconds() > 0
                && progress.getProgressMillis()
                >= progress.getDurationSeconds() * 1000 * PlaylistResume.FINISHED_RATIO;
    }

    /** @return how many of the videos at {@code urls} were watched */
    static int countWatched(@NonNull final List<String> urls,
                            @NonNull final Map<String, StreamProgress> progress) {
        int watched = 0;
        for (final String url : urls) {
            if (isWatched(progress.get(url))) {
                watched++;
            }
        }
        return watched;
    }

    /** Percentage of watched videos, for the progress bars. */
    static int percent(final int watched, final int count) {
        return count <= 0 ? 0 : watched * 100 / count;
    }
}
