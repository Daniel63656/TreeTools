package net.scoreworks.test_model;


import net.scoreworks.treetools.Child;

public class Note extends Child<NoteGroup> {
    int pitch;
    boolean accidental;
    NoteName noteName;
    Note previousTied;
    Note nextTied;

    //this constructor the transactional logic is looking for
    private Note(NoteGroup noteGroup) {
        super(noteGroup);
        addToOwner();
    }
    //this method the transactional logic is looking for in order to atomically delete objects
    protected void removeFromOwner() {
        getOwner().notes.remove(this);
    }
    protected void onRemove() {
        if (nextTied != null) {
            nextTied.previousTied = null;
        }
        if (previousTied != null) {
            previousTied.nextTied = null;
        }
    }
    protected void addToOwner() {
        getOwner().notes.add(this);
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
