package net.scoreworks.test_model;

import java.util.ArrayList;
import java.util.List;

public class Tuplet extends NoteGroupOrTuplet {

    List<NoteGroup> noteGroups = new ArrayList<>();

    private Tuplet(NoteTimeTick noteTimeTick, Voice voice) {
        super(noteTimeTick, voice);
    }

    public Tuplet(NoteTimeTick noteTimeTick, Staff staff, Voice voice, int duration) {
        super(noteTimeTick, staff, voice, duration);
    }

    public NoteGroup getNoteGroup(int idx) {
        return noteGroups.get(idx);
    }
}
