package org.schabi.newpipe.qpipepipe;

import static org.junit.Assert.assertSame;

import org.junit.Test;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.info_list.InfoListAdapter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Swipe actions resolve the video of a row with {@link InfoListAdapter#getStreamItem}: it must be
 * the video shown in that row, also when upstream's toolbar filter or sorting is on. The adapter
 * needs a Context to be built, so the test allocates it without the constructor.
 */
public class InfoListAdapterSwipeItemTest {
    private static StreamInfoItem item(final String name) {
        return new StreamInfoItem(0, "https://example.com/" + name, name, StreamType.VIDEO_STREAM);
    }

    private static InfoListAdapter adapter(final List<InfoItem> items) throws Exception {
        // sun.misc.Unsafe is not in the Android compile classpath, only in the test JVM
        final Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        final Field f = unsafeClass.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        final InfoListAdapter a = (InfoListAdapter) unsafeClass
                .getMethod("allocateInstance", Class.class)
                .invoke(f.get(null), InfoListAdapter.class);
        set(a, "infoItemList", items);
        set(a, "filteredItemList", new ArrayList<InfoItem>());
        set(a, "sortedItemList", new ArrayList<InfoItem>());
        set(a, "filterText", "");
        return a;
    }

    private static void set(final Object o, final String name, final Object v) throws Exception {
        final Field f = InfoListAdapter.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(o, v);
    }

    @SuppressWarnings("unchecked")
    private static InfoItem shownAt(final InfoListAdapter a, final int row) throws Exception {
        final Method apply = InfoListAdapter.class.getDeclaredMethod("applyFilter");
        apply.setAccessible(true);
        apply.invoke(a);
        final Method shown = InfoListAdapter.class.getDeclaredMethod("shownList");
        shown.setAccessible(true);
        return ((List<InfoItem>) shown.invoke(a)).get(row);
    }

    @Test
    public void swipeOnFirstRowOfReversedPlaylistResolvesTheShownVideo() throws Exception {
        final List<InfoItem> items = new ArrayList<>(List.of(item("A"), item("B"), item("C")));
        final InfoListAdapter a = adapter(items);
        set(a, "reverseOrigin", true); // sort "origin, reversed" of PlaylistFragment
        final InfoItem shown = shownAt(a, 0); // row 0 shows C
        assertSame("row 0 shows " + shown.getName() + ", swipe acts on "
                + a.getStreamItem(0).getName(), shown, a.getStreamItem(0));
    }

    @Test
    public void swipeOnFirstRowOfFilteredListResolvesTheShownVideo() throws Exception {
        final List<InfoItem> items = new ArrayList<>(List.of(item("A"), item("B"), item("C")));
        final InfoListAdapter a = adapter(items);
        set(a, "filterText", "c"); // toolbar filter: only C is shown
        final InfoItem shown = shownAt(a, 0);
        assertSame("row 0 shows " + shown.getName() + ", swipe acts on "
                + a.getStreamItem(0).getName(), shown, a.getStreamItem(0));
    }
}
