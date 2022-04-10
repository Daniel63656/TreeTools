package com.immersive.test_model;

import com.immersive.annotations.ChildField;
import com.immersive.transactions.ChildEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public class Track extends ChildEntity<FullScore> {
    @ChildField
    List<Staff> staffs = new ArrayList<>();
    @ChildField
    List<Voice> voices = new ArrayList<>();
    @ChildField
    TreeMap<Long, NoteTimeTick> noteTimeTicks = new TreeMap<>();

    //this constructor the transactional logic is looking for
    public Track(FullScore fullScore) {
        super(fullScore);
        fullScore.tracks.add(this);
    }
    //this method the transactional logic is looking for in order to atomically delete objects
    private void destruct() {
        getOwner().tracks.remove(this);
    }

    public Staff getStaff(int idx) {
        return staffs.get(idx);
    }

    public Voice getVoice(int idx) {
        return voices.get(idx);
    }

    public NoteTimeTick getNTT(long idx) {
        return noteTimeTicks.get(idx);
    }

    @Override
    public void clear() {

    }
}
