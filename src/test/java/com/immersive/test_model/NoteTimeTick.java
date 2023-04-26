package com.immersive.test_model;


import com.immersive.transactions.KeyedChildEntity;


import java.util.HashMap;
import java.util.Map;

public class NoteTimeTick extends KeyedChildEntity<Track, Fraction> {
    Map<Voice, NoteGroupOrTuplet> noteGroupOrTuplets = new HashMap<>();

    //this method the transactional logic is looking for in order to atomically delete objects
    protected void onRemove() {
        getOwner().noteTimeTicks.remove(getKey());
    }

    public NoteTimeTick(Track track, Fraction tick) {
        super(track, tick);
        track.noteTimeTicks.put(tick, this);
    }

    public NoteGroupOrTuplet getNGOT(Voice voice) {
        return noteGroupOrTuplets.get(voice);
    }
}
