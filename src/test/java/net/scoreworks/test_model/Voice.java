package net.scoreworks.test_model;


import net.scoreworks.collection.DiscontinuousRangeMap;
import net.scoreworks.treetools.Child;

public class Voice extends Child<Track> {
    DiscontinuousRangeMap<Voice, Long, Beam> beams = new DiscontinuousRangeMap<>();
    int voiceId;

    //this constructor the transactional logic is looking for
    private Voice(Track track) {
        super(track);
        track.voices.add(this);
    }
    //this method the transactional logic is looking for in order to atomically delete objects
    protected void removeFromOwner() {
        getOwner().voices.remove(this);
        //remove all that used voice as key
        for (NoteTimeTick ntt : getOwner().noteTimeTicks.values()) {
            ntt.noteGroupOrTuplets.remove(this);
        }
    }

    public Voice(Track track, int voiceId) {
        super(track);
        this.voiceId = voiceId;
        track.voices.add(this);
    }
}
