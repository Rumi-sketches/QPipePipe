package org.schabi.newpipe.qpipepipe.recentqueues;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.PicassoHelper;

import java.util.ArrayList;
import java.util.List;

/** Rows of the recent queues list. */
final class RecentQueuesAdapter extends RecyclerView.Adapter<RecentQueuesAdapter.Holder> {

    interface Listener {
        void onQueueClicked(@NonNull RecentQueueEntity queue);

        void onQueueMenuClicked(@NonNull RecentQueueEntity queue, @NonNull View anchor);
    }

    @NonNull
    private final Listener listener;
    @NonNull
    private List<RecentQueueEntity> queues = new ArrayList<>();

    RecentQueuesAdapter(@NonNull final Listener listener) {
        this.listener = listener;
    }

    void setQueues(@NonNull final List<RecentQueueEntity> newQueues) {
        queues = newQueues;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull final ViewGroup parent, final int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.qpipepipe_recent_queue_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull final Holder holder, final int position) {
        holder.bind(queues.get(position));
    }

    @Override
    public int getItemCount() {
        return queues.size();
    }

    final class Holder extends RecyclerView.ViewHolder {
        private final ImageView thumbnail;
        private final TextView streamCount;
        private final TextView title;
        private final TextView date;
        private final TextView added;
        private final TextView meta;
        private final TextView position;
        private final ProgressBar progress;
        private final ImageButton menu;

        Holder(@NonNull final View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.recent_queue_thumbnail);
            streamCount = itemView.findViewById(R.id.recent_queue_stream_count);
            title = itemView.findViewById(R.id.recent_queue_title);
            date = itemView.findViewById(R.id.recent_queue_date);
            added = itemView.findViewById(R.id.recent_queue_added);
            meta = itemView.findViewById(R.id.recent_queue_meta);
            position = itemView.findViewById(R.id.recent_queue_position);
            progress = itemView.findViewById(R.id.recent_queue_progress);
            menu = itemView.findViewById(R.id.recent_queue_menu);
        }

        void bind(@NonNull final RecentQueueEntity queue) {
            final Context context = itemView.getContext();
            PicassoHelper.loadPlaylistThumbnail(queue.thumbnailUrl).into(thumbnail);
            streamCount.setText(String.valueOf(queue.streamCount));
            title.setText(RecentQueueActions.title(context, queue));

            // a linked queue is named after its playlist, so its date goes on its own line
            date.setVisibility(queue.isLinkedToPlaylist() ? View.VISIBLE : View.GONE);
            date.setText(RecentQueueActions.dateTime(context, queue.updatedAt));
            if (queue.addedCount > 0) {
                added.setText(context.getResources().getQuantityString(
                        R.plurals.recent_queue_added_to_playlist, queue.addedCount,
                        queue.addedCount));
                added.setVisibility(View.VISIBLE);
            } else {
                added.setVisibility(View.GONE);
            }

            meta.setText(RecentQueueActions.countAndDuration(context, queue.streamCount,
                    queue.totalDuration));
            position.setText(RecentQueueActions.position(context, queue.currentIndex,
                    queue.streamCount));
            progress.setProgress(RecentQueueActions.progressPercent(queue.currentIndex,
                    queue.streamCount));

            itemView.setOnClickListener(v -> listener.onQueueClicked(queue));
            menu.setOnClickListener(v -> listener.onQueueMenuClicked(queue, v));
        }
    }
}
