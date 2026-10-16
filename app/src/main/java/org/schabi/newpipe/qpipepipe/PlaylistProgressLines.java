package org.schabi.newpipe.qpipepipe;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.Localization;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The line under a local playlist in Playlists while recent queues are on: "13 videos ·
 * 2:20:00 · at video 8 of 13", or "Never played". Filled by
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
        final String countAndDuration = progress.getTotalDuration() > 0
                ? Localization.concatenateStrings(
                        Localization.localizeStreamCount(context, streamCount),
                        Localization.getDurationString(progress.getTotalDuration()))
                : Localization.localizeStreamCount(context, streamCount);
        final Integer position = progress.getLastPlayedPosition();
        return Localization.concatenateStrings(countAndDuration, position == null
                ? context.getString(R.string.playlist_never_played)
                : context.getString(R.string.recent_queue_position, position + 1,
                        (int) streamCount));
    }
}
