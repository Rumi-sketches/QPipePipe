package org.schabi.newpipe.qpipepipe.selection;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ImageSpan;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.LocalItem;
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.fragments.list.comments.CommentsFragment;
import org.schabi.newpipe.info_list.dialog.StreamDialogEntry;
import org.schabi.newpipe.local.LocalItemListAdapter;
import org.schabi.newpipe.local.bookmark.BookmarkFragment;
import org.schabi.newpipe.util.ThemeHelper;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Multiple selection of videos in a list: "Multiple selection" in the long-press menu or the
 * toolbar button turns the list into selection mode, where taps select videos and a bar at the
 * bottom applies one action to all of them. Lists opt in with {@link #install}.
 */
public final class MultiSelect {
    /** The video bound at an adapter position, or null for headers, footers and non-videos. */
    public interface StreamResolver {
        @Nullable
        StreamInfoItem resolve(int adapterPosition);

        /**
         * What identifies the video at the position while the list reloads or shifts; the url
         * unless a list can hold the same video twice.
         */
        @Nullable
        default String keyAt(final int adapterPosition) {
            final StreamInfoItem item = resolve(adapterPosition);
            return item == null ? null : item.getServiceId() + ":" + item.getUrl();
        }
    }

    /**
     * Resolver of a local list. Videos of a local playlist are told apart by their place in the
     * playlist, which stays put while selecting (reordering is off), as a playlist can hold the
     * same video twice.
     */
    @NonNull
    public static StreamResolver localResolver(@NonNull final LocalItemListAdapter adapter) {
        return new StreamResolver() {
            @Nullable
            @Override
            public StreamInfoItem resolve(final int adapterPosition) {
                return adapter.getStreamItem(adapterPosition);
            }

            @Nullable
            @Override
            public String keyAt(final int adapterPosition) {
                final LocalItem item = adapter.getLocalItem(adapterPosition);
                if (item instanceof PlaylistStreamEntry) {
                    return "join:" + ((PlaylistStreamEntry) item).getJoinIndex();
                }
                return StreamResolver.super.keyAt(adapterPosition);
            }
        };
    }

    /** Removes videos from the list's own data, e.g. a local playlist or the history. */
    public interface Remover {
        @StringRes
        int label();

        void remove(@NonNull List<Integer> adapterPositions);
    }

    private static final Map<Fragment, SelectionController> CONTROLLERS = new WeakHashMap<>();

    private MultiSelect() {
    }

    /**
     * Enables multiple selection on {@code list} for as long as the fragment's view lives.
     * Call it each time the view is created.
     */
    public static void install(@NonNull final Fragment fragment,
                               @NonNull final RecyclerView list,
                               @NonNull final StreamResolver resolver,
                               @Nullable final Remover remover) {
        if (!holdsVideos(fragment)) {
            return;
        }
        final SelectionController old = CONTROLLERS.remove(fragment);
        if (old != null) {
            old.dispose();
        }
        final SelectionController controller =
                new SelectionController(fragment, list, resolver, remover);
        CONTROLLERS.put(fragment, controller);
        fragment.getViewLifecycleOwner().getLifecycle().addObserver(
                new DefaultLifecycleObserver() {
                    @Override
                    public void onDestroy(@NonNull final LifecycleOwner owner) {
                        if (CONTROLLERS.get(fragment) == controller) {
                            CONTROLLERS.remove(fragment);
                        }
                        controller.dispose();
                    }
                });
    }

    /**
     * The base list classes install selection everywhere; the lists that never hold videos are
     * excluded here, so their upstream classes stay untouched.
     */
    private static boolean holdsVideos(@NonNull final Fragment fragment) {
        return !(fragment instanceof CommentsFragment) && !(fragment instanceof BookmarkFragment);
    }

    /** Whether {@code fragment} is in selection mode: taps select instead of opening. */
    public static boolean isSelecting(@Nullable final Fragment fragment) {
        final SelectionController controller = CONTROLLERS.get(fragment);
        return controller != null && controller.isSelecting();
    }

    /** Puts "Multiple selection" first in a long-press menu of {@code fragment}. */
    public static void addDialogEntry(@NonNull final Fragment fragment,
                                      @NonNull final List<StreamDialogEntry> entries) {
        final SelectionController controller = CONTROLLERS.get(fragment);
        if (controller != null) {
            entries.add(0, new StreamDialogEntry(R.string.qpipepipe_multi_select,
                    (f, item) -> controller.startFromLongPress(item)));
        }
    }

    /** The text of a long-press menu entry; "Multiple selection" gets an icon. */
    @NonNull
    public static CharSequence dialogLabel(@NonNull final Context context,
                                           @NonNull final StreamDialogEntry entry) {
        final String text = entry.getString(context);
        if (entry.resource != R.string.qpipepipe_multi_select) {
            return text;
        }
        final Drawable icon = AppCompatResources.getDrawable(context, R.drawable.ic_checklist);
        if (icon == null) {
            return text;
        }
        final Drawable tinted = DrawableCompat.wrap(icon.mutate());
        DrawableCompat.setTint(tinted, ThemeHelper.resolveColorFromAttr(context,
                R.attr.colorPrimary));
        final int size = Math.round(20 * context.getResources().getDisplayMetrics().density);
        tinted.setBounds(0, 0, size, size);
        final SpannableStringBuilder label = new SpannableStringBuilder("  ").append(text);
        label.setSpan(new ImageSpan(tinted, DynamicDrawableSpan.ALIGN_BOTTOM), 0, 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return label;
    }

    /** Adds the toolbar button that starts selection mode, if the list supports it. */
    public static void addMenuItem(@NonNull final Fragment fragment, @NonNull final Menu menu) {
        if (!holdsVideos(fragment) || menu.findItem(R.id.qpipepipe_menu_multi_select) != null) {
            return;
        }
        menu.add(Menu.NONE, R.id.qpipepipe_menu_multi_select, Menu.NONE,
                        R.string.qpipepipe_multi_select)
                .setIcon(R.drawable.ic_checklist)
                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                .setOnMenuItemClickListener(item -> {
                    final SelectionController controller = CONTROLLERS.get(fragment);
                    if (controller != null) {
                        controller.start(null);
                    }
                    return true;
                });
    }
}
