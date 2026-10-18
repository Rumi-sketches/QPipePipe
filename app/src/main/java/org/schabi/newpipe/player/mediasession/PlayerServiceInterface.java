package org.schabi.newpipe.player.mediasession;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.v4.media.session.MediaSessionCompat;
import android.view.View;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;

public interface PlayerServiceInterface{


    public void onCreate();
    public int onStartCommand(final Intent intent, final int flags, final int startId);

    public void stopForImmediateReusing();

    public void onTaskRemoved(final Intent rootIntent);

    public void onDestroy();

    public void stopService();

    public IBinder onBind(final Intent intent);

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    boolean isLandscape();

    @Nullable
    public View getView();

    public void removeViewFromParent();

    public MediaSessionCompat getMediaSession();

    /**
     * @return the connector that already drives {@link #getMediaSession()}, which the player
     * must reuse instead of attaching a second one, or null if the player creates its own
     */
    @Nullable
    public MediaSessionConnector getMediaSessionConnector();

    public Service getInstance();

}