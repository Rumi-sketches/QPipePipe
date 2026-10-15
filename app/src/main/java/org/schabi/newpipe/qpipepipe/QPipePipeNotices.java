package org.schabi.newpipe.qpipepipe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.util.AnnouncementParser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Startup notices: the What's New dialog and the announcements. Each notice says whether it
 * comes from PipePipe (upstream) or from QPipePipe, so users know which app it is about.
 */
public final class QPipePipeNotices {

    /** Raw markdown of the wiki page {@code Announcement}. */
    private static final String ANNOUNCEMENT_URL = "https://raw.githubusercontent.com/wiki/"
            + BuildConfig.QPIPEPIPE_GITHUB_REPO + "/Announcement.md";
    private static final String LAST_ANNOUNCEMENT_ID_KEY = "qpipepipe_last_announcement_id";
    private static final String LAST_ANNOUNCEMENT_CHECK_KEY = "qpipepipe_last_announcement_check";
    private static final long ANNOUNCEMENT_CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000;
    /** Announcement headings are dates in yyMMdd format, as on upstream's page. */
    private static final Pattern ANNOUNCEMENT_HEADING = Pattern.compile("##\\s*([0-9]{6})");
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
     * They live on the QPipePipe wiki page {@code Announcement}, one {@code ## yyMMdd}
     * section each, newest first, as on upstream's page.
     *
     * <p>Reads the page's raw markdown, a few hundred bytes, at most every
     * {@link #ANNOUNCEMENT_CHECK_INTERVAL_MS}: fetching and parsing the HTML page on every
     * start added about half a second to video starts.</p>
     */
    public static void showAnnouncements(@NonNull final Activity activity) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        final long now = System.currentTimeMillis();
        if (now - prefs.getLong(LAST_ANNOUNCEMENT_CHECK_KEY, 0) < ANNOUNCEMENT_CHECK_INTERVAL_MS) {
            return;
        }
        prefs.edit().putLong(LAST_ANNOUNCEMENT_CHECK_KEY, now).apply();
        final String lastId = prefs.getString(LAST_ANNOUNCEMENT_ID_KEY, null);
        try {
            NewPipe.getDownloader().getAsync(ANNOUNCEMENT_URL, response -> {
                if (response.responseCode() != 200) {
                    return; // no announcement page yet
                }
                final AnnouncementParser.ParsedResult result =
                        parseAnnouncements(response.responseBody(), lastId);
                if (result == null) {
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

    /**
     * Returns the sections of the announcement page newer than {@code lastId}, formatted like
     * upstream's announcements, or {@code null} when there are none.
     */
    @Nullable
    static AnnouncementParser.ParsedResult parseAnnouncements(@NonNull final String markdown,
                                                             @Nullable final String lastId) {
        final StringBuilder contents = new StringBuilder();
        String latestId = null;
        String currentId = null;
        for (final String rawLine : markdown.split("\r?\n")) {
            final String line = rawLine.trim();
            final Matcher heading = ANNOUNCEMENT_HEADING.matcher(line);
            if (heading.matches()) {
                currentId = heading.group(1);
                // yyMMdd ids sort as strings; stop at the first one already seen.
                if (lastId != null && currentId.compareTo(lastId) <= 0) {
                    break;
                }
                if (latestId == null) {
                    latestId = currentId;
                }
                if (contents.length() > 0) {
                    contents.append('\n');
                }
                contents.append("## ").append(currentId).append('\n');
            } else if (currentId != null && !line.isEmpty() && !line.equals("---")) {
                contents.append(line).append('\n');
            }
        }
        return latestId == null ? null : new AnnouncementParser.ParsedResult(
                contents.toString(), latestId);
    }
}
