package net.scoreworks.test_model;

import net.scoreworks.treetools.annotations.AbstractClass;
import net.scoreworks.treetools.MappedChild;
import net.scoreworks.treetools.annotations.TransactionalConstructor;

@AbstractClass(subclasses = {NoteGroup.class, Tuplet.class})
public abstract class NoteGroupOrTuplet extends MappedChild<NoteTimeTick, Voice> {
    Staff staff;
    int duration;

    public void setStaff(Staff staff) {
        this.staff = staff;
    }
    public Staff getStaff() {
        return staff;
    }

    @TransactionalConstructor
    protected NoteGroupOrTuplet(NoteTimeTick noteTimeTick, Voice voice) {
        super(noteTimeTick, voice);
    }

    protected void removeFromOwner() {
        getOwner().noteGroupOrTuplets.remove(getKey());
    }
    protected void addToOwner() {
        getOwner().noteGroupOrTuplets.put(getKey(), this);
    }

    public NoteGroupOrTuplet(NoteTimeTick noteTimeTick, Staff staff, Voice voice, int duration) {
        super(noteTimeTick, voice);
        this.staff = staff;
        this.duration = duration;
        noteTimeTick.noteGroupOrTuplets.put(voice, this);
    }
}
