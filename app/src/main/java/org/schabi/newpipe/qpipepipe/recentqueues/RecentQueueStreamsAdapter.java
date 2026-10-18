package org.schabi.newpipe.qpipepipe.recentqueues;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.qpipepipe.PlaylistResume;
import org.schabi.newpipe.qpipepipe.StreamProgress;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.PicassoHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** The header and the videos of a recent queue. */
final class RecentQueueStreamsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_STREAM = 1;

    interface Listener {
        void onStreamClicked(int index);
    }

    @NonNull
    private final View header;
    @NonNull
    private final Listener listener;
    @NonNull
    private List<RecentQueueStreamEntity> streams = new ArrayList<>();
    @NonNull
    private Map<String, StreamProgress> progress = Collections.emptyMap();
    private int currentIndex = -1;

    RecentQueueStreamsAdapter(@NonNull final View header, @NonNull final Listener listener) {
        this.header = header;
        this.listener = listener;
    }

    void setStreams(@NonNull final List<RecentQueueStreamEntity> newStreams,
                    final int newCurrentIndex) {
        streams = newStreams;
        currentIndex = newCurrentIndex;
        notifyDataSetChanged();
    }

    void setProgress(@NonNull final Map<String, StreamProgress> newProgress) {
        progress = newProgress;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(final int position) {
        return position == 0 ? TYPE_HEADER : TYPE_STREAM;
    }

    @Override
    public int getItemCount() {
        return streams.size() + 1;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull final ViewGroup parent,
                                                      final int viewType) {
        if (viewType == TYPE_HEADER) {
            if (header.getParent() instanceof ViewGroup) {
                ((ViewGroup) header.getParent()).removeView(header);
            }
            return new RecyclerView.ViewHolder(header) { };
        }
        return new StreamHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.qpipepipe_recent_queue_stream_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder holder,
                                 final int position) {
        if (holder instanceof StreamHolder) {
            final int index = position - 1;
            ((StreamHolder) holder).bind(streams.get(index), index);
        }
    }

    private final class StreamHolder extends RecyclerView.ViewHolder {
        private final ImageView thumbnail;
        private final TextView duration;
        private final ProgressBar progressBar;
        private final TextView title;
        private final TextView uploader;
        private final TextView state;
        private final TextView added;

        StreamHolder(@NonNull final View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.recent_stream_thumbnail);
            duration = itemView.findViewById(R.id.recent_stream_duration);
            progressBar = itemView.findViewById(R.id.recent_stream_progress);
            title = itemView.findViewById(R.id.recent_stream_title);
            uploader = itemView.findViewById(R.id.recent_stream_uploader);
            state = itemView.findViewById(R.id.recent_stream_state);
            added = itemView.findViewById(R.id.recent_stream_added);
        }

        void bind(@NonNull final RecentQueueStreamEntity stream, final int index) {
            final Context context = itemView.getContext();
            PicassoHelper.loadThumbnail(stream.thumbnailUrl).into(thumbnail);
            title.setText(stream.title);
            uploader.setText(stream.uploader);
            added.setVisibility(stream.addedToPlaylist ? View.VISIBLE : View.GONE);

            if (stream.duration > 0) {
                duration.setText(Localization.getDurationString(stream.duration));
                duration.setVisibility(View.VISIBLE);
            } else {
                duration.setVisibility(View.GONE);
            }

            final long progressMillis = progressMillisOf(stream);
            final long durationMillis = stream.duration * 1000;
            if (progressMillis > 0 && durationMillis > 0) {
                progressBar.setMax((int) stream.duration);
                progressBar.setProgress((int) (progressMillis / 1000));
                progressBar.setVisibility(View.VISIBLE);
            } else {
                progressBar.setVisibility(View.INVISIBLE);
            }

            final String position = progressMillis > 0 && durationMillis > 0
                    ? context.getString(R.string.recent_queue_progress,
                            Localization.getDurationString(progressMillis / 1000),
                            Localization.getDurationString(stream.duration))
                    : null;
            state.setText(stateText(context, index, position, progressMillis, durationMillis));
            itemView.setOnClickListener(v -> listener.onStreamClicked(index));
        }

        private long progressMillisOf(@NonNull final RecentQueueStreamEntity stream) {
            final StreamProgress saved = progress.get(stream.url);
            return saved == null ? 0 : saved.getProgressMillis();
        }
    }

    @NonNull
    private String stateText(@NonNull final Context context, final int index,
                             @Nullable final String position, final long progressMillis,
                             final long durationMillis) {
        if (index == currentIndex) {
            return position != null
                    ? context.getString(R.string.recent_queue_playing_at, position)
                    : context.getString(R.string.recent_queue_playing_now);
        }
        if (durationMillis > 0
                && progressMillis >= durationMillis * PlaylistResume.FINISHED_RATIO) {
            return context.getString(R.string.recent_queue_watched);
        }
        return position != null ? position : context.getString(R.string.recent_queue_not_watched);
    }
}
