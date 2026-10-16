package org.schabi.newpipe.qpipepipe.recentqueues;

import android.content.Context;
import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.database.AppDatabase;
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry;
import org.schabi.newpipe.database.playlist.model.PlaylistEntity;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.local.playlist.LocalPlaylistManager;
import org.schabi.newpipe.player.mediaitem.PlayerMediaItem;
import org.schabi.newpipe.player.playqueue.ChannelPlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.PlaylistPlayQueue;
import org.schabi.newpipe.qpipepipe.QPipePipePreferences;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Saves the queue of the player to the recent queues while it plays, and appends the videos the
 * user enqueues to the local playlist the queue was started from. Queues started from a remote
 * playlist or a channel are not recorded: the user saves those by hand.
 * <p>
 * The player calls it on the main thread; every database write goes through one background
 * thread, so appends and saves never overlap.
 */
public final class RecentQueueRecorder {
    private static final String TAG = "RecentQueueRecorder";
    /** Wait for the queue to settle before writing it, e.g. after several quick skips. */
    private static final long SAVE_DELAY_MILLIS = 2000;

    private static final Scheduler WRITES = Schedulers.from(Executors.newSingleThreadExecutor());

    /** Sessions the user built: started with several videos, or enqueued to by hand. */
    private static final Set<String> BUILT_SESSIONS = new HashSet<>();
    /** Key of the queue playing now. */
    @Nullable
    private static String activeKey;
    @NonNull
    private static WeakReference<PlayQueue> activeQueue = new WeakReference<>(null);
    /** The queue playing now is being saved as a playlist: hold its saves until linked. */
    private static boolean activeLinking;
    /** The user deleted the entry of the queue playing now: don't bring it back. */
    private static boolean activeDeleted;

    private RecentQueueRecorder() {
    }

    /**
     * Called when the player starts a queue. The subscription ends by itself when the player
     * disposes the queue, after writing the last pending change.
     */
    @MainThread
    public static void onQueueStarted(@NonNull final Context context,
                                      @NonNull final PlayQueue queue) {
        if (!isRecorded(context, queue)) {
            return;
        }
        activeKey = keyOf(queue.getSourcePlaylistId(), queue.getSessionId());
        activeQueue = new WeakReference<>(queue);
        activeDeleted = false;
        activeLinking = false;
        if (queue.size() >= 2) {
            BUILT_SESSIONS.add(queue.getSessionId());
        }
        final Context appContext = context.getApplicationContext();
        queue.getBroadcastReceiver()
                .debounce(SAVE_DELAY_MILLIS, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> save(appContext, queue, Collections.emptyList()),
                        error -> Log.e(TAG, "Queue events failed", error));
    }

    /**
     * Called when the user enqueues {@code enqueued} on the playing {@code queue}. On a queue
     * started from a local playlist, the enqueued videos are appended to that playlist, whether
     * picked one by one or as a whole playlist or channel. Only the videos that joined the queue
     * are appended: for a remote playlist, the ones loaded so far.
     */
    @MainThread
    public static void onItemsEnqueued(@NonNull final Context context,
                                       @NonNull final PlayQueue queue,
                                       @NonNull final PlayQueue enqueued) {
        if (!isRecorded(context, queue)) {
            return;
        }
        BUILT_SESSIONS.add(queue.getSessionId());
        final long playlistId = queue.getSourcePlaylistId();
        if (playlistId < 0) {
            return;
        }
        final Context appContext = context.getApplicationContext();
        final AppDatabase db = NewPipeDatabase.getInstance(appContext);
        final List<PlayerMediaItem> items = new ArrayList<>(enqueued.getStreams());
        Single.fromCallable(() -> appendMissing(db, playlistId, items))
                .subscribeOn(WRITES)
                // save() copies the queue, which the player changes on the main thread
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(added -> {
                    if (!added.isEmpty()) {
                        save(appContext, queue, added);
                    }
                }, error -> Log.e(TAG, "Append to playlist failed", error));
    }

    /** Called when the user deletes an entry, so the playing queue does not recreate it. */
    @MainThread
    static void onDeleted(@NonNull final String queueKey) {
        if (queueKey.equals(activeKey)) {
            activeDeleted = true;
        }
    }

    /**
     * Saves {@code streams} as a new local playlist and links {@code entry} to it, together with
     * the playing queue if it is that entry's. Later enqueues then go to the playlist and saving
     * is no longer offered, so the playlist is never duplicated.
     */
    @MainThread
    static void saveAsPlaylist(@NonNull final Context context,
                               @NonNull final RecentQueueEntity entry,
                               @NonNull final String name,
                               @NonNull final List<StreamEntity> streams,
                               @NonNull final Runnable onSaved) {
        if (streams.isEmpty()) {
            return;
        }
        final Context appContext = context.getApplicationContext();
        final boolean isActive = entry.queueKey.equals(activeKey);
        if (isActive) {
            activeLinking = true;
        }
        Single.fromCallable(() -> {
            final AppDatabase db = NewPipeDatabase.getInstance(appContext);
            // index -1 puts it on top of the playlists, as LocalPlaylistManager does
            final long playlistId = db.playlistDAO().insert(
                    new PlaylistEntity(name, streams.get(0).getThumbnailUrl(), -1));
            new LocalPlaylistManager(db).appendToPlaylist(playlistId, streams).blockingGet();
            RecentQueuesDatabase.getInstance(appContext).recentQueueDAO().linkToPlaylist(
                    entry.uid, RecentQueueEntity.keyForPlaylist(playlistId), playlistId, name);
            return playlistId;
        }).subscribeOn(WRITES)
                .observeOn(AndroidSchedulers.mainThread())
                .doFinally(() -> {
                    if (isActive) {
                        activeLinking = false;
                    }
                })
                .subscribe(playlistId -> {
                    final PlayQueue queue = activeQueue.get();
                    if (isActive && queue != null && entry.queueKey.equals(activeKey)) {
                        queue.setSourcePlaylistId(playlistId);
                        activeKey = RecentQueueEntity.keyForPlaylist(playlistId);
                        activeLinking = false;
                        save(appContext, queue, Collections.emptyList());
                    }
                    onSaved.run();
                }, error -> Log.e(TAG, "Saving the queue as playlist failed", error));
    }

    /** Called when the user clears all entries. */
    @MainThread
    static void onAllDeleted() {
        activeDeleted = true;
    }

    private static boolean isRecorded(@NonNull final Context context,
                                      @NonNull final PlayQueue queue) {
        return QPipePipePreferences.isRecentQueuesEnabled(context)
                && !(queue instanceof PlaylistPlayQueue)
                && !(queue instanceof ChannelPlayQueue);
    }

    /**
     * Appends to the playlist the {@code items} it does not contain yet. Runs on {@link #WRITES}
     * only, so two quick enqueues cannot both see a video as missing.
     *
     * @return the urls appended
     */
    @NonNull
    private static List<String> appendMissing(@NonNull final AppDatabase db, final long playlistId,
                                              @NonNull final List<PlayerMediaItem> items) {
        final List<PlaylistStreamEntry> current = db.playlistStreamDAO()
                .getOrderedStreamsOf(playlistId).blockingFirst();
        final Set<String> urls = new HashSet<>();
        for (final PlaylistStreamEntry entry : current) {
            urls.add(entry.getStreamEntity().getUrl());
        }
        final List<StreamEntity> missing = new ArrayList<>();
        final List<String> added = new ArrayList<>();
        for (final PlayerMediaItem item : items) {
            if (urls.add(item.getUrl())) {
                missing.add(new StreamEntity(item));
                added.add(item.getUrl());
            }
        }
        if (!missing.isEmpty()) {
            new LocalPlaylistManager(db).appendToPlaylist(playlistId, missing).blockingGet();
        }
        return added;
    }

    /** Copies the queue on the main thread and writes it on {@link #WRITES}. */
    @MainThread
    private static void save(@NonNull final Context context, @NonNull final PlayQueue queue,
                             @NonNull final List<String> addedUrls) {
        final String key = keyOf(queue.getSourcePlaylistId(), queue.getSessionId());
        if ((activeDeleted || activeLinking) && key.equals(activeKey)) {
            return;
        }
        final List<PlayerMediaItem> items = new ArrayList<>(queue.getStreams());
        final long playlistId = queue.getSourcePlaylistId();
        if (items.isEmpty()) {
            return;
        }
        // an entry from an earlier run of the app keeps being updated even if not built here
        final boolean mayCreate = isWorthKeeping(playlistId,
                BUILT_SESSIONS.contains(queue.getSessionId()));

        final RecentQueueEntity entity = new RecentQueueEntity();
        entity.queueKey = key;
        entity.sourcePlaylistId = playlistId >= 0 ? playlistId : RecentQueueEntity.NO_PLAYLIST;
        entity.updatedAt = System.currentTimeMillis();
        entity.currentIndex = Math.max(0, Math.min(queue.getIndex(), items.size() - 1));
        entity.streamCount = items.size();
        entity.thumbnailUrl = items.get(0).getThumbnailUrl();
        for (final PlayerMediaItem item : items) {
            entity.totalDuration += Math.max(0, item.getDuration());
        }
        final RecentQueueSnapshot snapshot = new RecentQueueSnapshot(entity, items, addedUrls);

        Completable.fromAction(() -> {
            if (playlistId >= 0) {
                entity.sourcePlaylistName = playlistName(context, playlistId);
            }
            RecentQueuesDatabase.getInstance(context).recentQueueDAO().save(snapshot, mayCreate);
        }).subscribeOn(WRITES)
                .subscribe(() -> { }, error -> Log.e(TAG, "Saving the queue failed", error));
    }

    /**
     * A queue started from a local playlist is always kept. Any other queue only once the user
     * built it, so videos opened one by one, with related videos queued automatically, are not.
     */
    static boolean isWorthKeeping(final long sourcePlaylistId, final boolean builtByUser) {
        return sourcePlaylistId >= 0 || builtByUser;
    }

    /** One entry per local playlist, one per queue session otherwise. */
    @NonNull
    static String keyOf(final long sourcePlaylistId, @NonNull final String sessionId) {
        return sourcePlaylistId >= 0
                ? RecentQueueEntity.keyForPlaylist(sourcePlaylistId)
                : RecentQueueEntity.keyForSession(sessionId);
    }

    @Nullable
    static String playlistName(@NonNull final Context context, final long playlistId) {
        final List<PlaylistEntity> playlists = NewPipeDatabase.getInstance(context)
                .playlistDAO().getPlaylist(playlistId).blockingFirst();
        return playlists.isEmpty() ? null : playlists.get(0).getName();
    }
}
