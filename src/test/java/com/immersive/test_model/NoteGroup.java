package com.immersive.test_model;


import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

public class NoteGroup extends NoteGroupOrTuplet {
    List<Note> notes = new ArrayList<>();
    public boolean stemUp;

    public NoteGroup(NoteTimeTick noteTimeTick, Staff staff, Voice voice, int duration, boolean stemUp) {
        super(noteTimeTick, staff, voice, duration);
        this.stemUp = stemUp;
    }

    //this constructor the transactional logic is looking for
    private NoteGroup(NoteTimeTick noteTimeTick, Voice voice) {
        super(noteTimeTick, voice);
    }
    //this method the transactional logic is looking for in order to atomically delete objects
    private void destruct() {
        getOwner().noteGroupOrTuplets.remove(getKey());
    }

    @Override
    public boolean clear() {
        if (!super.clear()) {
            getOwner().noteGroupOrTuplets.remove(getKey());
            new ArrayList<>(notes).forEach(Note::clear);
        }
        return true;
    }

    public Note getNote(int idx) {
        return notes.get(idx);
    }
}
