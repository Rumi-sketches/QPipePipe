package org.schabi.newpipe.qpipepipe.selection;

import android.content.Context;
import android.util.Log;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.external_communication.ShareUtils;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.core.Observable;

/**
 * QPipePipe: the fork's actions on upstream's multiple selection (play next, popup, mark as
 * watched, share), each on all the selected videos in the order of the list.
 */
public final class ForkSelectionActions {
    private static final String TAG = "ForkSelectionActions";

    private ForkSelectionActions() {
    }

    /** Adds the fork's actions to a selection toolbar of videos (it has upstream's enqueue). */
    public static void addMenuItems(@NonNull final Toolbar toolbar) {
        if (toolbar.getMenu().findItem(R.id.action_selection_enqueue) == null) {
            return;
        }
        toolbar.inflateMenu(R.menu.menu_selection_qpipepipe);
        // like the single-video entry: only with watch history on
        final Context context = toolbar.getContext();
        toolbar.getMenu().findItem(R.id.qpipepipe_selection_mark_watched).setVisible(
                PreferenceManager.getDefaultSharedPreferences(context)
                        .getBoolean(context.getString(R.string.enable_watch_history_key), true));
    }

    /** @return true if {@code item} is one of the fork's actions and has been handled */
    public static boolean handle(@NonNull final Fragment fragment,
                                 @NonNull final MenuItem item,
                                 @NonNull final List<StreamEntity> streams) {
        final int id = item.getItemId();
        if (id != R.id.qpipepipe_selection_enqueue_next
                && id != R.id.qpipepipe_selection_popup
                && id != R.id.qpipepipe_selection_mark_watched
                && id != R.id.qpipepipe_selection_share) {
            return false;
        }
        final Context context = fragment.getContext();
        if (streams.isEmpty() || context == null) {
            return true;
        }

        final List<StreamInfoItem> items = new ArrayList<>(streams.size());
        for (final StreamEntity stream : streams) {
            items.add(stream.toStreamInfoItem());
        }

        if (id == R.id.qpipepipe_selection_enqueue_next) {
            NavigationHelper.enqueueNextOnPlayer(context, new SinglePlayQueue(items, 0));
        } else if (id == R.id.qpipepipe_selection_popup) {
            NavigationHelper.playOnPopupPlayer(context, new SinglePlayQueue(items, 0), true);
        } else if (id == R.id.qpipepipe_selection_mark_watched) {
            markAsWatched(context, items);
        } else {
            ShareUtils.shareText(context, "", joinLinks(items));
        }
        return true;
    }

    /**
     * Not tied to the screen: the selection closes right away and the marks must be saved even
     * if the user leaves the list meanwhile.
     */
    private static void markAsWatched(@NonNull final Context context,
                                      @NonNull final List<StreamInfoItem> items) {
        final HistoryRecordManager history =
                new HistoryRecordManager(context.getApplicationContext());
        // one at a time: each may need to fetch the video's length first
        Observable.fromIterable(withoutLiveStreams(items))
                .concatMapMaybe(item -> history.markAsWatched(item).onErrorComplete())
                .subscribe(id -> { }, error -> Log.e(TAG, "Mark as watched failed", error));
    }

    /** Live streams can't be watched to the end, as in the single-video entry. */
    @NonNull
    static List<StreamInfoItem> withoutLiveStreams(@NonNull final List<StreamInfoItem> items) {
        final List<StreamInfoItem> result = new ArrayList<>(items.size());
        for (final StreamInfoItem item : items) {
            if (item.getStreamType() != StreamType.LIVE_STREAM
                    && item.getStreamType() != StreamType.AUDIO_LIVE_STREAM) {
                result.add(item);
            }
        }
        return result;
    }

    /** One link per line. */
    @NonNull
    static String joinLinks(@NonNull final List<StreamInfoItem> items) {
        final StringBuilder links = new StringBuilder();
        for (final StreamInfoItem item : items) {
            if (links.length() > 0) {
                links.append('\n');
            }
            links.append(item.getUrl());
        }
        return links.toString();
    }
}
