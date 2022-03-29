package com.immersive.test_model;

import com.immersive.annotations.CrossReference;
import com.immersive.annotations.OwnerField;
import com.immersive.annotations.TransactionalEntity;

public abstract class NoteGroupOrTuplet implements TransactionalEntity<NoteTimeTick> {
    @OwnerField
    NoteTimeTick noteTimeTick;
    @CrossReference
    Staff staff;
    @CrossReference
    Voice voice;
    int duration;

    //this constructor the transactional logic is looking for
    protected NoteGroupOrTuplet(NoteTimeTick noteTimeTick) {
        this.noteTimeTick = noteTimeTick;
        noteTimeTick.noteGroupOrTuplets.add(this);
    }

    @Override
    public NoteTimeTick getOwner() {
        return noteTimeTick;
    }

    public NoteGroupOrTuplet(NoteTimeTick noteTimeTick, Staff staff, Voice voice, int duration) {
        this.noteTimeTick = noteTimeTick;
        this.staff = staff;
        this.voice = voice;
        this.duration = duration;
        noteTimeTick.noteGroupOrTuplets.add(this);
    }
}
