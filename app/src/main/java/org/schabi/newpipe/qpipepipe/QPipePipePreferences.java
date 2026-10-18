package org.schabi.newpipe.qpipepipe;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;

/**
 * Reads the QPipePipe preferences (see {@code qpipepipe_settings.xml}) that upstream code
 * paths need. The swipe action preferences have their own reader,
 * {@link org.schabi.newpipe.info_list.swipe.SwipeActionPreferences}.
 */
public final class QPipePipePreferences {

    private QPipePipePreferences() {
    }

    /**
     * Whether opening a video by tapping it starts playback regardless of the Autoplay
     * setting, which then only governs automatic starts.
     */
    public static boolean isTapAlwaysPlays(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.tap_always_plays_key), true);
    }

    public static boolean isClearingQueueConfirmationRequired(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.clear_queue_confirmation_key), false);
    }

    /**
     * Whether the playlist play buttons start from the last video started in that playlist.
     * Set from the switch in the playlist header, one value for the whole app.
     */
    public static boolean isPlaylistResumeEnabled(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.playlist_resume_key), false);
    }

    /** Whether played queues are kept in the recent queues (see {@code qpipepipe/recentqueues}). */
    public static boolean isRecentQueuesEnabled(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.recent_queues_key), false);
    }

    public static void setPlaylistResumeEnabled(@NonNull final Context context,
                                                final boolean enabled) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(context.getString(R.string.playlist_resume_key), enabled)
                .apply();
    }
}
