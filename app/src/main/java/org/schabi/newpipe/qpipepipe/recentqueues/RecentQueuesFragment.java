package org.schabi.newpipe.qpipepipe.recentqueues;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.history.dao.StreamHistoryDAO;
import org.schabi.newpipe.qpipepipe.StreamProgress;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/** The "Recent queues" side of the Playlists page. */
public class RecentQueuesFragment extends Fragment implements RecentQueuesAdapter.Listener {
    private static final String TAG = "RecentQueuesFragment";

    private final CompositeDisposable disposables = new CompositeDisposable();
    @Nullable
    private RecentQueuesAdapter adapter;
    @Nullable
    private TextView countView;
    @Nullable
    private View emptyView;
    @Nullable
    private View clearAllButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.qpipepipe_recent_queues, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        countView = view.findViewById(R.id.recent_queues_count);
        emptyView = view.findViewById(R.id.recent_queues_empty);
        clearAllButton = view.findViewById(R.id.recent_queues_clear_all);
        clearAllButton.setOnClickListener(v -> confirmClearAll());

        adapter = new RecentQueuesAdapter(this);
        final RecyclerView list = view.findViewById(R.id.recent_queues_list);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        final RecentQueueDAO dao = RecentQueuesDatabase.getInstance(requireContext())
                .recentQueueDAO();
        final StreamHistoryDAO historyDao = NewPipeDatabase.getInstance(requireContext())
                .streamHistoryDAO();
        disposables.add(dao.getAll()
                .map(queues -> new Pair<>(queues, watchedCounts(dao, historyDao, queues)))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(loaded -> showQueues(loaded.first, loaded.second),
                        error -> Log.e(TAG, "Loading the recent queues failed", error)));
    }

    @Override
    public void onDestroyView() {
        disposables.clear();
        adapter = null;
        countView = null;
        emptyView = null;
        clearAllButton = null;
        super.onDestroyView();
    }

    /** @return how many videos of each queue were watched, by queue id */
    @NonNull
    private static Map<Long, Integer> watchedCounts(@NonNull final RecentQueueDAO dao,
                                                    @NonNull final StreamHistoryDAO historyDao,
                                                    @NonNull final List<RecentQueueEntity> queues) {
        final Map<Long, List<String>> urlsByQueue = new HashMap<>();
        final Set<String> allUrls = new HashSet<>();
        for (final RecentQueueEntity queue : queues) {
            final List<String> urls = dao.getUrls(queue.uid);
            urlsByQueue.put(queue.uid, urls);
            allUrls.addAll(urls);
        }
        final Map<String, StreamProgress> progress =
                RecentQueueProgress.load(historyDao, new ArrayList<>(allUrls));
        final Map<Long, Integer> watched = new HashMap<>();
        for (final Map.Entry<Long, List<String>> queue : urlsByQueue.entrySet()) {
            watched.put(queue.getKey(),
                    RecentQueueProgress.countWatched(queue.getValue(), progress));
        }
        return watched;
    }

    private void showQueues(@NonNull final List<RecentQueueEntity> queues,
                            @NonNull final Map<Long, Integer> watched) {
        if (adapter == null || countView == null || emptyView == null || clearAllButton == null) {
            return;
        }
        adapter.setQueues(queues, watched);
        countView.setText(getResources().getQuantityString(R.plurals.recent_queues_count,
                queues.size(), queues.size()));
        countView.setVisibility(queues.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        clearAllButton.setVisibility(queues.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        emptyView.setVisibility(queues.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onQueueClicked(@NonNull final RecentQueueEntity queue) {
        RecentQueueFragment.open(requireActivity().getSupportFragmentManager(), queue.uid);
    }

    @Override
    public void onQueueMenuClicked(@NonNull final RecentQueueEntity queue,
                                   @NonNull final View anchor) {
        final PopupMenu menu = new PopupMenu(requireContext(), anchor);
        final Menu items = menu.getMenu();
        items.add(Menu.NONE, R.string.recent_queue_resume, 0, R.string.recent_queue_resume);
        if (queue.isLinkedToPlaylist()) {
            items.add(Menu.NONE, R.string.recent_queue_open_playlist, 1,
                    R.string.recent_queue_open_playlist);
        } else {
            items.add(Menu.NONE, R.string.recent_queue_save_as_playlist, 1,
                    R.string.recent_queue_save_as_playlist);
        }
        items.add(Menu.NONE, R.string.delete, 2, R.string.delete);
        menu.setOnMenuItemClickListener(item -> {
            onMenuAction(queue, item.getItemId());
            return true;
        });
        menu.show();
    }

    private void onMenuAction(@NonNull final RecentQueueEntity queue, final int action) {
        final AppCompatActivity activity = (AppCompatActivity) requireActivity();
        if (action == R.string.recent_queue_open_playlist) {
            RecentQueueActions.openPlaylist(activity, queue);
        } else if (action == R.string.delete) {
            disposables.add(RecentQueueActions.delete(activity, queue));
        } else {
            // resume and save need the videos
            disposables.add(RecentQueuesDatabase.getInstance(activity).recentQueueDAO()
                    .getStreams(queue.uid)
                    .firstElement()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(streams -> {
                        if (action == R.string.recent_queue_resume) {
                            disposables.add(RecentQueueActions.play(activity, queue, streams,
                                    queue.currentIndex, RecentQueueActions.Player.MAIN));
                        } else {
                            RecentQueueActions.saveAsPlaylist(activity, queue, streams);
                        }
                    }, error -> Log.e(TAG, "Loading the queue failed", error)));
        }
    }

    private void confirmClearAll() {
        final RecentQueueDAO dao = RecentQueuesDatabase.getInstance(requireContext())
                .recentQueueDAO();
        new AlertDialog.Builder(requireContext())
                .setMessage(R.string.recent_queues_clear_all_message)
                .setPositiveButton(R.string.recent_queues_clear_all, (dialog, which) -> {
                    RecentQueueRecorder.onAllDeleted();
                    disposables.add(Completable.fromAction(dao::deleteAll)
                                .subscribeOn(Schedulers.io())
                                .subscribe(() -> { }, error ->
                                        Log.e(TAG, "Clearing the recent queues failed", error)));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
