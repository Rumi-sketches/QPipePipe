package org.schabi.newpipe.qpipepipe.recentqueues;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.snackbar.Snackbar;

import org.schabi.newpipe.R;

import java.lang.ref.WeakReference;

/**
 * Offers, for a few seconds, to take back a batch of videos just appended to a playlist, e.g. a
 * whole playlist enqueued by mistake. The append happens in the player, so the offer is shown on
 * whatever screen of the app is in front.
 */
public final class RecentQueueUndo {
    private static final int DURATION_MILLIS = 4000;

    @NonNull
    private static WeakReference<Activity> resumed = new WeakReference<>(null);

    private RecentQueueUndo() {
    }

    /** Called once by the application, to know which screen is in front. */
    public static void register(@NonNull final Application application) {
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(@NonNull final Activity activity) {
                resumed = new WeakReference<>(activity);
            }

            @Override
            public void onActivityPaused(@NonNull final Activity activity) {
                if (resumed.get() == activity) {
                    resumed = new WeakReference<>(null);
                }
            }

            @Override
            public void onActivityCreated(@NonNull final Activity activity,
                                          @Nullable final Bundle savedInstanceState) {
            }

            @Override
            public void onActivityStarted(@NonNull final Activity activity) {
            }

            @Override
            public void onActivityStopped(@NonNull final Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull final Activity activity,
                                                    @NonNull final Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(@NonNull final Activity activity) {
            }
        });
    }

    /**
     * Shows {@code message} with an Undo action that runs {@code undo}. Nothing is shown when the
     * app is in background: the videos stay in the playlist.
     */
    @MainThread
    static void offer(@NonNull final String message, @NonNull final Runnable undo) {
        final Activity activity = resumed.get();
        if (activity == null || activity.isFinishing()) {
            return;
        }
        final View root = activity.findViewById(android.R.id.content);
        if (root == null) {
            return;
        }
        Snackbar.make(root, message, DURATION_MILLIS)
                .setAction(R.string.undo, v -> undo.run())
                .show();
    }
}
