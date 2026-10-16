package org.schabi.newpipe.qpipepipe;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.R;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What a local playlist row in Playlists shows while recent queues are on: when it was last
 * played (or "Never played") and how far into it the user got, as a bar. The video count is
 * already on the thumbnail. Filled by
 * {@link org.schabi.newpipe.qpipepipe.recentqueues.RecentQueuesTabs}, read by the playlist rows
 * on the main thread.
 */
public final class PlaylistProgressLines {
    private static final Map<Long, PlaylistProgress> PROGRESS = new HashMap<>();

    private PlaylistProgressLines() {
    }

    public static void update(@NonNull final List<PlaylistProgress> progress) {
        PROGRESS.clear();
        for (final PlaylistProgress playlist : progress) {
            PROGRESS.put(playlist.getPlaylistId(), playlist);
        }
    }

    public static void clear() {
        PROGRESS.clear();
    }

    /** @return the line for the playlist, or null when there is nothing to show */
    @Nullable
    public static String lineFor(@NonNull final Context context, final long playlistId,
                                 final long streamCount) {
        final PlaylistProgress progress = PROGRESS.get(playlistId);
        if (progress == null || streamCount <= 0) {
            return null;
        }
        final Long playedAt = progress.getLastPlayedAt();
        return playedAt == null
                ? context.getString(R.string.playlist_never_played)
                : dateTime(context, playedAt);
    }

    /**
     * @return how far into the playlist the user got, from 0 to 100, or -1 when there is
     * nothing to show
     */
    public static int percentFor(final long playlistId, final long streamCount) {
        final PlaylistProgress progress = PROGRESS.get(playlistId);
        if (progress == null || streamCount <= 0) {
            return -1;
        }
        final Integer position = progress.getLastPlayedPosition();
        return position == null ? 0 : (int) ((position + 1) * 100 / streamCount);
    }

    /**
     * Shows the progress bar of the row, in the layouts that have one
     * ({@code qpipepipe_playlist_progress}).
     */
    public static void bindBar(@NonNull final View row, final long playlistId,
                               final long streamCount) {
        final ProgressBar bar = row.findViewById(R.id.qpipepipe_playlist_progress);
        if (bar == null) {
            return;
        }
        final int percent = percentFor(playlistId, streamCount);
        bar.setVisibility(percent < 0 ? View.GONE : View.VISIBLE);
        bar.setProgress(Math.max(0, percent));
    }

    /** Date and time as shown on the playlist and queue rows. */
    @NonNull
    public static String dateTime(@NonNull final Context context, final long millis) {
        return DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_DATE
                | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_NUMERIC_DATE
                | DateUtils.FORMAT_SHOW_YEAR);
    }
}
