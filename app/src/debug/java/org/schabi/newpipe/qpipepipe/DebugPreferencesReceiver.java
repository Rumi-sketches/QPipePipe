package org.schabi.newpipe.qpipepipe;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.util.Base64;

import androidx.preference.PreferenceManager;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Debug builds only: lets {@code tools/qcheck} read and replace the app preferences over adb
 * on phones where {@code run-as} is blocked (some custom ROMs lack its SELinux rule).
 *
 * <pre>
 * adb shell am broadcast -n &lt;app id&gt;/org.schabi.newpipe.qpipepipe.DebugPreferencesReceiver \
 *     --es op read
 * adb shell am broadcast -n &lt;app id&gt;/org.schabi.newpipe.qpipepipe.DebugPreferencesReceiver \
 *     --es op write --es xml &lt;base64 of the preferences XML&gt;
 * </pre>
 *
 * <p>The broadcast result is the preferences XML in base64 for {@code read}, {@code ok} for
 * {@code write}. The receiver requires {@code android.permission.DUMP}, which adb's shell
 * has and other apps cannot get.</p>
 */
public final class DebugPreferencesReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, final Intent intent) {
        final PendingResult result = goAsync();
        final File file = new File(context.getApplicationInfo().dataDir,
                "shared_prefs/" + context.getPackageName() + "_preferences.xml");
        // Waits for the writes the app queued while starting, so the file is complete and
        // none of them lands after a "write".
        PreferenceManager.getDefaultSharedPreferences(context).edit().commit();
        try {
            if ("write".equals(intent.getStringExtra("op"))) {
                writeFile(file, Base64.decode(intent.getStringExtra("xml"), Base64.DEFAULT));
                result.setResultData("ok");
                result.finish();
                // This process still holds the old values in memory; end it so the next
                // start reads the new file.
                Process.killProcess(Process.myPid());
            } else {
                result.setResultData(Base64.encodeToString(readFile(file), Base64.NO_WRAP));
                result.finish();
            }
        } catch (final IOException | IllegalArgumentException e) {
            result.setResultData("error: " + e.getMessage());
            result.finish();
        }
    }

    private static byte[] readFile(final File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private static void writeFile(final File file, final byte[] content) throws IOException {
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(content);
        }
    }
}
