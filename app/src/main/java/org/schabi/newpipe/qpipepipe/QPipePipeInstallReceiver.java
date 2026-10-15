package org.schabi.newpipe.qpipepipe;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

import org.schabi.newpipe.R;

/**
 * Receives the result of an update started by {@link QPipePipeUpdateActivity}: opens
 * Android's install confirmation, or reports why the install failed. On success the app is
 * replaced and this receiver gets nothing more to do.
 */
public final class QPipePipeInstallReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, final Intent intent) {
        final int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            final Intent confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirmation != null) {
                // QPipePipeUpdateActivity is still in the foreground, so this start is allowed.
                context.startActivity(confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        } else if (status != PackageInstaller.STATUS_SUCCESS
                && status != PackageInstaller.STATUS_FAILURE_ABORTED) { // aborted: user said no
            final String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            Toast.makeText(context, context.getString(R.string.qpipepipe_update_failed,
                    message != null ? message : String.valueOf(status)), Toast.LENGTH_LONG).show();
        }
    }
}
