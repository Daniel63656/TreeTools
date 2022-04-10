package com.immersive.test_model;

import com.immersive.annotations.ChildField;
import com.immersive.transactions.KeyedChildEntity;

import java.util.HashMap;
import java.util.Map;

public class NoteTimeTick extends KeyedChildEntity<Track, Long> {
    @ChildField
    Map<Voice, NoteGroupOrTuplet> noteGroupOrTuplets = new HashMap<>();

    //this method the transactional logic is looking for in order to atomically delete objects
    private void destruct() {
        getOwner().noteTimeTicks.remove(getKey());
    }

    public NoteTimeTick(Track track, Long tick) {
        super(track, tick);
        track.noteTimeTicks.put(tick, this);
    }

    public NoteGroupOrTuplet getNGOT(Voice voice) {
        return noteGroupOrTuplets.get(voice);
    }

    @Override
    public void clear() {
        super.clear();
    }
}
