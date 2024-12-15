package net.scoreworks.test_model;

import net.scoreworks.treetools.Child;
import net.scoreworks.treetools.annotations.TransactionalConstructor;

public class Staff extends Child<Track> {
    boolean treble;

    @TransactionalConstructor
    private Staff(Track track) {
        super(track);
    }

    //this method the transactional logic is looking for in order to atomically delete objects
    protected void removeFromOwner() {
        getOwner().staffs.remove(this);
    }
    protected void addToOwner() {
        getOwner().staffs.add(this);
    }

    public Staff(Track track, boolean treble) {
        super(track);
        this.treble = treble;
        track.staffs.add(this);
    }

    public boolean isTreble() {
        return treble;
    }

    public void setTreble(boolean treble) {
        this.treble = treble;
    }
}
