package org.schabi.newpipe.qpipepipe;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.events.RecoveryEvent;
import org.schabi.newpipe.util.NavigationHelper;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.ref.WeakReference;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * The queue played last, on the phone or in the car, kept on disk so that playback can resume
 * after the OS killed the app: from Android Auto, the system media controls, or the play button
 * of headphones and car. Every queue the player starts replaces it, and a queue played to its end
 * is forgotten, so a video finished at home does not come back in the car.
 * <p>
 * The player calls it on the main thread; the file is written on one background thread.
 */
public final class LastPlayback {
    private static final String TAG = "LastPlayback";
    private static final String FILE_NAME = "last_playback.ser";
    private static final long SAVE_DELAY_MILLIS = 1000;
    private static final Scheduler WRITES = Schedulers.from(Executors.newSingleThreadExecutor());

    @Nullable
    private static Disposable queueEvents;
    @Nullable
    private static Disposable resuming;
    /** The queue whose last video ended, until the user changes it. */
    @NonNull
    private static WeakReference<PlayQueue> finishedQueue = new WeakReference<>(null);

    private LastPlayback() {
    }

    /** Called when the player starts a queue: from now on that queue is the one to resume. */
    @MainThread
    public static void onQueueStarted(@NonNull final Context context,
                                      @NonNull final PlayQueue queue) {
        final File file = fileOf(context);
        if (queueEvents != null) {
            queueEvents.dispose();
        }
        finishedQueue = new WeakReference<>(null);
        // Every queue event (index moved, items added or removed, position recorded) saves it
        // again. The subscription ends by itself when the player disposes the queue.
        queueEvents = queue.getBroadcastReceiver()
                .doOnNext(event -> {
                    // A finished queue counts again only once the user changes it: closing the
                    // player still records its final position, which must not bring it back
                    if (!(event instanceof RecoveryEvent)) {
                        finishedQueue = new WeakReference<>(null);
                    }
                })
                .debounce(SAVE_DELAY_MILLIS, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .filter(event -> finishedQueue.get() != queue)
                .subscribe(event -> save(file, queue),
                        error -> Log.e(TAG, "Queue events failed", error));
    }

    /** Called when the last video of the queue ended: there is nothing left to resume. */
    @MainThread
    public static void onQueueFinished(@NonNull final Context context,
                                       @NonNull final PlayQueue queue) {
        finishedQueue = new WeakReference<>(queue);
        final File file = fileOf(context);
        Completable.fromAction(() -> clear(file))
                .subscribeOn(WRITES)
                .subscribe(() -> { }, error -> Log.e(TAG, "Clearing failed", error));
    }

    /** @return whether there is a queue to resume, without reading it */
    public static boolean exists(@NonNull final Context context) {
        return fileOf(context).exists();
    }

    /** @return the queue to resume, or nothing if there is none or it can't be read */
    @NonNull
    public static Maybe<PlayQueue> load(@NonNull final Context context) {
        final File file = fileOf(context);
        return Maybe.fromCallable(() -> readOrDrop(file)).subscribeOn(WRITES);
    }

    /**
     * Plays the queue to resume in background, from where it was left. A request made while
     * another one is loading is ignored, so a repeated play button starts it only once.
     *
     * @param onNothing called on the main thread when there is nothing to resume
     */
    @MainThread
    public static void resume(@NonNull final Context context, @NonNull final Runnable onNothing) {
        if (resuming != null && !resuming.isDisposed()) {
            return;
        }
        final Context appContext = context.getApplicationContext();
        resuming = load(appContext)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        queue -> NavigationHelper.playOnBackgroundPlayer(appContext, queue, true),
                        error -> {
                            Log.e(TAG, "Resuming failed", error);
                            onNothing.run();
                        },
                        onNothing::run);
    }

    /** @return whether a media button intent asks to start playing */
    public static boolean isPlayButton(@NonNull final Intent intent) {
        final KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
        if (event == null || event.getAction() != KeyEvent.ACTION_DOWN) {
            return false;
        }
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_HEADSETHOOK:
                return true;
            default:
                return false;
        }
    }

    @NonNull
    private static File fileOf(@NonNull final Context context) {
        return new File(context.getApplicationContext().getFilesDir(), FILE_NAME);
    }

    @MainThread
    private static void save(@NonNull final File file, @NonNull final PlayQueue queue) {
        final byte[] bytes;
        try {
            // serialized here, since the player changes the queue on the main thread
            bytes = serialize(queue);
        } catch (final IOException e) {
            Log.e(TAG, "Serializing the queue failed", e);
            return;
        }
        Completable.fromAction(() -> write(file, bytes))
                .subscribeOn(WRITES)
                .subscribe(() -> { }, error -> Log.e(TAG, "Saving failed", error));
    }

    @VisibleForTesting
    @NonNull
    static byte[] serialize(@NonNull final PlayQueue queue) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(queue);
        }
        return bytes.toByteArray();
    }

    /** Writes through a temporary file, so a crash never leaves half a queue behind. */
    @VisibleForTesting
    static void write(@NonNull final File file, @NonNull final byte[] bytes) throws IOException {
        final File temporary = new File(file.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temporary)) {
            out.write(bytes);
        }
        // On Android the rename replaces the old file; where it can't (Windows, in unit tests)
        // the old file is deleted first
        if (!temporary.renameTo(file) && !(file.delete() && temporary.renameTo(file))) {
            throw new IOException("Could not replace " + file);
        }
    }

    @Nullable
    private static PlayQueue readOrDrop(@NonNull final File file) {
        try {
            return read(file);
        } catch (final IOException | ClassNotFoundException | ClassCastException e) {
            // e.g. saved by a version whose classes changed: nothing sensible to resume
            Log.w(TAG, "Dropping an unreadable saved queue", e);
            if (!file.delete()) {
                Log.w(TAG, "Could not delete " + file);
            }
            return null;
        }
    }

    /** @return the saved queue, or null if there is none or it is empty */
    @VisibleForTesting
    @Nullable
    static PlayQueue read(@NonNull final File file) throws IOException, ClassNotFoundException {
        if (!file.exists()) {
            return null;
        }
        try (ObjectInputStream in = new ObjectInputStream(
                new BufferedInputStream(new FileInputStream(file)))) {
            final PlayQueue queue = (PlayQueue) in.readObject();
            return queue.isEmpty() ? null : queue;
        }
    }

    @VisibleForTesting
    static void clear(@NonNull final File file) throws IOException {
        if (file.exists() && !file.delete()) {
            throw new IOException("Could not delete " + file);
        }
    }
}
