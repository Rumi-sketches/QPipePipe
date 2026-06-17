package org.schabi.newpipe.info_list.swipe;

import android.content.Context;
import android.graphics.Canvas;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

/**
 * <p>
 *     Generic left/right swipe gesture handler for stream items, used by the "swipe actions"
 *     feature (see {@link SwipeActionPreferences}). It is intentionally adapter-agnostic: a
 *     {@link ItemResolver} is given to it so it can be reused on top of
 *     {@link org.schabi.newpipe.info_list.InfoListAdapter} (homepage, search), Groupie-backed
 *     lists (channel groups), or any other {@link RecyclerView.Adapter}.
 * </p>
 * <p>
 *     Unlike a typical swipe-to-dismiss callback, this never removes the swiped item: the action
 *     is performed and the row has to snap back into place afterwards. Since the item isn't
 *     removed, {@link #onSwiped} asks the adapter to rebind it ({@code notifyItemChanged}), which
 *     resets the translation {@code ItemTouchHelper} applied during the gesture.
 * </p>
 */
public class StreamSwipeItemTouchCallback extends ItemTouchHelper.SimpleCallback {

    /** Resolves the {@link StreamInfoItem} bound to a swiped {@link RecyclerView.ViewHolder}. */
    public interface ItemResolver {
        @Nullable
        StreamInfoItem resolve(@NonNull RecyclerView.ViewHolder viewHolder);
    }

    @NonNull
    private final Fragment fragment;
    @NonNull
    private final ItemResolver itemResolver;

    public StreamSwipeItemTouchCallback(@NonNull final Fragment fragment,
                                        @NonNull final ItemResolver itemResolver) {
        super(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
        this.fragment = fragment;
        this.itemResolver = itemResolver;
    }

    @Override
    public boolean onMove(@NonNull final RecyclerView recyclerView,
                          @NonNull final RecyclerView.ViewHolder viewHolder,
                          @NonNull final RecyclerView.ViewHolder target) {
        return false;
    }

    @Override
    public boolean isLongPressDragEnabled() {
        return false;
    }

    @Override
    public boolean isItemViewSwipeEnabled() {
        return true;
    }

    @Override
    public int getSwipeDirs(@NonNull final RecyclerView recyclerView,
                            @NonNull final RecyclerView.ViewHolder viewHolder) {
        // Only stream items can be swiped; headers, footers, channels, playlists, etc. opt out.
        if (itemResolver.resolve(viewHolder) == null) {
            return 0;
        }

        // This callback is used in contexts that have no special handling for actions that
        // only make sense elsewhere (e.g. removing a stream only makes sense inside a
        // playlist), so disable swiping in whichever direction is configured to use one.
        final Context context = recyclerView.getContext();
        int dirs = super.getSwipeDirs(recyclerView, viewHolder);
        if (SwipeActionPreferences.getLeftAction(context).contextSpecific) {
            dirs &= ~ItemTouchHelper.LEFT;
        }
        if (SwipeActionPreferences.getRightAction(context).contextSpecific) {
            dirs &= ~ItemTouchHelper.RIGHT;
        }
        return dirs;
    }

    @Override
    public void onChildDraw(@NonNull final Canvas canvas,
                            @NonNull final RecyclerView recyclerView,
                            @NonNull final RecyclerView.ViewHolder viewHolder,
                            final float dX, final float dY,
                            final int actionState, final boolean isCurrentlyActive) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            final SwipeAction action = dX > 0
                    ? SwipeActionPreferences.getRightAction(recyclerView.getContext())
                    : SwipeActionPreferences.getLeftAction(recyclerView.getContext());
            SwipeActionPainter.drawBackground(canvas, viewHolder.itemView, dX, action);
        }
        super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
    }

    @Override
    public void onSwiped(@NonNull final RecyclerView.ViewHolder viewHolder, final int direction) {
        final StreamInfoItem item = itemResolver.resolve(viewHolder);

        // The item isn't removed, so rebind it to snap the row back to its resting position
        // (this clears the translation ItemTouchHelper applied during the gesture).
        final RecyclerView.Adapter<?> adapter = viewHolder.getBindingAdapter();
        final int position = viewHolder.getBindingAdapterPosition();
        if (adapter != null && position != RecyclerView.NO_POSITION) {
            adapter.notifyItemChanged(position);
        }

        if (item == null || fragment.getContext() == null) {
            return;
        }

        final SwipeAction action = direction == ItemTouchHelper.RIGHT
                ? SwipeActionPreferences.getRightAction(fragment.requireContext())
                : SwipeActionPreferences.getLeftAction(fragment.requireContext());

        try {
            action.action.onClick(fragment, item);
        } catch (final Exception e) {
            ErrorUtil.showUiErrorSnackbar(fragment, "Performing swipe action " + action, e);
        }
    }
}
