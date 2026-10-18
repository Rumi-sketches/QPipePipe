package org.schabi.newpipe.qpipepipe.recentqueues;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.util.Pair;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.history.dao.StreamHistoryDAO;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/** The videos of one recent queue, with buttons to resume it or save it as a playlist. */
public class RecentQueueFragment extends BaseFragment
        implements RecentQueueStreamsAdapter.Listener {
    private static final String KEY_QUEUE_ID = "queue_id";
    /** Urls per query, under SQLite's limit of 999 bound parameters. */

    private final CompositeDisposable disposables = new CompositeDisposable();
    private long queueId;
    @Nullable
    private RecentQueueEntity queue;
    @NonNull
    private List<RecentQueueStreamEntity> streams = Collections.emptyList();
    @Nullable
    private RecentQueueStreamsAdapter adapter;
    @Nullable
    private View header;
    /** The watch progress being loaded, replaced when the queue changes. */
    @Nullable
    private Disposable progressLoad;

    static void open(@NonNull final FragmentManager fragmentManager, final long queueId) {
        final RecentQueueFragment fragment = new RecentQueueFragment();
        final Bundle args = new Bundle();
        args.putLong(KEY_QUEUE_ID, queueId);
        fragment.setArguments(args);
        fragmentManager.beginTransaction()
                .setCustomAnimations(R.animator.custom_fade_in, R.animator.custom_fade_out,
                        R.animator.custom_fade_in, R.animator.custom_fade_out)
                .replace(R.id.fragment_holder, fragment)
                .addToBackStack(null)
                .commit();
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        queueId = requireArguments().getLong(KEY_QUEUE_ID);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        final RecyclerView list = new RecyclerView(inflater.getContext());
        list.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        list.setBackgroundResource(android.R.color.transparent);
        list.setLayoutManager(new LinearLayoutManager(inflater.getContext()));
        header = inflater.inflate(R.layout.qpipepipe_recent_queue_header, list, false);
        adapter = new RecentQueueStreamsAdapter(header, this);
        list.setAdapter(adapter);
        return list;
    }

    @Override
    public void onViewCreated(@NonNull final View rootView, final Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);
        final Context context = requireContext();
        final RecentQueueDAO dao = RecentQueuesDatabase.getInstance(context).recentQueueDAO();
        disposables.add(Flowable.combineLatest(dao.getQueue(queueId), dao.getStreams(queueId),
                        Pair::new)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    if (result.first.isEmpty()) {
                        // deleted, e.g. with Clear all
                        getParentFragmentManager().popBackStack();
                    } else {
                        show(result.first.get(0), result.second);
                    }
                }, error -> Log.e(TAG, "Loading the queue failed", error)));
    }

    @Override
    public void onDestroyView() {
        disposables.clear();
        if (progressLoad != null) {
            progressLoad.dispose();
            progressLoad = null;
        }
        adapter = null;
        header = null;
        super.onDestroyView();
    }

    private void show(@NonNull final RecentQueueEntity newQueue,
                      @NonNull final List<RecentQueueStreamEntity> newStreams) {
        if (adapter == null || header == null) {
            return;
        }
        queue = newQueue;
        streams = newStreams;
        final Context context = requireContext();
        final String title = RecentQueueActions.title(context, newQueue);
        setTitle(title);

        ((TextView) header.findViewById(R.id.recent_queue_header_title)).setText(title);
        ((TextView) header.findViewById(R.id.recent_queue_header_meta)).setText(
                RecentQueueActions.positionAndDuration(context, newQueue.currentIndex,
                        newQueue.streamCount, newQueue.totalDuration));
        final TextView added = header.findViewById(R.id.recent_queue_header_added);
        if (newQueue.addedCount > 0) {
            added.setText(context.getResources().getQuantityString(
                    R.plurals.recent_queue_added_to_playlist, newQueue.addedCount,
                    newQueue.addedCount,
                    RecentQueueActions.dateTime(context, newQueue.createdAt)));
            added.setVisibility(View.VISIBLE);
        } else {
            added.setVisibility(View.GONE);
        }

        header.findViewById(R.id.recent_queue_play).setOnClickListener(v ->
                play(newQueue.currentIndex, RecentQueueActions.Player.MAIN));
        header.findViewById(R.id.recent_queue_play_background).setOnClickListener(v ->
                play(newQueue.currentIndex, RecentQueueActions.Player.BACKGROUND));
        header.findViewById(R.id.recent_queue_play_popup).setOnClickListener(v ->
                play(newQueue.currentIndex, RecentQueueActions.Player.POPUP));

        final TextView secondary = header.findViewById(R.id.recent_queue_secondary_action);
        if (newQueue.isLinkedToPlaylist()) {
            secondary.setText(R.string.recent_queue_open_playlist);
            secondary.setOnClickListener(v ->
                    RecentQueueActions.openPlaylist((AppCompatActivity) requireActivity(),
                            newQueue));
        } else {
            secondary.setText(R.string.recent_queue_save_as_playlist);
            secondary.setOnClickListener(v ->
                    RecentQueueActions.saveAsPlaylist(requireContext(), newQueue, streams));
        }

        adapter.setStreams(newStreams, newQueue.currentIndex);
        loadProgress(context, newStreams);
    }

    private void loadProgress(@NonNull final Context context,
                              @NonNull final List<RecentQueueStreamEntity> items) {
        final List<String> urls = new ArrayList<>(items.size());
        for (final RecentQueueStreamEntity item : items) {
            urls.add(item.url);
        }
        final StreamHistoryDAO historyDao = NewPipeDatabase.getInstance(context)
                .streamHistoryDAO();
        if (progressLoad != null) {
            progressLoad.dispose();
        }
        progressLoad = Single.fromCallable(() -> RecentQueueProgress.load(historyDao, urls))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(progress -> {
                    if (adapter != null) {
                        adapter.setProgress(progress);
                    }
                }, error -> Log.e(TAG, "Loading the watch progress failed", error));
    }

    private void play(final int index, @NonNull final RecentQueueActions.Player player) {
        if (queue != null) {
            disposables.add(RecentQueueActions.play((AppCompatActivity) requireActivity(),
                    queue, streams, index, player));
        }
    }

    @Override
    public void onStreamClicked(final int index) {
        play(index, RecentQueueActions.Player.MAIN);
    }
}
