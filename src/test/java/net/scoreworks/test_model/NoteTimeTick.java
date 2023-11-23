package net.scoreworks.test_model;


import net.scoreworks.treetools.MappedChild;


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

    public NoteTimeTick(Track track, Fraction tick) {
        super(track, tick);
        addToOwner();
    }

    public NoteGroupOrTuplet getNGOT(Voice voice) {
        return noteGroupOrTuplets.get(voice);
    }
}
