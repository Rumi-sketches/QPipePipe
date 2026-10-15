package org.schabi.newpipe.qpipepipe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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
    public void nothingNewOrNoSections() {
        assertNull(QPipePipeNotices.parseAnnouncements(PAGE, "261102"));
        assertNull(QPipePipeNotices.parseAnnouncements("404: Not Found", null));
    }
}
