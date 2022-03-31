package com.immersive.test_model;

import com.immersive.annotations.CrossReference;
import com.immersive.core.ChildEntity;

public abstract class NoteGroupOrTuplet extends ChildEntity<NoteTimeTick> {
    @CrossReference
    Staff staff;
    @CrossReference
    Voice voice;
    int duration;

    //this constructor the transactional logic is looking for
    protected NoteGroupOrTuplet(NoteTimeTick noteTimeTick) {
        super(noteTimeTick);
        noteTimeTick.noteGroupOrTuplets.add(this);
    }

    public NoteGroupOrTuplet(NoteTimeTick noteTimeTick, Staff staff, Voice voice, int duration) {
        super(noteTimeTick);
        this.staff = staff;
        this.voice = voice;
        this.duration = duration;
        noteTimeTick.noteGroupOrTuplets.add(this);
    }
}
