package com.immersive.test_model;

import com.immersive.annotations.ChildField;
import com.immersive.abstractions.RootEntity;

import java.util.ArrayList;
import java.util.List;

public class FullScore extends RootEntity {
    @ChildField
    List<Track> tracks = new ArrayList<>();

    public Track getTrack(int idx) {
        return tracks.get(idx);
    }

    //this constructor the transactional logic is looking for
    public FullScore() {}
}
