package org.schabi.newpipe.qpipepipe.selection;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.ThemeHelper;

/**
 * Draws the selected state over the rows, so no list layout needs to know about selection:
 * a tint over the row, the thumbnail dimmed and a check mark on it. Rows without a thumbnail
 * view (the experimental Compose rows) get the check mark at their start.
 */
final class SelectionDecoration extends RecyclerView.ItemDecoration {
    interface IsSelected {
        boolean at(int adapterPosition);
    }

    @NonNull
    private final IsSelected isSelected;
    private final Paint rowPaint = new Paint();
    private final Paint dimPaint = new Paint();
    private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    @Nullable
    private final Drawable check;
    private final float radius;
    private final Rect bounds = new Rect();

    SelectionDecoration(@NonNull final Context context, @NonNull final IsSelected isSelected) {
        this.isSelected = isSelected;
        final int accent = ThemeHelper.resolveColorFromAttr(context, R.attr.colorPrimary);
        rowPaint.setColor(ColorUtils.setAlphaComponent(accent, 40));
        dimPaint.setColor(ColorUtils.setAlphaComponent(Color.BLACK, 140));
        circlePaint.setColor(accent);
        final float density = context.getResources().getDisplayMetrics().density;
        radius = 16 * density;
        final Drawable icon = AppCompatResources.getDrawable(context, R.drawable.ic_done);
        if (icon != null) {
            check = DrawableCompat.wrap(icon.mutate());
            DrawableCompat.setTint(check, Color.WHITE);
            final int half = Math.round(10 * density);
            check.setBounds(-half, -half, half, half);
        } else {
            check = null;
        }
    }

    @Override
    public void onDrawOver(@NonNull final Canvas canvas, @NonNull final RecyclerView parent,
                           @NonNull final RecyclerView.State state) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            final View row = parent.getChildAt(i);
            final int position = parent.getChildAdapterPosition(row);
            if (position == RecyclerView.NO_POSITION || !isSelected.at(position)) {
                continue;
            }
            final float dy = row.getTranslationY();
            final float dx = row.getTranslationX();
            canvas.drawRect(row.getLeft() + dx, row.getTop() + dy, row.getRight() + dx,
                    row.getBottom() + dy, rowPaint);

            final View thumbnail = row.findViewById(R.id.itemThumbnailView);
            final float cx;
            final float cy;
            if (thumbnail != null && thumbnail.getWidth() > 0) {
                thumbnail.getDrawingRect(bounds);
                parent.offsetDescendantRectToMyCoords(thumbnail, bounds);
                bounds.offset(Math.round(dx), Math.round(dy));
                canvas.drawRect(bounds, dimPaint);
                cx = bounds.exactCenterX();
                cy = bounds.exactCenterY();
            } else {
                cx = row.getLeft() + dx + radius * 2;
                cy = row.getTop() + dy + row.getHeight() / 2f;
            }
            canvas.drawCircle(cx, cy, radius, circlePaint);
            if (check != null) {
                final int save = canvas.save();
                canvas.translate(cx, cy);
                check.draw(canvas);
                canvas.restoreToCount(save);
            }
        }
    }
}
