package com.immersive.test_model;


import com.immersive.transactions.RootEntity;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FullScore extends RootEntity {
    List<Track> tracks = new ArrayList<>();


    public String name;
    public Foo foo;

    public Track getTrack(int idx) {
        return tracks.get(idx);
    }

    //this constructor the transactional logic is looking for
    public FullScore() {
        foo = new Foo(new Fo(4, 2), 4, 30);
    }
}
