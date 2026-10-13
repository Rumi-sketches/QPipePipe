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
}
