package com.immersive.test_model;

import com.immersive.annotations.ChildField;
import com.immersive.core.KeyedChildEntity;

import java.util.ArrayList;
import java.util.List;

public class NoteTimeTick extends KeyedChildEntity<Track, Long> {
    @ChildField
    List<NoteGroupOrTuplet> noteGroupOrTuplets = new ArrayList<>();

    //this method the transactional logic is looking for in order to atomically delete objects
    private void destruct() {
        getOwner().noteTimeTicks.remove(getKey());
    }

    public NoteTimeTick(Track track, Long tick) {
        super(track, tick);
        track.noteTimeTicks.put(tick, this);
    }

    public NoteGroupOrTuplet getNGOT(int idx) {
        return noteGroupOrTuplets.get(idx);
    }
}
