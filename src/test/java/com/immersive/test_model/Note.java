package com.immersive.test_model;


import com.immersive.transactions.ChildEntity;

public class Note extends ChildEntity<NoteGroup> {
    int pitch;
    boolean accidental;
    NoteName noteName;
    Note previousTied;
    Note nextTied;

    //this constructor the transactional logic is looking for
    private Note(NoteGroup noteGroup) {
        super(noteGroup);
        noteGroup.notes.add(this);
    }
    //this method the transactional logic is looking for in order to atomically delete objects
    protected void onRemove() {
        getOwner().notes.remove(this);
        if (nextTied != null) {
            nextTied.previousTied = null;
        }
        if (previousTied != null) {
            previousTied.nextTied = null;
        }
    }

    public Note(NoteGroup noteGroup, int pitch, boolean accidental, NoteName noteName) {
        super(noteGroup);
        this.pitch = pitch;
        this.noteName = noteName;
        this.accidental = accidental;
        noteGroup.notes.add(this);
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

    public Note getPreviousTied() {
        return previousTied;
    }

    public Note getNextTied() {
        return nextTied;
    }
}
