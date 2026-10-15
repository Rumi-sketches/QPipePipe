package org.schabi.newpipe.qpipepipe;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.pm.PackageInfoCompat;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.ThemeHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Downloads a QPipePipe release APK and installs it with {@link PackageInstaller}, opened
 * from the update notification ({@code NewVersionWorker}). Android shows its own
 * confirmation and only accepts an APK signed with the installed app's certificate.
 */
public final class QPipePipeUpdateActivity extends AppCompatActivity {

    private static final String EXTRA_VERSION = "version";
    private static final String EXTRA_APK_URL = "apk_url";
    private static final String EXTRA_RELEASE_URL = "release_url";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean cancelled;
    private boolean waitingForConfirmation;

    /** Intent for the update notification; without an APK it opens the release page. */
    @NonNull
    public static Intent intent(@NonNull final Context context, @NonNull final String version,
                                @Nullable final String apkUrl,
                                @Nullable final String releaseUrl) {
        if (apkUrl == null) {
            return new Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        return new Intent(context, QPipePipeUpdateActivity.class)
                .putExtra(EXTRA_VERSION, version)
                .putExtra(EXTRA_APK_URL, apkUrl)
                .putExtra(EXTRA_RELEASE_URL, releaseUrl)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        ThemeHelper.setDayNightMode(this);
        setTheme(ThemeHelper.isLightThemeSelected(this)
                ? R.style.RouterActivityThemeLight : R.style.RouterActivityThemeDark);
        Localization.assureCorrectAppLanguage(this);
        super.onCreate(savedInstanceState);

        final String version = getIntent().getStringExtra(EXTRA_VERSION);
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.qpipepipe_update_title, version))
                .setMessage(R.string.qpipepipe_update_message)
                .setPositiveButton(R.string.qpipepipe_update_install, (dialog, which) ->
                        download(version))
                .setNeutralButton(R.string.qpipepipe_update_release_notes, (dialog, which) ->
                        openReleasePage())
                .setNegativeButton(R.string.qpipepipe_update_later, (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Android's install confirmation now covers this screen; nothing left to show.
        if (waitingForConfirmation) {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        cancelled = true;
        executor.shutdownNow();
        super.onDestroy();
    }

    private void download(final String version) {
        final ProgressBar progress =
                new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        final int padding = (int) (24 * getResources().getDisplayMetrics().density);
        progress.setPadding(padding, padding / 2, padding, padding / 2);
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.qpipepipe_update_downloading, version))
                .setView(progress)
                .setNegativeButton(android.R.string.cancel, (d, which) -> finish())
                .setOnCancelListener(d -> finish())
                .show();

        final File apk = new File(new File(getCacheDir(), "updates"), "QPipePipe-" + version + ".apk");
        final String apkUrl = getIntent().getStringExtra(EXTRA_APK_URL);
        executor.execute(() -> {
            try {
                downloadTo(apkUrl, apk, percent -> mainHandler.post(() ->
                        progress.setProgress(percent)));
                mainHandler.post(() -> {
                    dialog.dismiss();
                    install(apk);
                });
            } catch (final IOException e) {
                if (!cancelled) {
                    mainHandler.post(() -> {
                        dialog.dismiss();
                        showError(getString(R.string.qpipepipe_update_download_failed));
                    });
                }
            }
        });
    }

    private interface ProgressListener {
        void onProgress(int percent);
    }

    private void downloadTo(final String url, final File apk, final ProgressListener listener)
            throws IOException {
        final File folder = apk.getParentFile();
        if (folder.exists()) {
            // Leftovers from earlier updates.
            for (final File old : folder.listFiles()) {
                old.delete();
            }
        } else if (!folder.mkdirs()) {
            throw new IOException("Cannot create " + folder);
        }
        final HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(30_000);
        connection.setReadTimeout(30_000);
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + connection.getResponseCode());
            }
            final long total = connection.getContentLengthLong();
            try (InputStream in = connection.getInputStream();
                 OutputStream out = new FileOutputStream(apk)) {
                final byte[] buffer = new byte[64 * 1024];
                long done = 0;
                int read;
                while ((read = in.read(buffer)) != -1) {
                    if (cancelled) {
                        throw new IOException("Cancelled");
                    }
                    out.write(buffer, 0, read);
                    done += read;
                    if (total > 0) {
                        listener.onProgress((int) (done * 100 / total));
                    }
                }
            }
        } finally {
            connection.disconnect();
        }
    }

    private void install(final File apk) {
        // Android refuses an APK signed by someone else; check here what it would not
        // explain well: another app id (e.g. a debug build) or an older version.
        final PackageManager packageManager = getPackageManager();
        final PackageInfo archive = packageManager.getPackageArchiveInfo(apk.getPath(), 0);
        final PackageInfo installed;
        try {
            installed = packageManager.getPackageInfo(getPackageName(), 0);
        } catch (final PackageManager.NameNotFoundException e) {
            throw new IllegalStateException(e);
        }
        if (archive == null || !getPackageName().equals(archive.packageName)
                || PackageInfoCompat.getLongVersionCode(archive)
                <= PackageInfoCompat.getLongVersionCode(installed)) {
            apk.delete();
            showError(getString(R.string.qpipepipe_update_wrong_build));
            return;
        }

        final PackageInstaller installer = packageManager.getPackageInstaller();
        final PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(getPackageName());
        try {
            final int sessionId = installer.createSession(params);
            try (PackageInstaller.Session session = installer.openSession(sessionId)) {
                try (InputStream in = new FileInputStream(apk);
                     OutputStream out = session.openWrite("QPipePipe.apk", 0, apk.length())) {
                    final byte[] buffer = new byte[64 * 1024];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                    session.fsync(out);
                }
                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // PackageInstaller adds the result to the intent.
                    flags |= PendingIntent.FLAG_MUTABLE;
                }
                final PendingIntent result = PendingIntent.getBroadcast(this, sessionId,
                        new Intent(this, QPipePipeInstallReceiver.class), flags);
                waitingForConfirmation = true;
                session.commit(result.getIntentSender());
            }
        } catch (final IOException | SecurityException e) {
            waitingForConfirmation = false;
            showError(getString(R.string.qpipepipe_update_failed, e.getMessage()));
        }
    }

    private void openReleasePage() {
        final String releaseUrl = getIntent().getStringExtra(EXTRA_RELEASE_URL);
        if (releaseUrl != null) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl)));
        }
        finish();
    }

    private void showError(final String message) {
        if (isFinishing()) {
            Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton(R.string.qpipepipe_update_release_notes, (d, w) ->
                        openReleasePage())
                .setNegativeButton(android.R.string.cancel, (d, w) -> finish())
                .setOnCancelListener(d -> finish())
                .show();
    }
}
