package org.schabi.newpipe.qpipepipe;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.SwitchPreferenceCompat;

import org.schabi.newpipe.R;
import org.schabi.newpipe.settings.BasePreferenceFragment;

/** Settings screen grouping every feature added by QPipePipe. */
public class QPipePipeSettingsFragment extends BasePreferenceFragment {

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        addPreferencesFromResourceRegistry();
        initSwipeActionsPreference();
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
}
