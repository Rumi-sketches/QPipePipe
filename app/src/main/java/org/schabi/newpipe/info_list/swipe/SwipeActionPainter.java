package org.schabi.newpipe.info_list.swipe;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

/**
 * Draws the colored background and icon behind a stream item while it is being swiped.
 * Shared between {@link StreamSwipeItemTouchCallback} and any fragment-specific touch callback
 * (e.g. {@code LocalPlaylistFragment}'s, which also handles drag-to-reorder and so can't simply
 * delegate to {@link StreamSwipeItemTouchCallback}).
 */
public final class SwipeActionPainter {

    private SwipeActionPainter() {
    }

    // Reused across every drawBackground() call (always on the main thread).
    private static final Paint BACKGROUND_PAINT = new Paint();

    /**
     * Paints {@code action}'s background color and icon behind {@code itemView}, clipped to
     * how far it has currently been dragged ({@code dX}).
     *
     * @param canvas the canvas to draw on, as given to {@code onChildDraw()}
     * @param itemView the view being swiped
     * @param dX how far, and in which direction, {@code itemView} has been dragged
     * @param action the action whose color and icon should be drawn
     */
    public static void drawBackground(@NonNull final Canvas canvas,
                                      @NonNull final View itemView,
                                      final float dX,
                                      @NonNull final SwipeAction action) {
        if (dX == 0) {
            return;
        }
        final boolean swipingRight = dX > 0;
        final Context context = itemView.getContext();
        BACKGROUND_PAINT.setColor(ContextCompat.getColor(context, action.colorRes));

        final float left = swipingRight ? itemView.getLeft() : itemView.getRight() + dX;
        final float right = swipingRight ? itemView.getLeft() + dX : itemView.getRight();
        canvas.drawRect(left, itemView.getTop(), right, itemView.getBottom(), BACKGROUND_PAINT);

        final Drawable icon = ContextCompat.getDrawable(context, action.iconRes);
        if (icon == null) {
            return;
        }
        final int iconSize = icon.getIntrinsicHeight() > 0
                ? icon.getIntrinsicHeight() : itemView.getHeight() / 3;
        final int iconMargin = (itemView.getHeight() - iconSize) / 2;
        final int iconTop = itemView.getTop() + (itemView.getHeight() - iconSize) / 2;
        final int iconBottom = iconTop + iconSize;

        final int iconLeft;
        final int iconRight;
        if (swipingRight) {
            iconLeft = itemView.getLeft() + iconMargin;
            iconRight = iconLeft + iconSize;
        } else {
            iconRight = itemView.getRight() - iconMargin;
            iconLeft = iconRight - iconSize;
        }

        // Only draw the icon once the revealed colored area is large enough to fit it.
        if (right - left >= iconSize + (2 * iconMargin)) {
            icon.setTint(ContextCompat.getColor(context, android.R.color.white));
            icon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
            icon.draw(canvas);
        }
    }
}
