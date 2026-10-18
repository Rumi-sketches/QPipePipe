package org.schabi.newpipe.qpipepipe.recentqueues;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.qpipepipe.PlaylistProgressLines;
import org.schabi.newpipe.qpipepipe.QPipePipePreferences;
import org.schabi.newpipe.util.ThemeHelper;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * The "Playlists / Recent queues" switch of the Playlists page, and the playlist progress shown
 * under each local playlist. Both appear only while recent queues are on.
 */
public final class RecentQueuesTabs {
    private static final String TAG = "RecentQueuesTabs";
    private static final String QUEUES_FRAGMENT_TAG = "recent_queues";

    /** The side last shown, kept while the app runs. */
    private static boolean showingQueues = false;

    @NonNull
    private final Fragment host;
    @NonNull
    private final MaterialButtonToggleGroup tabs;
    @NonNull
    private final View queuesContainer;
    @NonNull
    private final View playlistsList;
    @NonNull
    private final Runnable onPlaylistProgressChanged;
    @Nullable
    private Disposable progressSubscription;

    /**
     * @param onPlaylistProgressChanged redraws the playlist rows, which read
     *                                  {@link PlaylistProgressLines}
     */
    public RecentQueuesTabs(@NonNull final Fragment host, @NonNull final View root,
                            @NonNull final Runnable onPlaylistProgressChanged) {
        this.host = host;
        this.tabs = root.findViewById(R.id.recent_queues_tabs);
        this.queuesContainer = root.findViewById(R.id.recent_queues_container);
        this.playlistsList = root.findViewById(R.id.items_list);
        this.onPlaylistProgressChanged = onPlaylistProgressChanged;
        tintWithAppAccent(root.getContext());
        tabs.check(showingQueues ? R.id.recent_queues_tab_queues
                : R.id.recent_queues_tab_playlists);
        tabs.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                showingQueues = checkedId == R.id.recent_queues_tab_queues;
                showSelectedSide();
            }
        });
    }

    /**
     * The buttons run under a Material theme (see the layout), whose colors are not the app's:
     * mark the selected side with the accent of the app theme.
     */
    private void tintWithAppAccent(@NonNull final Context context) {
        final int accent = ThemeHelper.resolveColorFromAttr(context, R.attr.colorAccent);
        final ColorStateList background = new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{Color.argb(64, Color.red(accent), Color.green(accent),
                        Color.blue(accent)), Color.TRANSPARENT});
        for (int i = 0; i < tabs.getChildCount(); i++) {
            final MaterialButton button = (MaterialButton) tabs.getChildAt(i);
            button.setBackgroundTintList(background);
            button.setStrokeColor(ColorStateList.valueOf(accent));
        }
    }

    /** Applies the setting, which may have changed while the page was hidden. */
    public void refresh() {
        final Context context = host.requireContext();
        final boolean enabled = QPipePipePreferences.isRecentQueuesEnabled(context);
        tabs.setVisibility(enabled ? View.VISIBLE : View.GONE);
        showSelectedSide();
        if (enabled && progressSubscription == null) {
            progressSubscription = NewPipeDatabase.getInstance(context).playlistStreamDAO()
                    .getPlaylistProgress()
                    // history changes at every video start; redraw only on real changes
                    .distinctUntilChanged()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(progress -> {
                        PlaylistProgressLines.update(progress);
                        onPlaylistProgressChanged.run();
                    }, error -> Log.e(TAG, "Loading the playlist progress failed", error));
        } else if (!enabled) {
            dispose();
            onPlaylistProgressChanged.run();
        }
    }

    public void dispose() {
        if (progressSubscription != null) {
            progressSubscription.dispose();
            progressSubscription = null;
        }
        // other lists with playlist rows (e.g. Add to playlist) would show stale lines
        PlaylistProgressLines.clear();
    }

    private void showSelectedSide() {
        final boolean showQueues = tabs.getVisibility() == View.VISIBLE && showingQueues;
        queuesContainer.setVisibility(showQueues ? View.VISIBLE : View.GONE);
        // the playlists stay laid out below the queues: keep screen readers off them
        playlistsList.setImportantForAccessibility(showQueues
                ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        if (showQueues) {
            final FragmentManager fragments = host.getChildFragmentManager();
            if (fragments.findFragmentByTag(QUEUES_FRAGMENT_TAG) == null) {
                fragments.beginTransaction()
                        .replace(R.id.recent_queues_container, new RecentQueuesFragment(),
                                QUEUES_FRAGMENT_TAG)
                        .commit();
            }
        }
    }
}
