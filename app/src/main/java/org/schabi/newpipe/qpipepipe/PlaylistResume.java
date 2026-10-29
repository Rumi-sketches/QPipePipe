package org.schabi.newpipe.qpipepipe;

import android.content.Context;

import androidx.annotation.NonNull;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.database.history.dao.StreamHistoryDAO;
import org.schabi.newpipe.database.history.model.LatestPlayedStream;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Finds where a playlist should resume: the video furthest down the list among those played
 * past {@link #STARTED_RATIO} of their length according to the watch history, or the one after
 * it when that video was watched past {@link #FINISHED_RATIO}. Videos only opened for a moment
 * are ignored.
 */
public final class PlaylistResume {

    /** Share of a video that counts as watched, so playback resumes from the next one. */
    public static final double FINISHED_RATIO = 0.9;

    /** Share of a video that must be played for it to count as the one to resume. */
    static final double STARTED_RATIO = 0.05;

    /** Urls per query, under SQLite's limit of 999 bound parameters. */
    private static final int URLS_PER_QUERY = 500;

    /** Returned when there is no point to resume from. */
    public static final int NO_RESUME = -1;

    private PlaylistResume() {
    }

    /**
     * @return the index in {@code items} to resume from, or {@link #NO_RESUME} when none of
     * the items was played past {@link #STARTED_RATIO} or the furthest one played is the final
     * one and is watched
     */
    public static Single<Integer> findResumeIndex(@NonNull final Context context,
                                                  @NonNull final List<StreamInfoItem> items) {
        final StreamHistoryDAO historyDao =
                NewPipeDatabase.getInstance(context).streamHistoryDAO();
        return Single.fromCallable(() -> resumeIndex(items, playedAmong(historyDao, items)))
                .subscribeOn(Schedulers.io());
    }

    private static List<LatestPlayedStream> playedAmong(
            @NonNull final StreamHistoryDAO historyDao,
            @NonNull final List<StreamInfoItem> items) {
        final List<String> urls = new ArrayList<>(new LinkedHashSet<>(urlsOf(items)));
        final List<LatestPlayedStream> played = new ArrayList<>();
        for (int from = 0; from < urls.size(); from += URLS_PER_QUERY) {
            played.addAll(historyDao.getPlayedAmong(
                    urls.subList(from, Math.min(from + URLS_PER_QUERY, urls.size()))));
        }
        return played;
    }

    private static List<String> urlsOf(@NonNull final List<StreamInfoItem> items) {
        final List<String> urls = new ArrayList<>(items.size());
        for (final StreamInfoItem item : items) {
            urls.add(item.getUrl());
        }
        return urls;
    }

    /**
     * @param played the items' streams found in the watch history, in any order
     */
    static int resumeIndex(@NonNull final List<StreamInfoItem> items,
                           @NonNull final List<LatestPlayedStream> played) {
        final Map<String, LatestPlayedStream> started = new HashMap<>();
        for (final LatestPlayedStream stream : played) {
            if (isStarted(stream)) {
                started.put(key(stream.getServiceId(), stream.getUrl()), stream);
            }
        }
        for (int i = items.size() - 1; i >= 0; i--) {
            final StreamInfoItem item = items.get(i);
            final LatestPlayedStream stream = started.get(key(item.getServiceId(), item.getUrl()));
            if (stream != null) {
                if (!isWatched(stream)) {
                    return i;
                }
                return i + 1 < items.size() ? i + 1 : NO_RESUME;
            }
        }
        return NO_RESUME;
    }

    private static String key(final int serviceId, @NonNull final String url) {
        return serviceId + " " + url;
    }

    /** A video with an unknown duration counts as soon as it was started. */
    private static boolean isStarted(@NonNull final LatestPlayedStream stream) {
        return stream.getDurationSeconds() <= 0
                || stream.getProgressMillis()
                >= stream.getDurationSeconds() * 1000 * STARTED_RATIO;
    }

    private static boolean isWatched(@NonNull final LatestPlayedStream stream) {
        return stream.getDurationSeconds() > 0
                && stream.getProgressMillis()
                >= stream.getDurationSeconds() * 1000 * FINISHED_RATIO;
    }
}
