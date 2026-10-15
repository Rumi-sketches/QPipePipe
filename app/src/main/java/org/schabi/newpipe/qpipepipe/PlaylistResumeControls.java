package org.schabi.newpipe.qpipepipe;

import static org.schabi.newpipe.qpipepipe.PlaylistResume.NO_RESUME;

import android.content.Context;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
 * buttons which index to start from.
 */
public final class PlaylistResumeControls {
    private static final String TAG = "PlaylistResumeControls";

    private final PlaylistResumeControlBinding binding;
    private final Supplier<List<StreamInfoItem>> streams;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private int resumeIndex = NO_RESUME;
    @Nullable
    private String resumeTitle;

    /**
     * @param streams the streams in the order the play buttons queue them
     */
    public PlaylistResumeControls(@NonNull final PlaylistResumeControlBinding binding,
                                  @NonNull final Supplier<List<StreamInfoItem>> streams) {
        this.binding = binding;
        this.streams = streams;

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
     * Reads the resume point again. Call it when the streams change or when the screen comes
     * back, since the switch is shared by all playlists and the history may have moved on.
     */
    public void refresh() {
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
        binding.playlistCtrlPlayAllText.setText(
                binding.playlistResumeSwitch.isChecked() && hasResumePoint
                        ? context.getString(R.string.playlist_resume_play, resumeIndex + 1)
                        : context.getString(R.string.play_all));
    }

    private Context context() {
        return binding.getRoot().getContext();
    }
}
