package com.immersive.test_model;

import java.util.ArrayList;
import java.util.List;

public class Tuplet extends NoteGroupOrTuplet {

    List<NoteGroup> noteGroups = new ArrayList<>();

    //this constructor the transactional logic is looking for
    private Tuplet(NoteTimeTick noteTimeTick, Voice voice) {
        super(noteTimeTick, voice);
    }
    //this method the transactional logic is looking for in order to atomically delete objects
    protected void onRemove() {
        getOwner().noteGroupOrTuplets.remove(getKey());
    }

    public Tuplet(NoteTimeTick noteTimeTick, Staff staff, Voice voice, int duration) {
        super(noteTimeTick, staff, voice, duration);
    }

    public NoteGroup getNoteGroup(int idx) {
        return noteGroups.get(idx);
    }
}
