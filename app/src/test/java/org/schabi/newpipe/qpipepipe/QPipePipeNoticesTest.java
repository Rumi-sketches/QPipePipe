package org.schabi.newpipe.qpipepipe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.schabi.newpipe.util.AnnouncementParser;

public class QPipePipeNoticesTest {

    private static final String PAGE = "Announcements for QPipePipe users.\n"
            + "\n"
            + "## 261102\n"
            + "Second announcement.\n"
            + "\n"
            + "---\n"
            + "## 261020\r\n"
            + "First announcement,\r\n"
            + "on two lines.\r\n";

    @Test
    public void firstCheckShowsEverySection() {
        final AnnouncementParser.ParsedResult result =
                QPipePipeNotices.parseAnnouncements(PAGE, null);
        assertEquals("261102", result.latestId);
        assertEquals("## 261102\nSecond announcement.\n\n"
                + "## 261020\nFirst announcement,\non two lines.\n", result.contents);
    }

    @Test
    public void stopsAtTheLastSeenSection() {
        final AnnouncementParser.ParsedResult result =
                QPipePipeNotices.parseAnnouncements(PAGE, "261020");
        assertEquals("261102", result.latestId);
        assertEquals("## 261102\nSecond announcement.\n", result.contents);
    }

    @Test
    public void upstreamNotesOnlyWhenTheUpstreamReleaseChanged() {
        assertTrue(QPipePipeNotices.upstreamChanged(1109021, 1110011)); // 5.4.0-q2 -> 5.5.0-q1
        assertTrue(QPipePipeNotices.upstreamChanged(0, 1110011)); // first install
        assertFalse(QPipePipeNotices.upstreamChanged(1110011, 1110021)); // fork release only
    }

    @Test
    public void nothingNewOrNoSections() {
        assertNull(QPipePipeNotices.parseAnnouncements(PAGE, "261102"));
        assertNull(QPipePipeNotices.parseAnnouncements("404: Not Found", null));
    }
}
