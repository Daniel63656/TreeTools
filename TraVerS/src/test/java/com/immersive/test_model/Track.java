package com.immersive.test_model;


import com.immersive.transactions.ChildEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public class Track extends ChildEntity<FullScore> {
    List<Staff> staffs = new ArrayList<>();
    List<Voice> voices = new ArrayList<>();
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
    public boolean clear() {
        return super.clear();
    }
}
