package net.scoreworks.test_model;


import net.scoreworks.treetools.Child;


import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public class Track extends Child<FullScore> {
    List<Staff> staffs = new ArrayList<>();
    List<Voice> voices = new ArrayList<>();
    TreeMap<Fraction, NoteTimeTick> noteTimeTicks = new TreeMap<>();

    //this constructor the transactional logic is looking for
    public Track(FullScore fullScore) {
        super(fullScore);
        fullScore.tracks.add(this);
    }
    //this method the transactional logic is looking for in order to atomically delete objects
    protected void removeFromOwner() {
        getOwner().tracks.remove(this);
    }

    public Staff getStaff(int idx) {
        return staffs.get(idx);
    }

    public Voice getVoice(int idx) {
        return voices.get(idx);
    }

    public NoteTimeTick getNTT(Fraction idx) {
        return noteTimeTicks.get(idx);
    }
}
