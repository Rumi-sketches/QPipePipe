/*
 * Copyright 2017 Mauricio Colli <mauriciocolli@outlook.com>
 * Part of NewPipe
 *
 * License: GPL-3.0+
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.schabi.newpipe.player;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import androidx.media.MediaBrowserServiceCompat;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;
import org.schabi.newpipe.App;
import org.schabi.newpipe.databinding.PlayerBinding;
import org.schabi.newpipe.player.mediabrowser.MediaBrowserImpl;
import org.schabi.newpipe.player.mediabrowser.MediaBrowserPlaybackPreparer;
import org.schabi.newpipe.player.mediasession.PlayerServiceInterface;
import org.schabi.newpipe.qpipepipe.LastPlayback;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.ThemeHelper;

import java.util.List;

import static org.schabi.newpipe.player.PlayerIntentConstants.BIND_PLAYER_HOLDER_ACTION;
import static org.schabi.newpipe.util.Localization.assureCorrectAppLanguage;


/**
 * One service for all players with Android Auto support.
 *
 * @author mauriciocolli
 */
public final class PlayerServiceForAuto extends MediaBrowserServiceCompat implements PlayerServiceInterface {
    private static final String TAG = "PlayerServiceForAuto";
    private static final boolean DEBUG = Player.DEBUG;

    // These objects are used to cleanly separate the Service implementation (in this file) and the
    // media browser and playback preparer implementations. At the moment the playback preparer is
    // only used in conjunction with the media browser.
    private MediaBrowserImpl mediaBrowserImpl;
    private MediaBrowserPlaybackPreparer mediaBrowserPlaybackPreparer;

    // these are instantiated in onCreate() as per
    // https://developer.android.com/training/cars/media#browser_workflow
    private MediaSessionCompat mediaSession;
    private MediaSessionConnector sessionConnector;

    private Player player;
    private WindowManager windowManager;

    private final IBinder mBinder = new PlayerServiceForAuto.LocalBinder();

    // Android Auto sends play right after asking for the root, see takeCarAutoPlay()
    private static final long CAR_AUTO_PLAY_MILLIS = 2000;
    // whether the next root request comes from a car that has just connected
    private boolean awaitingCar = true;
    private long carAutoPlayDeadline = 0;

    // Lets playback stay on this service after leaving the car, see hasActivePlayer().
    // Only accessed on the main thread.
    @Nullable
    private static PlayerServiceForAuto runningInstance;

    /**
     * @return whether this service holds a player with a queue, even paused. After leaving the
     * car such a player keeps serving the phone until it is closed, so that the phone never
     * ends up with two players.
     */
    public static boolean hasActivePlayer() {
        final PlayerServiceForAuto service = runningInstance;
        return service != null && service.player != null
                && service.player.getPlayQueue() != null;
    }

    /** Pauses the player, if any, e.g. when the phone leaves the car. */
    public static void pausePlayer() {
        final PlayerServiceForAuto service = runningInstance;
        if (service != null && service.player != null) {
            service.player.pause();
        }
    }

    /** Called when the phone leaves the car: the next connection is a new car start. */
    public static void onCarDisconnected() {
        final PlayerServiceForAuto service = runningInstance;
        if (service != null) {
            service.awaitingCar = true;
        }
    }

    /**
     * @return whether a play request is the one Android Auto sends by itself as soon as it
     * connects, to resume the media app it used last. Only the first request counts, so a play
     * pressed by the user always plays.
     */
    public boolean takeCarAutoPlay() {
        final boolean automatic = SystemClock.elapsedRealtime() < carAutoPlayDeadline;
        carAutoPlayDeadline = 0;
        return automatic;
    }

    @Override
    public Service getInstance() {
        return this;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Service's LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate() {
        super.onCreate();
        if (DEBUG) {
            Log.d(TAG, "onCreate() called");
        }
        runningInstance = this;
        assureCorrectAppLanguage(this);
        windowManager = ContextCompat.getSystemService(this, WindowManager.class);

        ThemeHelper.setTheme(this);
        mediaBrowserImpl = new MediaBrowserImpl(this, this::notifyChildrenChanged);

        // see https://developer.android.com/training/cars/media#browser_workflow
        // This is the only connector of the session: the player's MediaSessionManager reuses
        // it (see getMediaSessionConnector()), since a second connector would overwrite the
        // playback state published by this one.
        mediaSession = new MediaSessionCompat(this, "MediaSessionPlayerServ");
        setSessionToken(mediaSession.getSessionToken());
        sessionConnector = new MediaSessionConnector(mediaSession);
        sessionConnector.setMetadataDeduplicationEnabled(true);

        mediaBrowserPlaybackPreparer = new MediaBrowserPlaybackPreparer(
                this,
                this::setMediaSessionError,
                () -> setMediaSessionError(null, 0),
                (playWhenReady) -> {
                    if (player != null && player.getPlayQueue() != null) {
                        player.onPrepare();
                    } else if (playWhenReady) {
                        onPlayWithNothingLoaded();
                    }
                }
        );
        sessionConnector.setPlaybackPreparer(mediaBrowserPlaybackPreparer);

        // after the session, which the player attaches to instead of creating its own
        createView();
    }

    /**
     * Play with nothing loaded, e.g. from the car or the system media controls after the OS
     * killed the app: resume what was playing last. When the car asks by itself on connecting,
     * it plays only if it was playing, otherwise the car shows it paused. With nothing to resume
     * (e.g. the video ended) the car stays on its lists: no error.
     */
    private void onPlayWithNothingLoaded() {
        final boolean automatic = takeCarAutoPlay();
        if (automatic && PlayerService.hasActivePlayer()) {
            // The phone's player is being handed over, see CarConnectionMonitor
            return;
        }
        setMediaSessionError(null, 0);
        LastPlayback.resume(this, !automatic || LastPlayback.wasPlaying(this), () -> { });
    }

    private void setMediaSessionError(@Nullable final String message, final int errorCode) {
        sessionConnector.setCustomErrorMessage(message, errorCode);
        if (message != null && (player == null || player.getMediaSessionManager() == null)) {
            // Without a player the connector only publishes STATE_NONE: show the error here
            mediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_ERROR, 0, 0)
                    .setErrorMessage(errorCode, message)
                    .setActions(PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID)
                    .build());
        }
    }

    private void createView() {
        final PlayerBinding binding = PlayerBinding.inflate(LayoutInflater.from(this));

        player = new Player(this);
        player.setupFromView(binding);
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        if (DEBUG) {
            Log.d(TAG, "onStartCommand() called with: intent = [" + intent
                    + "], flags = [" + flags + "], startId = [" + startId + "]");
        }
        if (intent == null) {
            // The system restarted the service with a null intent, which happens when the process
            // was killed while a start was still pending. The pending start might have been
            // issued with startForegroundService(), so a foreground notification must be posted
            // to avoid ForegroundServiceDidNotStartInTimeException; then the service is stopped,
            // since there is nothing to play.
            Log.w(TAG, "onStartCommand() got a null intent, closing the service");
            NotificationUtil.getInstance().startForegroundWithDummyNotification(this);
            stopService();
            return START_NOT_STICKY;
        }
        if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())
                && (player == null || player.getPlayQueue() == null)) {
            // Nothing loaded. MediaButtonReceiver starts this service with
            // startForegroundService(), so it must still post a notification, otherwise Android
            // reports the app as not responding.
            NotificationUtil.getInstance().startForegroundWithDummyNotification(this);
            if (LastPlayback.isPlayButton(intent)) {
                // e.g. the OS killed the app since: play what was playing last
                LastPlayback.resume(this, true, this::stopService);
            } else {
                stopService();
            }
            return START_NOT_STICKY;
        }
        // null check
        if (player == null) {
            createView();
        }

        if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())
                || intent.getStringExtra(PlayerIntentConstants.PLAY_QUEUE_KEY) != null) {
            NotificationUtil.getInstance().createNotificationAndStartForeground(player, this);
        }

        player.handleIntent(intent);
        if (player.getMediaSessionManager() != null) {
            player.getMediaSessionManager().handleMediaButtonIntent(intent);
        }
        return START_NOT_STICKY;
    }

    public void stopForImmediateReusing() {
        if (DEBUG) {
            Log.d(TAG, "stopForImmediateReusing() called");
        }

        if (!player.exoPlayerIsNull()) {
            player.saveWasPlaying();

            // Releases wifi & cpu, disables keepScreenOn, etc.
            // We can't just pause the player here because it will make transition
            // from one stream to a new stream not smooth
            player.smoothStopPlayer();
            player.setRecovery();

            // Android TV will handle back button in case controls will be visible
            // (one more additional unneeded click while the player is hidden)
            player.hideControls(0, 0);
            player.closeItemsList();

            // Notification shows information about old stream but if a user selects
            // a stream from backStack it's not actual anymore
            // So we should hide the notification at all.
            // When autoplay enabled such notification flashing is annoying so skip this case
        }
    }

    @Override
    public void onTaskRemoved(final Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        if (!player.videoPlayerSelected()) {
            return;
        }
        onDestroy();
        // Unload from memory completely
        Runtime.getRuntime().halt(0);
    }

    @Override
    public void onDestroy() {
        if (DEBUG) {
            Log.d(TAG, "destroy() called");
        }
        super.onDestroy();
        if (runningInstance == this) {
            runningInstance = null;
        }

        cleanup();

        mediaBrowserPlaybackPreparer.dispose();
        mediaSession.release();
        mediaBrowserImpl.dispose();
    }

    private void cleanup() {
        if (player != null) {
            // Exit from fullscreen when user closes the player via notification
            if (player.isFullscreen()) {
                player.changeFullscreen(false);
            }
            removeViewFromParent();

            player.saveStreamProgressState();
            player.setRecovery();
            player.stopActivityBinding();
            player.removePopupFromView();
            player.destroy();

            player = null;
            mediaSession.setActive(false);

            // Should already be handled by NotificationUtil.cancelNotificationAndStopForeground() in
            // NotificationPlayerUi, but let's make sure that the foreground service is stopped.
//            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        }
    }

    public void stopService() {
        NotificationUtil.getInstance().cancelNotificationAndStopForeground(this);
        cleanup();
        stopSelf();
    }

    @Override
    protected void attachBaseContext(final Context base) {
        super.attachBaseContext(AudioServiceLeakFix.preventLeakOf(base));
    }

    @Override
    public IBinder onBind(final Intent intent) {
        if (BIND_PLAYER_HOLDER_ACTION.equals(intent.getAction())) {
            // Note that this binder might be reused multiple times while the service is alive, even
            // after unbind() has been called: https://stackoverflow.com/a/8794930 .
            return mBinder;

        } else if (MediaBrowserServiceCompat.SERVICE_INTERFACE.equals(intent.getAction())) {
            // MediaBrowserService also uses its own binder, so for actions related to the media
            // browser service, pass the onBind to the superclass.
            return super.onBind(intent);

        } else {
            // This is an unknown request, avoid returning any binder to not leak objects.
            return null;
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    public boolean isLandscape() {
        // DisplayMetrics from activity context knows about MultiWindow feature
        // while DisplayMetrics from app context doesn't
        return DeviceUtils.isLandscape(player != null && player.getParentActivity() != null
                ? player.getParentActivity() : this);
    }

    @Nullable
    public View getView() {
        if (player == null) {
            return null;
        }

        return player.getRootView();
    }

    public void removeViewFromParent() {
        if (getView() != null && getView().getParent() != null) {
            if (player.getParentActivity() != null) {
                // This means view was added to fragment
                final ViewGroup parent = (ViewGroup) getView().getParent();
                parent.removeView(getView());
            } else {
                // This means view was added by windowManager for popup player
                windowManager.removeViewImmediate(getView());
            }
        }
    }

    /**
     * @return the current active player instance. May be null, since the player service can outlive
     * the player e.g. to respond to Android Auto media browser queries.
     */
    @Nullable
    public Player getPlayer() {
        return player;
    }

    /**
     * @return the media session for Android Auto compatibility
     */
    @NonNull
    public MediaSessionCompat getMediaSession() {
        return mediaSession;
    }

    @NonNull
    @Override
    public MediaSessionConnector getMediaSessionConnector() {
        return sessionConnector;
    }

    //endregion

    //region Media browser
    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull final String clientPackageName,
                                 final int clientUid,
                                 @Nullable final Bundle rootHints) {
        // TODO check if the accessing package has permission to view data
        final boolean recent = rootHints != null
                && rootHints.getBoolean(BrowserRoot.EXTRA_RECENT);
        if (!recent && awaitingCar) {
            // a car has just connected: its own play request follows, see takeCarAutoPlay()
            awaitingCar = false;
            carAutoPlayDeadline = SystemClock.elapsedRealtime() + CAR_AUTO_PLAY_MILLIS;
        }
        return mediaBrowserImpl.onGetRoot(clientPackageName, clientUid, rootHints);
    }

    @Override
    public void onLoadChildren(@NonNull final String parentId,
                               @NonNull final Result<List<MediaBrowserCompat.MediaItem>> result) {
        mediaBrowserImpl.onLoadChildren(parentId, result);
    }

    @Override
    public void onSearch(@NonNull final String query,
                         final Bundle extras,
                         @NonNull final Result<List<MediaBrowserCompat.MediaItem>> result) {
        mediaBrowserImpl.onSearch(query, result);
    }
    //endregion


    public class LocalBinder extends Binder implements PlayerBinderInterface {

        public PlayerServiceForAuto getService() {
            return PlayerServiceForAuto.this;
        }

        public Player getPlayer() {
            return PlayerServiceForAuto.this.player;
        }
    }
}
