package org.schabi.newpipe.qpipepipe.selection;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.local.dialog.PlaylistDialog;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.StreamProcessor;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import us.shandian.giga.get.DirectDownloader;

/** The actions of the selection bar, each on all the selected videos in order. */
final class SelectionActions {
    private static final String TAG = "SelectionActions";

    private SelectionActions() {
    }

    /**
     * One queue with all the videos. Enqueued on a queue started from a local playlist, they are
     * added to that playlist like any enqueue (recent queues).
     */
    static void enqueue(@NonNull final Fragment fragment,
                        @NonNull final List<StreamInfoItem> items, final boolean next) {
        final SinglePlayQueue queue = new SinglePlayQueue(items, 0);
        if (next) {
            NavigationHelper.enqueueNextOnPlayer(fragment.requireContext(), queue);
        } else {
            NavigationHelper.enqueueOnPlayer(fragment.requireContext(), queue);
        }
    }

    static void playInBackground(@NonNull final Fragment fragment,
                                 @NonNull final List<StreamInfoItem> items) {
        NavigationHelper.playOnBackgroundPlayer(fragment.requireContext(),
                new SinglePlayQueue(items, 0), true);
    }

    static void playInPopup(@NonNull final Fragment fragment,
                            @NonNull final List<StreamInfoItem> items) {
        NavigationHelper.playOnPopupPlayer(fragment.requireContext(),
                new SinglePlayQueue(items, 0), true);
    }

    /** The playlist dialog already takes several videos. */
    static void addToPlaylist(@NonNull final Fragment fragment,
                              @NonNull final List<StreamInfoItem> items) {
        final List<StreamEntity> streams = new ArrayList<>(items.size());
        for (final StreamInfoItem item : items) {
            streams.add(new StreamEntity(item));
        }
        PlaylistDialog.createCorrespondingDialog(fragment.requireContext(), streams,
                dialog -> dialog.show(fragment.getParentFragmentManager(),
                        "MultiSelect@playlist"));
    }

    /**
     * Video or audio only, then the sequential download of "Download all": quality and folder
     * come from the download settings, files already there are skipped.
     */
    static void download(@NonNull final Fragment fragment,
                         @NonNull final List<StreamInfoItem> items,
                         @NonNull final Runnable onChosen) {
        final Context context = fragment.requireContext();
        final int count = items.size();
        new AlertDialog.Builder(context)
                .setTitle(context.getResources().getQuantityString(
                        R.plurals.qpipepipe_selection_download_title, count, count))
                .setMessage(R.string.qpipepipe_selection_download_message)
                .setPositiveButton(R.string.video, (dialog, which) -> {
                    startDownload(context, items, DirectDownloader.DownloadType.VIDEO);
                    onChosen.run();
                })
                .setNeutralButton(R.string.audio, (dialog, which) -> {
                    startDownload(context, items, DirectDownloader.DownloadType.AUDIO);
                    onChosen.run();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static void startDownload(@NonNull final Context context,
                                      @NonNull final List<StreamInfoItem> items,
                                      @NonNull final DirectDownloader.DownloadType type) {
        // the processor fetches every video before downloading it, so it starts off the UI; it
        // runs long, so it gets the app context, not the screen
        final Context appContext = context.getApplicationContext();
        Schedulers.io().scheduleDirect(() -> new StreamProcessor()
                .processStreamsSequentiallyWithProgress(appContext, items, type));
    }

    /** Like the single-video entry: only with watch history on, and not for live streams. */
    static boolean canMarkAsWatched(@NonNull final Context context,
                                    @NonNull final List<StreamInfoItem> items) {
        if (!PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.enable_watch_history_key), true)) {
            return false;
        }
        for (final StreamInfoItem item : items) {
            if (!isLive(item)) {
                return true;
            }
        }
        return false;
    }

    static void markAsWatched(@NonNull final Fragment fragment,
                              @NonNull final List<StreamInfoItem> items) {
        final HistoryRecordManager history = new HistoryRecordManager(fragment.requireContext());
        // one at a time: each may need to fetch the video's length first
        Observable.fromIterable(items)
                .filter(item -> !isLive(item))
                .concatMapMaybe(item -> history.markAsWatched(item).onErrorComplete())
                .subscribe(id -> { }, error -> Log.e(TAG, "Mark as watched failed", error));
    }

    private static boolean isLive(@NonNull final StreamInfoItem item) {
        return item.getStreamType() == StreamType.LIVE_STREAM
                || item.getStreamType() == StreamType.AUDIO_LIVE_STREAM;
    }

    /** One link per line. */
    static void shareLinks(@NonNull final Fragment fragment,
                           @NonNull final List<StreamInfoItem> items) {
        final StringBuilder links = new StringBuilder();
        for (final StreamInfoItem item : items) {
            if (links.length() > 0) {
                links.append('\n');
            }
            links.append(item.getUrl());
        }
        final Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, links.toString());
        fragment.startActivity(Intent.createChooser(send,
                fragment.getString(R.string.qpipepipe_selection_share_links)));
    }
}
