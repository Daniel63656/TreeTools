package com.immersive.test_model;

import com.immersive.annotations.OwnerField;
import com.immersive.annotations.TransactionalEntity;

public class Voice implements TransactionalEntity<Track> {
    @OwnerField
    Track track;
    int voiceId;

    //this constructor the transactional logic is looking for
    private Voice(Track track) {
        this.track = track;
        track.voices.add(this);
    }
    //this method the transactional logic is looking for in order to atomically delete objects
    private void destruct() {
        track.voices.remove(this);
    }

    public Voice(Track track, int voiceId) {
        this.track = track;
        this.voiceId = voiceId;
        track.voices.add(this);
    }

    @Override
    public Track getOwner() {
        return track;
    }
}
