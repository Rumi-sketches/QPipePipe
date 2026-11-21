package org.schabi.newpipe.qpipepipe;

import android.content.Context;
import android.text.format.DateUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;

import org.schabi.newpipe.R;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What a local playlist row in Playlists shows while recent queues are on: when it was last
 * played (or "Never played") and the share of its videos watched, as a bar. The video count is
 * already on the thumbnail. Filled by
 * {@link org.schabi.newpipe.qpipepipe.recentqueues.RecentQueuesTabs}, read by the playlist rows
 * on the main thread.
 */
public final class PlaylistProgressLines {
    private static final Map<Long, PlaylistProgress> PROGRESS = new HashMap<>();
    private static final String BAR_TAG = "qpipepipe_playlist_progress";

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
     * @return the share of watched videos, from 0 to 100, or -1 when there is nothing to show
     */
    public static int percentFor(final long playlistId, final long streamCount) {
        final PlaylistProgress progress = PROGRESS.get(playlistId);
        if (progress == null || streamCount <= 0) {
            return -1;
        }
        return (int) (Math.min(progress.getWatchedCount(), streamCount) * 100 / streamCount);
    }

    /**
     * Shows the progress bar under the line of the row. The bar is added from here, below
     * {@code itemUploaderView}, so the upstream row layouts stay untouched.
     */
    public static void bindBar(@NonNull final View row, final long playlistId,
                               final long streamCount) {
        final int percent = percentFor(playlistId, streamCount);
        ProgressBar bar = row.findViewWithTag(BAR_TAG);
        if (bar == null) {
            if (percent < 0) {
                return;
            }
            bar = addBar(row);
            if (bar == null) {
                return;
            }
        }
        bar.setVisibility(percent < 0 ? View.GONE : View.VISIBLE);
        bar.setProgress(Math.max(0, percent));
    }

    /** @return the new bar, or null when the row has no line to put it under */
    @Nullable
    private static ProgressBar addBar(@NonNull final View row) {
        final View line = row.findViewById(R.id.itemUploaderView);
        if (line == null || !(line.getParent() instanceof RelativeLayout)) {
            return null;
        }
        final Context context = row.getContext();
        final ProgressBar bar = new ProgressBar(context, null,
                android.R.attr.progressBarStyleHorizontal);
        bar.setTag(BAR_TAG);
        bar.setMax(100);
        final TypedValue drawable = new TypedValue();
        if (context.getTheme().resolveAttribute(R.attr.progress_horizontal_drawable, drawable,
                true)) {
            bar.setProgressDrawable(AppCompatResources.getDrawable(context,
                    drawable.resourceId));
        }
        final float density = context.getResources().getDisplayMetrics().density;
        final RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(0,
                Math.round(3 * density));
        params.addRule(RelativeLayout.BELOW, line.getId());
        params.addRule(RelativeLayout.ALIGN_START, line.getId());
        params.addRule(RelativeLayout.ALIGN_END, line.getId());
        params.topMargin = Math.round(4 * density);
        ((RelativeLayout) line.getParent()).addView(bar, params);
        return bar;
    }

    /** Date and time as shown on the playlist and queue rows. */
    @NonNull
    public static String dateTime(@NonNull final Context context, final long millis) {
        return DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_DATE
                | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_NUMERIC_DATE
                | DateUtils.FORMAT_SHOW_YEAR);
    }
}
