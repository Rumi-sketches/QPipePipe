package org.schabi.newpipe.util;

import android.app.ActivityManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.car.app.connection.CarConnection;
import androidx.core.content.ContextCompat;

import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.PlayerBinderInterface;
import org.schabi.newpipe.player.PlayerIntentConstants;
import org.schabi.newpipe.player.PlayerService;
import org.schabi.newpipe.player.PlayerServiceForAuto;

/**
 * Tracks whether the phone is connected to a car, so that playback is routed to the player
 * service Android Auto is bound to (see {@link DeviceUtils#getPlayerServiceClass()}).
 *
 * <p>The state comes from {@link CarConnection#getType()}. The
 * {@code CAR_CONNECTION_UPDATED} broadcast carries no state: the library re-queries Android
 * Auto's content provider every time it is received, so it must not be read from the intent.</p>
 */
public final class CarConnectionMonitor {
    private static final String TAG = "CarConnectionMonitor";

    private static volatile boolean carConnected = false;

    private CarConnectionMonitor() {
    }

    public static boolean isCarConnected() {
        return carConnected;
    }

    /**
     * Observes the car connection for the whole life of the process. The first value arrives
     * asynchronously, so until then the phone is considered not connected.
     */
    public static void start(@NonNull final Context context) {
        final Context appContext = context.getApplicationContext();
        new CarConnection(appContext).getType().observeForever(type ->
                onConnectionChanged(appContext, isConnectedType(type)));
    }

    static boolean isConnectedType(@Nullable final Integer type) {
        return type != null && type != CarConnection.CONNECTION_TYPE_NOT_CONNECTED;
    }

    private static void onConnectionChanged(@NonNull final Context context,
                                            final boolean connected) {
        if (connected == carConnected) {
            return;
        }
        final Class<? extends Service> previousService = DeviceUtils.getPlayerServiceClass();
        carConnected = connected;
        Log.d(TAG, "Car connected: " + connected);

        if (!connected) {
            // Leaving the car pauses playback; the car's player stays usable from the phone
            // until it is closed (see PlayerServiceForAuto.hasActivePlayer())
            PlayerServiceForAuto.pausePlayer();
            PlayerServiceForAuto.onCarDisconnected();
        }

        // The car and the phone would otherwise drive two different players. Entering the car,
        // the car's player takes over what the phone was playing, or showing paused.
        final Class<? extends Service> currentService = DeviceUtils.getPlayerServiceClass();
        if (previousService != currentService) {
            stopIfRunning(context, previousService, connected ? currentService : null);
        }
    }

    /**
     * @param handOverTo the service that continues the stopped player's queue, from the same
     *                   position and playing or paused as it was, or null to just stop it
     */
    private static void stopIfRunning(@NonNull final Context context,
                                      @NonNull final Class<? extends Service> serviceClass,
                                      @Nullable final Class<? extends Service> handOverTo) {
        if (!isRunning(context, serviceClass)) {
            return;
        }
        final ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(final ComponentName name, final IBinder service) {
                final PlayerBinderInterface binder = (PlayerBinderInterface) service;
                final Intent handOver = handOverTo == null
                        ? null : handOverIntent(context, binder.getPlayer(), handOverTo);
                if (handOver != null) {
                    PlayerServiceForAuto.setHandOverPending(true);
                }
                binder.getService().stopService();
                context.unbindService(this);
                if (handOver != null) {
                    startHandOver(context, handOver);
                }
            }

            @Override
            public void onServiceDisconnected(final ComponentName name) {
            }
        };
        // PlayerServiceForAuto only returns the player binder for this action
        final Intent intent = new Intent(context, serviceClass)
                .setAction(PlayerIntentConstants.BIND_PLAYER_HOLDER_ACTION);
        if (!context.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
            context.unbindService(connection);
        }
    }

    @Nullable
    private static Intent handOverIntent(@NonNull final Context context,
                                         @Nullable final Player player,
                                         @NonNull final Class<? extends Service> serviceClass) {
        if (player == null || player.getPlayQueue() == null) {
            return null;
        }
        // the queue records the position only on demand
        player.setRecovery();
        return NavigationHelper.getPlayerIntent(context, serviceClass, player.getPlayQueue(),
                        false, player.getPlayWhenReady())
                .putExtra(PlayerIntentConstants.PLAYER_TYPE,
                        PlayerService.PlayerType.AUDIO.ordinal());
    }

    private static void startHandOver(@NonNull final Context context,
                                      @NonNull final Intent intent) {
        try {
            ContextCompat.startForegroundService(context, intent);
        } catch (final IllegalStateException e) {
            // Android refused to start it from the background: the queue is still saved by
            // LastPlayback, so play in the car resumes it
            Log.w(TAG, "Could not hand the player over to the car", e);
            PlayerServiceForAuto.setHandOverPending(false);
        }
    }

    @SuppressWarnings("deprecation") // still returns the caller's own services
    private static boolean isRunning(@NonNull final Context context,
                                     @NonNull final Class<? extends Service> serviceClass) {
        final ActivityManager activityManager =
                ContextCompat.getSystemService(context, ActivityManager.class);
        if (activityManager == null) {
            return false;
        }
        for (final ActivityManager.RunningServiceInfo info
                : activityManager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(info.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
