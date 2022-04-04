package com.immersive.test_model;

import com.immersive.abstractions.ChildEntity;

public class Voice extends ChildEntity<Track> {
    int voiceId;

    //this constructor the transactional logic is looking for
    private Voice(Track track) {
        super(track);
        track.voices.add(this);
    }
    //this method the transactional logic is looking for in order to atomically delete objects
    private void destruct() {
        getOwner().voices.remove(this);
    }

    public Voice(Track track, int voiceId) {
        super(track);
        this.voiceId = voiceId;
        track.voices.add(this);
    }

    @Override
    public void clear() {

    }
}
