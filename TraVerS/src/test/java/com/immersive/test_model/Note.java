package com.immersive.test_model;

import com.immersive.annotations.CrossReference;
import com.immersive.annotations.OwnerField;
import com.immersive.annotations.TransactionalEntity;

public class Note implements TransactionalEntity<NoteGroup> {
    @OwnerField
    NoteGroup noteGroup;
    int pitch;
    boolean accidental;
    NoteName noteName;
    @CrossReference
    Note previousTied;
    @CrossReference
    Note nextTied;

    //this constructor the transactional logic is looking for
    private Note(NoteGroup noteGroup) {
        this.noteGroup = noteGroup;
        noteGroup.notes.add(this);
    }
    //this method the transactional logic is looking for in order to atomically delete objects
    private void destruct() {
        noteGroup.notes.remove(this);
    }

    public Note(NoteGroup noteGroup, int pitch, boolean accidental, NoteName noteName) {
        this.noteGroup = noteGroup;
        this.pitch = pitch;
        this.noteName = noteName;
        this.accidental = accidental;
        noteGroup.notes.add(this);
    }

    public void clear() {
        noteGroup.notes.remove(this);
    }

    public void setPitch(int pitch) {
        this.pitch = pitch;
    }

    public int getPitch() {
        return pitch;
    }

    public void setAccidental(boolean accidental) {
        this.accidental = accidental;
    }

    public boolean getAccidental() {
        return accidental;
    }

    public void tieWith(Note note) {
        this.nextTied = note;
        note.previousTied = this;
    }

    @Override
    public NoteGroup getOwner() {
        return noteGroup;
    }
}
