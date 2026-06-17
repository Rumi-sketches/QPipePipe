package org.schabi.newpipe.info_list.swipe;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;

/**
 * Central place to read the "swipe actions on videos" preferences (see
 * {@code gesture_settings.xml}), so the various places that need to know whether the
 * feature is on, and which action is bound to which direction, don't each re-derive it.
 */
public final class SwipeActionPreferences {

    private SwipeActionPreferences() {
    }

    public static boolean isEnabled(@NonNull final Context context) {
        return preferences(context)
                .getBoolean(context.getString(R.string.enable_swipe_actions_key), false);
    }

    @NonNull
    public static SwipeAction getLeftAction(@NonNull final Context context) {
        final String value = preferences(context)
                .getString(context.getString(R.string.swipe_left_action_key), null);
        return SwipeAction.fromPreferenceValue(value, SwipeAction.ENQUEUE);
    }

    @NonNull
    public static SwipeAction getRightAction(@NonNull final Context context) {
        final String value = preferences(context)
                .getString(context.getString(R.string.swipe_right_action_key), null);
        return SwipeAction.fromPreferenceValue(value, SwipeAction.APPEND_PLAYLIST);
    }

    private static SharedPreferences preferences(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }
}
