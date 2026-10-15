package org.schabi.newpipe.qpipepipe;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.database.history.dao.StreamHistoryDAO;
import org.schabi.newpipe.database.history.model.LatestPlayedStream;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Finds where a playlist should resume: the video started most recently according to the
 * watch history, or the one after it when that video was watched past
 * {@link #FINISHED_RATIO} of its length.
 */
public final class PlaylistResume {

    /** Share of a video that counts as watched, so playback resumes from the next one. */
    static final double FINISHED_RATIO = 0.9;

    /** Urls per query, under SQLite's limit of 999 bound parameters. */
    private static final int URLS_PER_QUERY = 500;

    /** Returned when there is no point to resume from. */
    public static final int NO_RESUME = -1;

    private PlaylistResume() {
    }

    /**
     * @return the index in {@code items} to resume from, or {@link #NO_RESUME} when none of
     * the items was ever started or the last one started was the final one and is watched
     */
    public static Single<Integer> findResumeIndex(@NonNull final Context context,
                                                  @NonNull final List<StreamInfoItem> items) {
        final StreamHistoryDAO historyDao =
                NewPipeDatabase.getInstance(context).streamHistoryDAO();
        return Single.fromCallable(() -> resumeIndex(items, latestPlayed(historyDao, items)))
                .subscribeOn(Schedulers.io());
    }

    @Nullable
    private static LatestPlayedStream latestPlayed(@NonNull final StreamHistoryDAO historyDao,
                                                   @NonNull final List<StreamInfoItem> items) {
        final List<String> urls = new ArrayList<>(new LinkedHashSet<>(urlsOf(items)));
        LatestPlayedStream latest = null;
        for (int from = 0; from < urls.size(); from += URLS_PER_QUERY) {
            final LatestPlayedStream candidate = historyDao.getLatestPlayedAmong(
                    urls.subList(from, Math.min(from + URLS_PER_QUERY, urls.size())));
            if (candidate != null && (latest == null
                    || candidate.getAccessDateMillis() > latest.getAccessDateMillis())) {
                latest = candidate;
            }
        }
        return latest;
    }

    private static List<String> urlsOf(@NonNull final List<StreamInfoItem> items) {
        final List<String> urls = new ArrayList<>(items.size());
        for (final StreamInfoItem item : items) {
            urls.add(item.getUrl());
        }
        return urls;
    }

    static int resumeIndex(@NonNull final List<StreamInfoItem> items,
                           @Nullable final LatestPlayedStream latest) {
        if (latest == null) {
            return NO_RESUME;
        }
        for (int i = 0; i < items.size(); i++) {
            final StreamInfoItem item = items.get(i);
            if (item.getServiceId() == latest.getServiceId()
                    && item.getUrl().equals(latest.getUrl())) {
                if (!isWatched(latest)) {
                    return i;
                }
                return i + 1 < items.size() ? i + 1 : NO_RESUME;
            }
        }
        return NO_RESUME;
    }

    private static boolean isWatched(@NonNull final LatestPlayedStream stream) {
        return stream.getDurationSeconds() > 0
                && stream.getProgressMillis()
                >= stream.getDurationSeconds() * 1000 * FINISHED_RATIO;
    }
}
