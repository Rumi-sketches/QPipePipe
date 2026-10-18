package org.schabi.newpipe.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;

import androidx.core.content.ContextCompat;

public class MediaButtonReceiver extends BroadcastReceiver {
    private static final String TAG = "MediaButtonReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        // We only care about the MEDIA_BUTTON intent.
        if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
            KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            if (event == null || event.getAction() != KeyEvent.ACTION_DOWN) {
                return;
            }

            // The service recognises media buttons by the action, so it has to be kept
            final Intent serviceIntent = new Intent(Intent.ACTION_MEDIA_BUTTON)
                    .setClass(context, DeviceUtils.getPlayerServiceClass())
                    .putExtras(intent.getExtras());
            try {
                ContextCompat.startForegroundService(context, serviceIntent);
            } catch (final IllegalStateException e) {
                // Android 12+ may refuse to start a foreground service from the background
                // (ForegroundServiceStartNotAllowedException): drop the key press, don't crash
                Log.w(TAG, "Could not start the player service for a media button", e);
            }
        }
    }
}
