package com.immersive.test_model;

import com.immersive.annotations.OwnerField;
import com.immersive.annotations.TransactionalEntity;

public class Staff implements TransactionalEntity<Track> {
    @OwnerField
    Track track;
    boolean treble;

    //this constructor the transactional logic is looking for
    private Staff(Track track) {
        this.track = track;
        track.staffs.add(this);
    }
    //this method the transactional logic is looking for in order to atomically delete objects
    private void destruct() {
        track.staffs.remove(this);
    }

    public Staff(Track track, boolean treble) {
        this.track = track;
        this.treble = treble;
        track.staffs.add(this);
    }

    public boolean isTreble() {
        return treble;
    }

    public void setTreble(boolean treble) {
        this.treble = treble;
    }

    @Override
    public Track getOwner() {
        return track;
    }
}
