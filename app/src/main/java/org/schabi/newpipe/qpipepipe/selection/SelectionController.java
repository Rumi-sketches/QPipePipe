package org.schabi.newpipe.qpipepipe.selection;

import android.app.Activity;
import android.graphics.Rect;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.core.view.GestureDetectorCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Selection mode of one list: intercepts taps to select videos, shows the count and "Select
 * all" in an action mode over the toolbar, and the actions in a bar at the bottom.
 */
final class SelectionController implements RecyclerView.OnItemTouchListener,
        ActionMode.Callback {
    @NonNull
    private final Fragment fragment;
    @NonNull
    private final RecyclerView list;
    @NonNull
    private final MultiSelect.StreamResolver resolver;
    @Nullable
    private final MultiSelect.Remover remover;
    @NonNull
    private final GestureDetectorCompat gestures;
    @NonNull
    private final SelectionDecoration decoration;
    @NonNull
    private final LifecycleObserver leaveOnPause;

    /**
     * Selected videos by key (see {@link MultiSelect.StreamResolver#keyAt}), in the order they
     * were picked. Keys, not positions: history and feed reload while selecting.
     */
    private final Map<String, StreamInfoItem> selected = new LinkedHashMap<>();
    /** Where the last touch started, to select the video whose long-press menu was used. */
    private int pressedPosition = RecyclerView.NO_POSITION;
    /** Leaves selection mode when the list stops being visible, e.g. a tab swiped away. */
    private final ViewTreeObserver.OnScrollChangedListener visibilityCheck =
            this::finishIfHidden;
    private final ViewTreeObserver.OnGlobalLayoutListener layoutVisibilityCheck =
            this::finishIfHidden;
    private final Rect visibleRect = new Rect();
    @Nullable
    private ActionMode mode;
    @Nullable
    private View bar;
    private int listPaddingBottom;
    private boolean listClipToPadding;

    SelectionController(@NonNull final Fragment fragment, @NonNull final RecyclerView list,
                        @NonNull final MultiSelect.StreamResolver resolver,
                        @Nullable final MultiSelect.Remover remover) {
        this.fragment = fragment;
        this.list = list;
        this.resolver = resolver;
        this.remover = remover;
        this.decoration = new SelectionDecoration(list.getContext(), position -> {
            final String key = resolver.keyAt(position);
            return key != null && selected.containsKey(key);
        });
        this.gestures = new GestureDetectorCompat(list.getContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onSingleTapUp(@NonNull final MotionEvent e) {
                        return isSelecting() && toggleAt(e, true);
                    }

                    @Override
                    public void onLongPress(@NonNull final MotionEvent e) {
                        if (isSelecting()) {
                            // the row's own long press follows and must still see selection
                            // mode, so emptying the selection here does not end it
                            toggleAt(e, false);
                        }
                    }
                });
        // a tab swiped away or a screen left behind must not keep acting on this list
        this.leaveOnPause = new DefaultLifecycleObserver() {
            @Override
            public void onPause(@NonNull final LifecycleOwner owner) {
                finish();
            }
        };
        list.addOnItemTouchListener(this);
        list.addItemDecoration(decoration);
        fragment.getLifecycle().addObserver(leaveOnPause);
    }

    void dispose() {
        finish();
        list.removeOnItemTouchListener(this);
        list.removeItemDecoration(decoration);
        fragment.getLifecycle().removeObserver(leaveOnPause);
    }

    boolean isSelecting() {
        return mode != null;
    }

    /**
     * "Multiple selection" in the long-press menu: start with that menu's video selected. The
     * row is the one touched last, unless the list changed meanwhile or no touch opened the
     * menu (keyboard, D-pad): then the video is looked up.
     */
    void startFromLongPress(@NonNull final StreamInfoItem item) {
        int position = pressedPosition;
        if (!sameVideo(resolver.resolve(position), item)) {
            position = RecyclerView.NO_POSITION;
            final RecyclerView.Adapter<?> adapter = list.getAdapter();
            for (int p = 0; adapter != null && p < adapter.getItemCount(); p++) {
                if (sameVideo(resolver.resolve(p), item)) {
                    position = p;
                    break;
                }
            }
        }
        start(position == RecyclerView.NO_POSITION ? null : position);
    }

    private static boolean sameVideo(@Nullable final StreamInfoItem a,
                                     @NonNull final StreamInfoItem b) {
        return a != null && a.getServiceId() == b.getServiceId()
                && Objects.equals(a.getUrl(), b.getUrl());
    }

    /** @param position the row to select first, or null to start with nothing selected */
    void start(@Nullable final Integer position) {
        final Activity activity = fragment.getActivity();
        if (!(activity instanceof AppCompatActivity) || fragment.getView() == null) {
            return;
        }
        if (mode == null) {
            mode = ((AppCompatActivity) activity).startSupportActionMode(this);
        }
        if (position != null) {
            select(position);
        }
        refresh();
    }

    private void select(final int position) {
        final String key = resolver.keyAt(position);
        final StreamInfoItem item = resolver.resolve(position);
        if (key != null && item != null && !selected.containsKey(key)) {
            selected.put(key, item);
        }
    }

    private void finishIfHidden() {
        if (isSelecting() && (!fragment.getUserVisibleHint() || !list.isShown()
                || !list.getGlobalVisibleRect(visibleRect))) {
            finish();
        }
    }

    void finish() {
        if (mode != null) {
            mode.finish();
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Touches
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public boolean onInterceptTouchEvent(@NonNull final RecyclerView rv,
                                         @NonNull final MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            pressedPosition = positionAt(e);
        }
        // only a completed tap is taken from the rows, so scrolling still works
        return isSelecting() && gestures.onTouchEvent(e);
    }

    @Override
    public void onTouchEvent(@NonNull final RecyclerView rv, @NonNull final MotionEvent e) {
    }

    @Override
    public void onRequestDisallowInterceptTouchEvent(final boolean disallowIntercept) {
    }

    private int positionAt(@NonNull final MotionEvent e) {
        final View child = list.findChildViewUnder(e.getX(), e.getY());
        return child == null ? RecyclerView.NO_POSITION : list.getChildAdapterPosition(child);
    }

    /**
     * @param finishWhenEmpty whether unselecting the last video ends selection mode
     * @return whether a video was under the touch; headers keep their own taps
     */
    private boolean toggleAt(@NonNull final MotionEvent e, final boolean finishWhenEmpty) {
        final int position = positionAt(e);
        final String key = position == RecyclerView.NO_POSITION ? null : resolver.keyAt(position);
        if (key == null) {
            return false;
        }
        if (selected.remove(key) == null) {
            select(position);
        } else if (selected.isEmpty() && finishWhenEmpty) {
            finish();
            return true;
        }
        refresh();
        return true;
    }

    private void selectAll() {
        final RecyclerView.Adapter<?> adapter = list.getAdapter();
        if (adapter == null) {
            return;
        }
        for (int position = 0; position < adapter.getItemCount(); position++) {
            select(position);
        }
        refresh();
    }

    private void refresh() {
        if (mode != null) {
            final int count = selected.size();
            mode.setTitle(fragment.getResources().getQuantityString(
                    R.plurals.qpipepipe_selection_count, count, count));
        }
        if (bar != null) {
            setActionsEnabled((ViewGroup) bar, !selected.isEmpty());
        }
        list.invalidate();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Action mode and bar
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public boolean onCreateActionMode(@NonNull final ActionMode actionMode,
                                      @NonNull final Menu menu) {
        menu.add(Menu.NONE, R.id.qpipepipe_menu_select_all, Menu.NONE,
                        R.string.qpipepipe_selection_select_all)
                .setIcon(R.drawable.ic_select_all)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        showBar();
        list.getViewTreeObserver().addOnScrollChangedListener(visibilityCheck);
        list.getViewTreeObserver().addOnGlobalLayoutListener(layoutVisibilityCheck);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(@NonNull final ActionMode actionMode,
                                       @NonNull final Menu menu) {
        return false;
    }

    @Override
    public boolean onActionItemClicked(@NonNull final ActionMode actionMode,
                                       @NonNull final MenuItem item) {
        if (item.getItemId() == R.id.qpipepipe_menu_select_all) {
            selectAll();
            return true;
        }
        return false;
    }

    @Override
    public void onDestroyActionMode(@NonNull final ActionMode actionMode) {
        mode = null;
        list.getViewTreeObserver().removeOnScrollChangedListener(visibilityCheck);
        list.getViewTreeObserver().removeOnGlobalLayoutListener(layoutVisibilityCheck);
        selected.clear();
        hideBar();
        list.invalidate();
    }

    private void showBar() {
        final Activity activity = fragment.requireActivity();
        final ViewGroup content = activity.findViewById(android.R.id.content);
        bar = LayoutInflater.from(activity).inflate(R.layout.qpipepipe_selection_bar, content,
                false);
        content.addView(bar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        // above the navigation bar when the window draws behind it
        final int barPaddingBottom = bar.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(bar, (v, insets) -> {
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(),
                    barPaddingBottom + insets.getInsets(
                            WindowInsetsCompat.Type.navigationBars()).bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(bar);
        bar.findViewById(R.id.qpipepipe_selection_enqueue).setOnClickListener(v ->
                act(() -> SelectionActions.enqueue(fragment, items(), false)));
        bar.findViewById(R.id.qpipepipe_selection_enqueue_next).setOnClickListener(v ->
                act(() -> SelectionActions.enqueue(fragment, items(), true)));
        bar.findViewById(R.id.qpipepipe_selection_background).setOnClickListener(v ->
                act(() -> SelectionActions.playInBackground(fragment, items())));
        bar.findViewById(R.id.qpipepipe_selection_popup).setOnClickListener(v ->
                act(() -> SelectionActions.playInPopup(fragment, items())));
        bar.findViewById(R.id.qpipepipe_selection_playlist).setOnClickListener(v ->
                act(() -> SelectionActions.addToPlaylist(fragment, items())));
        bar.findViewById(R.id.qpipepipe_selection_download).setOnClickListener(v ->
                SelectionActions.download(fragment, items(), this::finish));
        bar.findViewById(R.id.qpipepipe_selection_more).setOnClickListener(this::showMore);

        // keep the last rows reachable above the bar
        listPaddingBottom = list.getPaddingBottom();
        listClipToPadding = list.getClipToPadding();
        bar.post(() -> {
            if (bar != null) {
                list.setClipToPadding(false);
                list.setPadding(list.getPaddingLeft(), list.getPaddingTop(),
                        list.getPaddingRight(), listPaddingBottom + bar.getHeight());
            }
        });
    }

    private void hideBar() {
        if (bar == null) {
            return;
        }
        ((ViewGroup) bar.getParent()).removeView(bar);
        bar = null;
        list.setClipToPadding(listClipToPadding);
        list.setPadding(list.getPaddingLeft(), list.getPaddingTop(), list.getPaddingRight(),
                listPaddingBottom);
    }

    private static void setActionsEnabled(@NonNull final ViewGroup group, final boolean enabled) {
        for (int i = 0; i < group.getChildCount(); i++) {
            final View child = group.getChildAt(i);
            child.setEnabled(enabled);
            child.setAlpha(enabled ? 1f : 0.4f);
        }
    }

    private void showMore(@NonNull final View anchor) {
        final PopupMenu menu = new PopupMenu(anchor.getContext(), anchor);
        if (SelectionActions.canMarkAsWatched(anchor.getContext(), items())) {
            menu.getMenu().add(Menu.NONE, R.string.mark_as_watched, 0, R.string.mark_as_watched);
        }
        menu.getMenu().add(Menu.NONE, R.string.qpipepipe_selection_share_links, 1,
                R.string.qpipepipe_selection_share_links);
        if (remover != null) {
            menu.getMenu().add(Menu.NONE, remover.label(), 2, remover.label());
        }
        menu.setOnMenuItemClickListener(item -> {
            final int id = item.getItemId();
            if (id == R.string.mark_as_watched) {
                act(() -> SelectionActions.markAsWatched(fragment, items()));
            } else if (id == R.string.qpipepipe_selection_share_links) {
                act(() -> SelectionActions.shareLinks(fragment, items()));
            } else if (remover != null && id == remover.label()) {
                removeSelected();
            }
            return true;
        });
        menu.show();
    }

    /** Runs an action on the selected videos, then leaves selection mode. */
    private void act(@NonNull final Runnable action) {
        if (!selected.isEmpty()) {
            action.run();
        }
        finish();
    }

    @NonNull
    private List<StreamInfoItem> items() {
        return new ArrayList<>(selected.values());
    }

    /** Removing acts on the rows that hold the selected videos now. */
    private void removeSelected() {
        final RecyclerView.Adapter<?> adapter = list.getAdapter();
        if (remover == null || adapter == null) {
            return;
        }
        final List<Integer> positions = new ArrayList<>();
        for (int position = 0; position < adapter.getItemCount(); position++) {
            final String key = resolver.keyAt(position);
            if (key != null && selected.containsKey(key)) {
                positions.add(position);
            }
        }
        if (positions.size() < selected.size()) {
            // some selected videos left the list meanwhile
            Toast.makeText(list.getContext(), R.string.qpipepipe_selection_list_changed,
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        finish();
        remover.remove(positions);
    }
}
