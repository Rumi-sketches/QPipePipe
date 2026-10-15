package org.schabi.newpipe.qpipepipe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.util.AnnouncementParser;

import java.util.regex.Pattern;

/**
 * Startup notices: the What's New dialog and the announcements. Each notice says whether it
 * comes from PipePipe (upstream) or from QPipePipe, so users know which app it is about.
 */
public final class QPipePipeNotices {

    private static final String ANNOUNCEMENT_URL =
            "https://github.com/Rumi-sketches/QPipePipe/wiki/Announcement";
    private static final String LAST_ANNOUNCEMENT_ID_KEY = "qpipepipe_last_announcement_id";
    /** Announcement headings are dates in yyMMdd format, as on upstream's page. */
    private static final Pattern ANNOUNCEMENT_ID = Pattern.compile("[0-9]{6}");
    /** Version codes are upstream code * 1000 + fork release * 10 + ABI (qpipepipe.gradle). */
    private static final int UPSTREAM_VERSION_CODE_FACTOR = 1000;

    private QPipePipeNotices() {
    }

    /** Whether this version's notes were not shown yet; fork releases count too. */
    public static boolean hasReleaseNotes(final int lastShownVersionCode) {
        return BuildConfig.VERSION_CODE > lastShownVersionCode;
    }

    /**
     * QPipePipe's notes ({@code qpipepipe_update_log}), followed by upstream's
     * ({@code update_log}) when the upstream release changed since the last notes shown.
     */
    @NonNull
    public static String getReleaseNotes(@NonNull final Context context,
                                         final int lastShownVersionCode) {
        final StringBuilder notes = new StringBuilder("QPipePipe ")
                .append(BuildConfig.VERSION_NAME).append(":\n\n")
                .append(context.getString(R.string.qpipepipe_update_log));
        if (lastShownVersionCode / UPSTREAM_VERSION_CODE_FACTOR
                != BuildConfig.VERSION_CODE / UPSTREAM_VERSION_CODE_FACTOR) {
            notes.append("\n\nPipePipe ")
                    .append(BuildConfig.VERSION_NAME.replaceFirst("-q[0-9]+$", ""))
                    .append(":\n\n")
                    .append(context.getString(R.string.update_log));
        }
        return notes.toString();
    }

    /** Title of a notice that comes from upstream, e.g. "PipePipe: Announcement". */
    @NonNull
    public static String fromPipePipe(@NonNull final Context context,
                                      @StringRes final int title) {
        return "PipePipe: " + context.getString(title);
    }

    /**
     * Shows the QPipePipe announcements published since the last one the user dismissed.
     * They live on the QPipePipe wiki in the same format as upstream's page.
     */
    public static void showAnnouncements(@NonNull final Activity activity) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        final String lastId = prefs.getString(LAST_ANNOUNCEMENT_ID_KEY, null);
        try {
            NewPipe.getDownloader().getAsync(ANNOUNCEMENT_URL, response -> {
                final AnnouncementParser.ParsedResult result;
                try {
                    result = AnnouncementParser.parseContentsBeforeId(
                            response.responseBody(), lastId);
                } catch (final RuntimeException e) {
                    return;
                }
                // Without the page GitHub serves the wiki home, whose headings are not dates.
                if (result.latestId == null
                        || !ANNOUNCEMENT_ID.matcher(result.latestId).matches()) {
                    return;
                }
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (activity.isFinishing()) {
                        return;
                    }
                    new AlertDialog.Builder(activity)
                            .setTitle("QPipePipe: " + activity.getString(R.string.announcement))
                            .setMessage(result.contents)
                            .setPositiveButton(R.string.ok, (dialog, which) -> prefs.edit()
                                    .putString(LAST_ANNOUNCEMENT_ID_KEY, result.latestId)
                                    .apply())
                            .show();
                });
            });
        } catch (final Exception ignored) {
            // Announcements are best effort, like upstream's.
        }
    }
}
