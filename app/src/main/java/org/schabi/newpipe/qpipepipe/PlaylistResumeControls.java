package org.schabi.newpipe.qpipepipe;

import static org.schabi.newpipe.qpipepipe.PlaylistResume.NO_RESUME;

import android.content.Context;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.PlaylistResumeControlBinding;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

/**
 * Drives the "continue where you left off" switch of the playlist headers
 * ({@code playlist_resume_control.xml}): shows where playback would resume and tells the play
 * buttons which index to start from. With the switch on, the video to resume from is also
 * marked in the list ({@link PlaylistResumeDecoration}).
 */
public final class PlaylistResumeControls {
    private static final String TAG = "PlaylistResumeControls";
    /** Lets a drag or a burst of loaded items settle before reading the history again. */
    private static final long LIST_CHANGE_DELAY_MILLIS = 300;

    private final PlaylistResumeControlBinding binding;
    private final Supplier<List<StreamInfoItem>> streams;
    private final RecyclerView list;
    private final PlaylistResumeDecoration decoration;
    private final CompositeDisposable disposables = new CompositeDisposable();
    private final Runnable refreshTask = this::refresh;
    private final RecyclerView.AdapterDataObserver listObserver =
            new RecyclerView.AdapterDataObserver() {
                @Override
                public void onChanged() {
                    refreshSoon();
                }

                @Override
                public void onItemRangeInserted(final int positionStart, final int itemCount) {
                    refreshSoon();
                }

                @Override
                public void onItemRangeRemoved(final int positionStart, final int itemCount) {
                    refreshSoon();
                }

                @Override
                public void onItemRangeMoved(final int fromPosition, final int toPosition,
                                             final int itemCount) {
                    refreshSoon();
                }
            };
    @Nullable
    private RecyclerView.Adapter<?> observedAdapter;

    private int resumeIndex = NO_RESUME;
    @Nullable
    private String resumeTitle;

    /**
     * @param streams the streams in the order the play buttons queue them
     * @param list     the playlist's list, showing the header (with these controls) first and
     *                 then the streams in the same order
     */
    public PlaylistResumeControls(@NonNull final PlaylistResumeControlBinding binding,
                                  @NonNull final Supplier<List<StreamInfoItem>> streams,
                                  @NonNull final RecyclerView list) {
        this.binding = binding;
        this.streams = streams;
        this.list = list;
        decoration = new PlaylistResumeDecoration(list.getContext());
        list.addItemDecoration(decoration);

        binding.playlistResumeSwitch.setChecked(
                QPipePipePreferences.isPlaylistResumeEnabled(context()));
        binding.playlistResumeSwitch.setOnCheckedChangeListener((button, checked) -> {
            QPipePipePreferences.setPlaylistResumeEnabled(context(), checked);
            lookUpResumeIndex(index -> { });
        });
        binding.playlistResumeRow.setOnClickListener(v -> binding.playlistResumeSwitch.toggle());
        render();
    }

    /**
     * Reads the resume point again. Call it when the streams are first shown and when the
     * screen comes back, since the switch is shared by all playlists and the history may have
     * moved on. Later changes to the list (loading, sorting, dragging) are followed on their own.
     */
    public void refresh() {
        observeList();
        final boolean enabled = QPipePipePreferences.isPlaylistResumeEnabled(context());
        if (binding.playlistResumeSwitch.isChecked() != enabled) {
            binding.playlistResumeSwitch.setChecked(enabled); // the listener looks it up
        } else {
            lookUpResumeIndex(index -> { });
        }
    }

    /**
     * Calls {@code play} with the index to start from: the resume point when the switch is on
     * and there is one, otherwise 0. The resume point is read again first, because a player
     * may have advanced while this screen stayed open.
     */
    public void startFromResumePoint(@NonNull final IntConsumer play) {
        if (!binding.playlistResumeSwitch.isChecked()) {
            play.accept(0);
            return;
        }
        lookUpResumeIndex(index -> play.accept(index == NO_RESUME ? 0 : index));
    }

    public void dispose() {
        disposables.clear();
        list.removeCallbacks(refreshTask);
        list.removeItemDecoration(decoration);
        if (observedAdapter != null) {
            observedAdapter.unregisterAdapterDataObserver(listObserver);
            observedAdapter = null;
        }
    }

    /** The adapter may not be set yet when these controls are created with the header. */
    private void observeList() {
        final RecyclerView.Adapter<?> adapter = list.getAdapter();
        if (adapter != null && adapter != observedAdapter) {
            if (observedAdapter != null) {
                observedAdapter.unregisterAdapterDataObserver(listObserver);
            }
            adapter.registerAdapterDataObserver(listObserver);
            observedAdapter = adapter;
        }
    }

    private void refreshSoon() {
        list.removeCallbacks(refreshTask);
        list.postDelayed(refreshTask, LIST_CHANGE_DELAY_MILLIS);
    }

    private void lookUpResumeIndex(@NonNull final IntConsumer then) {
        final List<StreamInfoItem> items = streams.get();
        disposables.add(PlaylistResume.findResumeIndex(context(), items)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(index -> {
                    resumeIndex = index;
                    resumeTitle = index == NO_RESUME ? null : items.get(index).getName();
                    render();
                    then.accept(index);
                }, error -> {
                    Log.w(TAG, "Could not read the playlist resume point", error);
                    then.accept(NO_RESUME);
                }));
    }

    private void render() {
        final Context context = context();
        // the switch only shows up when resuming differs from playing all from the first video
        final boolean hasResumePoint = resumeIndex > 0;
        binding.playlistResumeRow.setVisibility(hasResumePoint ? View.VISIBLE : View.GONE);
        if (hasResumePoint) {
            binding.playlistResumeTarget.setText(context.getString(
                    R.string.playlist_resume_target, resumeIndex + 1, resumeTitle));
        }
        final boolean resuming = binding.playlistResumeSwitch.isChecked() && hasResumePoint;
        binding.playlistCtrlPlayAllText.setText(resuming
                ? context.getString(R.string.playlist_resume_play, resumeIndex + 1)
                : context.getString(R.string.play_all));
        // + 1 for the header, the first row of the list
        decoration.setTarget(list, resuming ? resumeIndex + 1 : RecyclerView.NO_POSITION);
    }

    private Context context() {
        return binding.getRoot().getContext();
    }
}
