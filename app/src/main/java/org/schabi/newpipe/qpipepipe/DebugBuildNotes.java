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
 * Debug builds installed on the user's phone are numbered, and say what to test with them:
 * {@code src/debug/assets/debug_build.txt} (not in git, see {@code app/qpipepipe.gradle}) holds
 * the number on its first line and the notes below it. They are shown once per number.
 */
public final class DebugBuildNotes {
    private static final String ASSET = "debug_build.txt";
    private static final String SHOWN_KEY = "qpipepipe_debug_build_shown";

    private DebugBuildNotes() {
    }

    public static void showIfNew(@NonNull final Activity activity) {
        if (!BuildConfig.DEBUG) {
            return;
        }
        final Notes notes = parse(readAsset(activity));
        if (notes == null) {
            return;
        }
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        if (prefs.getInt(SHOWN_KEY, 0) >= notes.number) {
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.app_name))
                .setMessage(notes.text)
                .setPositiveButton(R.string.ok, null)
                .setOnDismissListener(dialog ->
                        prefs.edit().putInt(SHOWN_KEY, notes.number).apply())
                .show();
    }

    @VisibleForTesting
    static final class Notes {
        final int number;
        @NonNull
        final String text;

        Notes(final int number, @NonNull final String text) {
            this.number = number;
            this.text = text;
        }
    }

    /** @return the number and notes of {@code file}, or null if it has no number */
    @VisibleForTesting
    @Nullable
    static Notes parse(@Nullable final String file) {
        if (file == null) {
            return null;
        }
        final String content = file.replace("\r\n", "\n");
        final int newline = content.indexOf('\n');
        final String first = (newline < 0 ? content : content.substring(0, newline)).trim();
        try {
            return new Notes(Integer.parseInt(first),
                    newline < 0 ? "" : content.substring(newline + 1).trim());
        } catch (final NumberFormatException e) {
            return null;
        }
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
            // not a numbered debug build
            return null;
        }
    }
}
