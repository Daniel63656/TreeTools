package com.immersive.test_model;

import com.immersive.annotations.ChildField;
import com.immersive.annotations.OwnerField;
import com.immersive.annotations.TransactionalEntity;

import java.util.ArrayList;
import java.util.List;

public class Track implements TransactionalEntity<FullScore> {
    @OwnerField
    FullScore fullScore;
    @ChildField
    List<Staff> staffs = new ArrayList<>();
    @ChildField
    List<Voice> voices = new ArrayList<>();
    @ChildField
    List<NoteTimeTick> noteTimeTicks = new ArrayList<>();

    //this constructor the transactional logic is looking for
    public Track(FullScore fullScore) {
        this.fullScore = fullScore;
        fullScore.tracks.add(this);
    }
    //this method the transactional logic is looking for in order to atomically delete objects
    private void destruct() {
        fullScore.tracks.remove(this);
    }

    public Staff getStaff(int idx) {
        return staffs.get(idx);
    }

    public Voice getVoice(int idx) {
        return voices.get(idx);
    }

    public NoteTimeTick getNTT(int idx) {
        return noteTimeTicks.get(idx);
    }


    @Override
    public FullScore getOwner() {
        return fullScore;
    }
}
