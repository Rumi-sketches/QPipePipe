package org.schabi.newpipe.qpipepipe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class DebugBuildNotesTest {
    @Test
    public void numberComesFromTheFirstLineAndNotesFromTheRest() {
        final DebugBuildNotes.Notes notes =
                DebugBuildNotes.parse("3\r\n- Android Auto: resume\n- Headphones\n");
        assertEquals(3, notes.number);
        assertEquals("- Android Auto: resume\n- Headphones", notes.text);
    }

    @Test
    public void numberAloneHasNoNotes() {
        assertEquals("", DebugBuildNotes.parse("4").text);
    }

    @Test
    public void withoutANumberThereIsNothingToShow() {
        assertNull(DebugBuildNotes.parse("notes without number"));
        assertNull(DebugBuildNotes.parse(null));
    }
}
