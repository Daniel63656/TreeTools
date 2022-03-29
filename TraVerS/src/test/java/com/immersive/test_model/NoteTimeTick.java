package com.immersive.test_model;

import com.immersive.annotations.ChildField;
import com.immersive.annotations.OwnerField;
import com.immersive.annotations.TransactionalEntity;

import java.util.ArrayList;
import java.util.List;

public class NoteTimeTick implements TransactionalEntity<Track> {
    @OwnerField
    Track track;
    long tick;
    @ChildField
    List<NoteGroupOrTuplet> noteGroupOrTuplets = new ArrayList<>();


    //this constructor the transactional logic is looking for
    private NoteTimeTick(Track track) {
        this.track = track;
        track.noteTimeTicks.add(this);
    }
    //this method the transactional logic is looking for in order to atomically delete objects
    private void destruct() {
        track.noteTimeTicks.remove(this);
    }

    public NoteTimeTick(Track track, long tick) {
        this.track = track;
        this.tick = tick;
        track.noteTimeTicks.add(this);
    }

    public NoteGroupOrTuplet getNGOT(int idx) {
        return noteGroupOrTuplets.get(idx);
    }

    @Override
    public Track getOwner() {
        return track;
    }
}
