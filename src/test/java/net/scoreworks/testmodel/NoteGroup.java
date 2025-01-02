package net.scoreworks.testmodel;


import java.util.ArrayList;
import java.util.List;

public class NoteGroup extends NoteGroupOrTuplet {
    List<Note> notes = new ArrayList<>();
    public boolean stemUp;

    public NoteGroup(NoteTimeTick noteTimeTick, Staff staff, Voice voice, int duration, boolean stemUp) {
        super(noteTimeTick, staff, voice, duration);
        this.stemUp = stemUp;
    }

    private NoteGroup(NoteTimeTick noteTimeTick, Voice voice) {
        super(noteTimeTick, voice);
    }

    public Note getNote(int idx) {
        return notes.get(idx);
    }
}
