package net.scoreworks.test_model;


import net.scoreworks.treetools.RootEntity;
import net.scoreworks.treetools.annotations.TransactionalConstructor;

import java.util.ArrayList;
import java.util.List;

public class FullScore extends RootEntity {
    List<Track> tracks = new ArrayList<>();


    public String name;
    public Foo foo;

    public Track getTrack(int idx) {
        return tracks.get(idx);
    }

    @TransactionalConstructor
    public FullScore() {
        foo = new Foo(new Fo(4, 2), 4, 30);
    }
}
