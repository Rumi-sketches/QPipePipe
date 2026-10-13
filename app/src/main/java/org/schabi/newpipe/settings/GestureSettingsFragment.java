package org.schabi.newpipe.settings;

import android.content.res.Resources;
import android.os.Bundle;
import android.text.format.DateUtils;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.ListPreference;
import androidx.preference.SwitchPreferenceCompat;

import org.schabi.newpipe.R;

import java.util.LinkedList;
import java.util.List;

public class GestureSettingsFragment extends BasePreferenceFragment {

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        addPreferencesFromResourceRegistry();
        updateSeekOptions();
        initSwipeActionsPreference();
        setupVerticalSwipeGestureMutualExclusion();
    }

    /**
     * Warns the user, the first time they turn on swipe actions, that list view will be
     * forced wherever swipe actions are available. The preference itself is only flipped
     * if the user confirms; otherwise the switch is reverted.
     */
    private void initSwipeActionsPreference() {
        final SwitchPreferenceCompat enableSwipeActions =
                findPreference(getString(R.string.enable_swipe_actions_key));
        if (enableSwipeActions == null) {
            return;
        }

        enableSwipeActions.setOnPreferenceChangeListener((preference, newValue) -> {
            if (Boolean.TRUE.equals(newValue)) {
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.enable_swipe_actions_warning_title)
                        .setMessage(R.string.enable_swipe_actions_warning_message)
                        .setPositiveButton(android.R.string.ok, (dialog, which) ->
                                enableSwipeActions.setChecked(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                // The dialog applies the change itself on confirmation, so block the
                // automatic update here to avoid flipping it before the user decides.
                return false;
            }
            return true;
        });
    }

    /**
     * Exiting fullscreen, playback speed and minimizing all want the vertical swipe in the middle
     * of the player while in fullscreen, so only one of them can own it.
     */
    private void setupVerticalSwipeGestureMutualExclusion() {
        final SwitchPreferenceCompat fullscreenPref = findPreference(
                getString(R.string.fullscreen_gesture_control_key));
        final SwitchPreferenceCompat speedPref = findPreference(
                getString(R.string.playback_speed_gesture_control_key));
        final ListPreference minimizePref = findPreference(
                getString(R.string.minimize_gesture_control_key));

        if (fullscreenPref == null || speedPref == null || minimizePref == null) {
            return;
        }

        fullscreenPref.setOnPreferenceChangeListener((pref, newValue) -> {
            if (Boolean.TRUE.equals(newValue)) {
                speedPref.setChecked(false);
                releaseFullscreenMinimizeMode(minimizePref);
            }
            return true;
        });

        speedPref.setOnPreferenceChangeListener((pref, newValue) -> {
            if (Boolean.TRUE.equals(newValue)) {
                fullscreenPref.setChecked(false);
                releaseFullscreenMinimizeMode(minimizePref);
            }
            return true;
        });

        minimizePref.setOnPreferenceChangeListener((pref, newValue) -> {
            if (getString(R.string.minimize_gesture_fullscreen_key).equals(newValue)) {
                fullscreenPref.setChecked(false);
                speedPref.setChecked(false);
            }
            return true;
        });
    }

    /**
     * Minimizing outside the fullscreen player does not collide with the other vertical gestures,
     * so enabling one of them only drops the minimize mode back to that.
     */
    private void releaseFullscreenMinimizeMode(final ListPreference minimizePref) {
        if (getString(R.string.minimize_gesture_fullscreen_key).equals(minimizePref.getValue())) {
            minimizePref.setValue(getString(R.string.minimize_gesture_non_fullscreen_key));
        }
    }

    private void updateSeekOptions() {
        final Resources res = getResources();
        final String[] durationsValues = res.getStringArray(R.array.seek_duration_value);
        final List<String> displayedDurationValues = new LinkedList<>();
        final List<String> displayedDescriptionValues = new LinkedList<>();
        int currentDurationValue;

        for (final String durationsValue : durationsValues) {
            currentDurationValue =
                    Integer.parseInt(durationsValue) / (int) DateUtils.SECOND_IN_MILLIS;

            displayedDurationValues.add(durationsValue);
            try {
                displayedDescriptionValues.add(String.format(
                        res.getQuantityString(R.plurals.seconds,
                                currentDurationValue),
                        currentDurationValue));
            } catch (final Resources.NotFoundException ignored) {
            }
        }

        final ListPreference durations = findPreference(
                getString(R.string.seek_duration_key));
        durations.setEntryValues(displayedDurationValues.toArray(new CharSequence[0]));
        durations.setEntries(displayedDescriptionValues.toArray(new CharSequence[0]));
    }
}
