package net.scoreworks.test_model;

import net.scoreworks.treetools.Child;

public class Staff extends Child<Track> {
    boolean treble;

    //this constructor the transactional logic is looking for
    private Staff(Track track) {
        super(track);
        track.staffs.add(this);
    }

    //this method the transactional logic is looking for in order to atomically delete objects
    protected void removeFromOwner() {
        getOwner().staffs.remove(this);
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
