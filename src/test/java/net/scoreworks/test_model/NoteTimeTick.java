package net.scoreworks.test_model;


import net.scoreworks.treetools.MappedChild;
import net.scoreworks.treetools.annotations.TransactionalConstructor;


import java.util.HashMap;
import java.util.Map;

public class NoteTimeTick extends MappedChild<Track, Fraction> {
    Map<Voice, NoteGroupOrTuplet> noteGroupOrTuplets = new HashMap<>();

    protected void removeFromOwner() {
        getOwner().noteTimeTicks.remove(getKey());
    }
    protected void addToOwner() {
        getOwner().noteTimeTicks.put(getKey(), this);
    }

    @TransactionalConstructor
    public NoteTimeTick(Track track, Fraction tick) {
        super(track, tick);
    }

    public NoteGroupOrTuplet getNGOT(Voice voice) {
        return noteGroupOrTuplets.get(voice);
    }
}
