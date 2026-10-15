package org.schabi.newpipe.qpipepipe;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.ThemeHelper;

/**
 * Marks the video playback would resume from in a playlist: a bar along its left edge and a
 * "Resume here" label over its top left corner. Drawn over the row, so it works with every
 * item layout (list, grid, card) without touching their holders.
 */
final class PlaylistResumeDecoration extends RecyclerView.ItemDecoration {
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF label = new RectF();
    private final String text;
    private final float barWidth;
    private final float margin;
    private final float paddingH;
    private final float paddingV;

    private int targetPosition = RecyclerView.NO_POSITION;

    PlaylistResumeDecoration(@NonNull final Context context) {
        final float density = context.getResources().getDisplayMetrics().density;
        fillPaint.setColor(ThemeHelper.resolveColorFromAttr(context, R.attr.colorPrimary));
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(12 * context.getResources().getDisplayMetrics().scaledDensity);
        textPaint.setFakeBoldText(true);
        text = context.getString(R.string.playlist_resume_here);
        barWidth = 4 * density;
        margin = 8 * density;
        paddingH = 8 * density;
        paddingV = 3 * density;
    }

    /** @param adapterPosition the row to mark, or {@link RecyclerView#NO_POSITION} for none */
    void setTarget(@NonNull final RecyclerView list, final int adapterPosition) {
        if (targetPosition != adapterPosition) {
            targetPosition = adapterPosition;
            list.invalidate();
        }
    }

    @Override
    public void onDrawOver(@NonNull final Canvas canvas, @NonNull final RecyclerView parent,
                           @NonNull final RecyclerView.State state) {
        if (targetPosition == RecyclerView.NO_POSITION) {
            return;
        }
        for (int i = 0; i < parent.getChildCount(); i++) {
            final View child = parent.getChildAt(i);
            if (parent.getChildAdapterPosition(child) == targetPosition) {
                draw(canvas, child);
                return;
            }
        }
    }

    private void draw(@NonNull final Canvas canvas, @NonNull final View child) {
        // follow the row while it is being swiped or animated
        final float left = child.getLeft() + child.getTranslationX();
        final float top = child.getTop() + child.getTranslationY();
        final int alpha = Math.round(child.getAlpha() * 255);
        fillPaint.setAlpha(alpha);
        textPaint.setAlpha(alpha);

        canvas.drawRect(left, top, left + barWidth, top + child.getHeight(), fillPaint);

        final Paint.FontMetrics font = textPaint.getFontMetrics();
        label.set(left + barWidth + margin, top + margin,
                left + barWidth + margin + textPaint.measureText(text) + 2 * paddingH,
                top + margin + (font.descent - font.ascent) + 2 * paddingV);
        final float radius = label.height() / 2;
        canvas.drawRoundRect(label, radius, radius, fillPaint);
        canvas.drawText(text, label.left + paddingH, label.top + paddingV - font.ascent,
                textPaint);
    }
}
