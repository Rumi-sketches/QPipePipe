package org.schabi.newpipe.qpipepipe;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DebugBuildNotesTest {
    @Test
    public void notesAreTheLinesBelowTheNumber() {
        assertEquals("- Android Auto: resume\n- Headphones",
                DebugBuildNotes.notesOf("3\r\n- Android Auto: resume\n- Headphones\n"));
    }

    @Test
    public void numberAloneHasNoNotes() {
        assertEquals("", DebugBuildNotes.notesOf("4"));
        assertEquals("", DebugBuildNotes.notesOf("4\n\n"));
    }

    @Test
    public void withoutTheFileThereAreNoNotes() {
        assertEquals("", DebugBuildNotes.notesOf(null));
    }
}
