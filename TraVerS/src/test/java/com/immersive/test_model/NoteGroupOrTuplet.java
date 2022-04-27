package com.immersive.test_model;

import com.immersive.annotations.CrossReference;
import com.immersive.annotations.Polymorphic;
import com.immersive.transactions.KeyedChildEntity;

@Polymorphic(subclasses = {NoteGroup.class, Tuplet.class})
public abstract class NoteGroupOrTuplet extends KeyedChildEntity<NoteTimeTick, Voice> {
    @CrossReference
    Staff staff;
    int duration;

    //this constructor the transactional logic is looking for
    protected NoteGroupOrTuplet(NoteTimeTick noteTimeTick, Voice voice) {
        super(noteTimeTick, voice);
        noteTimeTick.noteGroupOrTuplets.put(voice, this);
    }

    public NoteGroupOrTuplet(NoteTimeTick noteTimeTick, Staff staff, Voice voice, int duration) {
        super(noteTimeTick, voice);
        this.staff = staff;
        this.duration = duration;
        noteTimeTick.noteGroupOrTuplets.put(voice, this);
    }
}
