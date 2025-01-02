package net.scoreworks.testmodel;


import net.scoreworks.treetools.Child;
import net.scoreworks.treetools.annotations.TransactionalConstructor;


import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public class Track extends Child<FullScore> {
    List<Staff> staffs = new ArrayList<>();
    List<Voice> voices = new ArrayList<>();
    TreeMap<Fraction, NoteTimeTick> noteTimeTicks = new TreeMap<>();

    @TransactionalConstructor
    public Track(FullScore fullScore) {
        super(fullScore);
    }
    //this method the transactional logic is looking for in order to atomically delete objects
    protected void removeFromOwner() {
        getOwner().tracks.remove(this);
    }
    protected void addToOwner() {
        getOwner().tracks.add(this);
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
