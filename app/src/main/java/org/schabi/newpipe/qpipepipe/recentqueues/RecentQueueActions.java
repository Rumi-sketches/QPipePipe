package org.schabi.newpipe.qpipepipe.recentqueues;

import android.content.Context;
import android.text.InputType;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.databinding.DialogEditTextBinding;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;
import org.schabi.newpipe.qpipepipe.PlaylistProgressLines;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.NavigationHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/** What the user can do with a recent queue, shared by the list and the detail screen. */
final class RecentQueueActions {
    private static final String TAG = "RecentQueueActions";

    enum Player { MAIN, BACKGROUND, POPUP }

    private RecentQueueActions() {
    }

    @NonNull
    static String title(@NonNull final Context context, @NonNull final RecentQueueEntity queue) {
        if (queue.isLinkedToPlaylist() && queue.sourcePlaylistName != null) {
            return context.getString(R.string.recent_queue_from_playlist,
                    queue.sourcePlaylistName);
        }
        return context.getString(R.string.recent_queue_title,
                dateTime(context, queue.createdAt));
    }

    @NonNull
    static String dateTime(@NonNull final Context context, final long millis) {
        return PlaylistProgressLines.dateTime(context, millis);
    }

    /** "2/12 videos · 2:12", the duration in hours and minutes. */
    @NonNull
    static String positionAndDuration(@NonNull final Context context, final int index,
                                      final int count, final long durationSeconds) {
        final String position = context.getResources().getQuantityString(
                R.plurals.recent_queue_position_videos, count, index + 1, count);
        return durationSeconds > 0
                ? Localization.concatenateStrings(position, hoursAndMinutes(durationSeconds))
                : position;
    }

    /** "77:23" for 77 hours and 23 minutes: long queues read better without days. */
    @NonNull
    static String hoursAndMinutes(final long seconds) {
        return String.format(Locale.ROOT, "%d:%02d", seconds / 3600, seconds % 3600 / 60);
    }

    /** Percentage of the queue reached, for the progress bars. */
    static int progressPercent(final int index, final int count) {
        return count <= 0 ? 0 : (index + 1) * 100 / count;
    }

    /**
     * Plays {@code streams} from {@code index}, continuing the same recent queue entry instead
     * of creating a new one.
     */
    @NonNull
    static Disposable play(@NonNull final AppCompatActivity activity,
                           @NonNull final RecentQueueEntity queue,
                           @NonNull final List<RecentQueueStreamEntity> streams,
                           final int index, @NonNull final Player player) {
        if (!queue.isLinkedToPlaylist()) {
            start(activity, queue, streams, index, player, false);
            return Disposable.disposed();
        }
        // after a backup import the id may belong to another playlist: link only if the name
        // still matches, so enqueued videos never land in the wrong playlist
        final Context appContext = activity.getApplicationContext();
        return Single.fromCallable(() -> String.valueOf(
                        RecentQueueRecorder.playlistName(appContext, queue.sourcePlaylistId)))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(name -> start(activity, queue, streams, index, player,
                                name.equals(queue.sourcePlaylistName)),
                        error -> Log.e(TAG, "Checking the playlist failed", error));
    }

    private static void start(@NonNull final AppCompatActivity activity,
                              @NonNull final RecentQueueEntity queue,
                              @NonNull final List<RecentQueueStreamEntity> streams,
                              final int index, @NonNull final Player player,
                              final boolean linked) {
        if (streams.isEmpty()) {
            return;
        }
        final List<StreamInfoItem> items = new ArrayList<>(streams.size());
        for (final RecentQueueStreamEntity stream : streams) {
            items.add(stream.toStreamInfoItem());
        }
        final PlayQueue playQueue = new SinglePlayQueue(items,
                Math.max(0, Math.min(index, items.size() - 1)));
        if (linked) {
            playQueue.setSourcePlaylistId(queue.sourcePlaylistId);
        } else if (!queue.isLinkedToPlaylist()) {
            playQueue.setSessionId(sessionOf(queue));
        }
        switch (player) {
            case BACKGROUND:
                NavigationHelper.playOnBackgroundPlayer(activity, playQueue, false);
                break;
            case POPUP:
                NavigationHelper.playOnPopupPlayer(activity, playQueue, false);
                break;
            default:
                NavigationHelper.playOnMainPlayer(activity, playQueue);
                break;
        }
    }

    @NonNull
    static String sessionOf(@NonNull final RecentQueueEntity queue) {
        final String prefix = RecentQueueEntity.keyForSession("");
        return queue.queueKey.startsWith(prefix)
                ? queue.queueKey.substring(prefix.length())
                : queue.queueKey;
    }

    /** Asks for a name, prefilled with the queue title, and saves the videos as a playlist. */
    static void saveAsPlaylist(@NonNull final Context context,
                               @NonNull final RecentQueueEntity queue,
                               @NonNull final List<RecentQueueStreamEntity> streams) {
        final DialogEditTextBinding dialogBinding = DialogEditTextBinding.inflate(
                android.view.LayoutInflater.from(context));
        dialogBinding.dialogEditText.setHint(R.string.name);
        dialogBinding.dialogEditText.setInputType(InputType.TYPE_CLASS_TEXT);
        dialogBinding.dialogEditText.setText(title(context, queue));
        dialogBinding.dialogEditText.selectAll();

        new AlertDialog.Builder(context)
                .setTitle(R.string.recent_queue_save_as_playlist)
                .setView(dialogBinding.getRoot())
                .setPositiveButton(R.string.create, (dialog, which) -> {
                    final String name = dialogBinding.dialogEditText.getText().toString().trim();
                    createPlaylist(context, queue,
                            name.isEmpty() ? title(context, queue) : name, streams);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @SuppressWarnings("CheckResult")
    private static void createPlaylist(@NonNull final Context context,
                                       @NonNull final RecentQueueEntity queue,
                                       @NonNull final String name,
                                       @NonNull final List<RecentQueueStreamEntity> streams) {
        final List<StreamEntity> entities = new ArrayList<>(streams.size());
        for (final RecentQueueStreamEntity stream : streams) {
            entities.add(new StreamEntity(stream.toStreamInfoItem()));
        }
        final Context appContext = context.getApplicationContext();
        RecentQueueRecorder.saveAsPlaylist(context, queue, name, entities, () ->
                Toast.makeText(appContext, R.string.recent_queue_saved, Toast.LENGTH_SHORT)
                        .show());
    }

    @NonNull
    static Disposable delete(@NonNull final Context context,
                             @NonNull final RecentQueueEntity queue) {
        RecentQueueRecorder.onDeleted(queue.queueKey);
        return Single.fromCallable(() -> RecentQueuesDatabase.getInstance(context)
                        .recentQueueDAO().delete(queue.uid))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(deleted -> Toast.makeText(context, queue.isLinkedToPlaylist()
                                ? R.string.recent_queue_deleted_linked
                                : R.string.recent_queue_deleted, Toast.LENGTH_SHORT).show(),
                        error -> Log.e(TAG, "Deleting the queue failed", error));
    }

    static void openPlaylist(@NonNull final AppCompatActivity activity,
                             @NonNull final RecentQueueEntity queue) {
        NavigationHelper.openLocalPlaylistFragment(activity.getSupportFragmentManager(),
                queue.sourcePlaylistId, queue.sourcePlaylistName);
    }
}
