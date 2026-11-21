package org.schabi.newpipe.qpipepipe;

import android.app.Activity;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.R;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * A nightly says what to test with it: {@code src/debug/assets/debug_build.txt} (not in git, see
 * {@code app/qpipepipe.gradle}) holds the notes below its first line. They are shown once per
 * nightly number ({@code BuildConfig.QPIPEPIPE_NIGHTLY}).
 */
public final class DebugBuildNotes {
    private static final String ASSET = "debug_build.txt";
    private static final String SHOWN_KEY = "qpipepipe_debug_build_shown";

    private DebugBuildNotes() {
    }

    public static void showIfNew(@NonNull final Activity activity) {
        final int number = BuildConfig.QPIPEPIPE_NIGHTLY;
        if (!BuildConfig.DEBUG || number <= 0) {
            return;
        }
        final String notes = notesOf(readAsset(activity));
        if (notes.isEmpty()) {
            return;
        }
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        if (prefs.getInt(SHOWN_KEY, 0) >= number) {
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.app_name))
                .setMessage(notes)
                .setPositiveButton(R.string.ok, null)
                .setOnDismissListener(dialog ->
                        prefs.edit().putInt(SHOWN_KEY, number).apply())
                .show();
    }

    /** @return the lines of {@code file} below the first, which holds the number */
    @VisibleForTesting
    @NonNull
    static String notesOf(@Nullable final String file) {
        if (file == null) {
            return "";
        }
        final String content = file.replace("\r\n", "\n");
        final int newline = content.indexOf('\n');
        return newline < 0 ? "" : content.substring(newline + 1).trim();
    }

    @Nullable
    private static String readAsset(@NonNull final Activity activity) {
        try (InputStream in = activity.getAssets().open(ASSET)) {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (final IOException e) {
            // no notes
            return null;
        }
    }
}
