package org.schabi.newpipe.info_list.swipe;

import static org.schabi.newpipe.util.SparseItemUtil.fetchItemInfoIfSparse;

import android.content.Context;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.info_list.dialog.StreamDialogDefaultEntry;
import org.schabi.newpipe.info_list.dialog.StreamDialogEntry;
import org.schabi.newpipe.player.helper.PlayerHolder;
import org.schabi.newpipe.util.NavigationHelper;

/**
 * <p>
 *     The actions that can be triggered by swiping a stream item left or right
 *     when the "swipe actions" feature is enabled in the settings.
 * </p>
 * <p>
 *     Each action reuses the same logic as its {@link StreamDialogDefaultEntry} counterpart
 *     (the one shown in the long-press context menu), so behaviour stays consistent between
 *     the two entry points. Most actions make sense regardless of which screen the stream
 *     item is displayed on (homepage, search, playlists, channel groups). A few, marked
 *     {@link #contextSpecific}, only work on some screens (e.g. {@link #REMOVE_FROM_PLAYLIST}
 *     only makes sense inside a playlist) and are disabled wherever there is no special
 *     handling for them, see {@link StreamSwipeItemTouchCallback#getSwipeDirs}.
 * </p>
 * <p>
 *     Each entry also carries a distinct background {@link #colorRes} and {@link #iconRes}
 *     which are drawn behind the item while it is being swiped, so the user can tell which
 *     action is about to be triggered before releasing.
 * </p>
 */
public enum SwipeAction {

    /**
     * Unlike the long-press menu's {@link StreamDialogDefaultEntry#ENQUEUE}, this falls back to
     * just playing the video normally when there is no active player to enqueue onto, instead
     * of silently starting it in the background (see {@link NavigationHelper#enqueueOnPlayer}).
     */
    ENQUEUE(
            StreamDialogDefaultEntry.ENQUEUE.resource,
            R.drawable.ic_playlist_play,
            R.color.swipe_action_enqueue,
            (fragment, item) -> {
                final Context context = fragment.requireContext();
                if (!PlayerHolder.getInstance().isPlayerOpen()) {
                    NavigationHelper.openVideoDetailFragment(context,
                            getNavigationFM(fragment), item.getServiceId(),
                            item.getUrl(), item.getName(), null, false);
                    return;
                }
                fetchItemInfoIfSparse(context, item, singlePlayQueue ->
                        NavigationHelper.enqueueOnPlayer(context, singlePlayQueue));
            }
    ),

    /** Same fallback as {@link #ENQUEUE}, see its doc. */
    ENQUEUE_NEXT(
            StreamDialogDefaultEntry.ENQUEUE_NEXT.resource,
            R.drawable.ic_playlist_add_check,
            R.color.swipe_action_enqueue_next,
            (fragment, item) -> {
                final Context context = fragment.requireContext();
                if (!PlayerHolder.getInstance().isPlayerOpen()) {
                    NavigationHelper.openVideoDetailFragment(context,
                            getNavigationFM(fragment), item.getServiceId(),
                            item.getUrl(), item.getName(), null, false);
                    return;
                }
                fetchItemInfoIfSparse(context, item, singlePlayQueue ->
                        NavigationHelper.enqueueNextOnPlayer(context, singlePlayQueue));
            }
    ),

    START_HERE_ON_BACKGROUND(
            StreamDialogDefaultEntry.START_HERE_ON_BACKGROUND.resource,
            R.drawable.ic_headset,
            R.color.swipe_action_start_background,
            StreamDialogDefaultEntry.START_HERE_ON_BACKGROUND.action
    ),

    START_HERE_ON_POPUP(
            StreamDialogDefaultEntry.START_HERE_ON_POPUP.resource,
            R.drawable.ic_play_arrow,
            R.color.swipe_action_start_popup,
            StreamDialogDefaultEntry.START_HERE_ON_POPUP.action
    ),

    APPEND_PLAYLIST(
            StreamDialogDefaultEntry.APPEND_PLAYLIST.resource,
            R.drawable.ic_playlist_add,
            R.color.swipe_action_append_playlist,
            StreamDialogDefaultEntry.APPEND_PLAYLIST.action
    ),

    SHARE(
            StreamDialogDefaultEntry.SHARE.resource,
            R.drawable.ic_share,
            R.color.swipe_action_share,
            StreamDialogDefaultEntry.SHARE.action
    ),

    DOWNLOAD(
            StreamDialogDefaultEntry.DOWNLOAD.resource,
            R.drawable.ic_file_download,
            R.color.swipe_action_download,
            StreamDialogDefaultEntry.DOWNLOAD.action
    ),

    OPEN_IN_BROWSER(
            StreamDialogDefaultEntry.OPEN_IN_BROWSER.resource,
            R.drawable.ic_public,
            R.color.swipe_action_open_in_browser,
            StreamDialogDefaultEntry.OPEN_IN_BROWSER.action
    ),

    MARK_AS_WATCHED(
            StreamDialogDefaultEntry.MARK_AS_WATCHED.resource,
            R.drawable.ic_visibility_on,
            R.color.swipe_action_mark_as_watched,
            StreamDialogDefaultEntry.MARK_AS_WATCHED.action
    ),

    /**
     * Only meaningful inside a playlist, since it needs to know which playlist entry to
     * remove, not just which {@link org.schabi.newpipe.extractor.stream.StreamInfoItem} was
     * swiped. Like {@link StreamDialogDefaultEntry#DELETE}, the default action throws and
     * must be handled manually by the screen that supports it (see
     * {@code LocalPlaylistFragment}).
     */
    REMOVE_FROM_PLAYLIST(
            R.string.remove_from_playlist_swipe_action,
            R.drawable.ic_delete,
            R.color.swipe_action_remove_from_playlist,
            true,
            (fragment, item) -> {
                throw new UnsupportedOperationException("This needs to be implemented manually "
                        + "by the fragment that supports it, e.g. LocalPlaylistFragment");
            }
    );

    @StringRes
    public final int nameRes;
    @DrawableRes
    public final int iconRes;
    @ColorRes
    public final int colorRes;
    /**
     * Whether this action only works on some screens (see the class doc). Screens without
     * special handling for it should disable swiping in the direction it is bound to,
     * rather than letting the gesture silently do nothing.
     */
    public final boolean contextSpecific;
    @NonNull
    public final StreamDialogEntry.StreamDialogEntryAction action;

    SwipeAction(@StringRes final int nameRes,
                @DrawableRes final int iconRes,
                @ColorRes final int colorRes,
                @NonNull final StreamDialogEntry.StreamDialogEntryAction action) {
        this(nameRes, iconRes, colorRes, false, action);
    }

    SwipeAction(@StringRes final int nameRes,
                @DrawableRes final int iconRes,
                @ColorRes final int colorRes,
                final boolean contextSpecific,
                @NonNull final StreamDialogEntry.StreamDialogEntryAction action) {
        this.nameRes = nameRes;
        this.iconRes = iconRes;
        this.colorRes = colorRes;
        this.contextSpecific = contextSpecific;
        this.action = action;
    }

    /**
     * Looks up a {@link SwipeAction} by its {@link #name()}, falling back to
     * {@code fallback} if {@code value} is null, blank or unknown.
     *
     * @param value the persisted preference value, expected to be a {@link SwipeAction} name
     * @param fallback the action to return if {@code value} doesn't resolve to one
     * @return the matching {@link SwipeAction}, or {@code fallback}
     */
    @NonNull
    public static SwipeAction fromPreferenceValue(final String value,
                                                  @NonNull final SwipeAction fallback) {
        if (value == null || value.isEmpty()) {
            return fallback;
        }
        for (final SwipeAction action : values()) {
            if (action.name().equals(value)) {
                return action;
            }
        }
        return fallback;
    }

    /**
     * Returns the {@link FragmentManager} that should be used to navigate to
     * {@link org.schabi.newpipe.fragments.detail.VideoDetailFragment}, replicating the
     * {@code BaseFragment.getFM()} logic: go up one level so the transaction targets the
     * activity-level container ({@code fragment_player_holder}) rather than any nested
     * child container.
     */
    @NonNull
    private static FragmentManager getNavigationFM(@NonNull final Fragment fragment) {
        final Fragment parent = fragment.getParentFragment();
        return parent == null
                ? fragment.getParentFragmentManager()
                : parent.getParentFragmentManager();
    }
}
