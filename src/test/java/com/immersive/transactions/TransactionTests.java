package com.immersive.transactions;


import com.immersive.test_model.*;
import com.immersive.transactions.LogicalObjectTree.LogicalObjectKey;


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TransactionTests {
    TransactionManager tm = TransactionManager.getInstance();
    Track track;
    Voice voice;
    Staff staff;
    Note note, tieStart, tieEnd;

    @BeforeEach
    public void logAspects() {
        tm.logAspects(true);
    }
    @AfterEach
    public void cleanUp(){
        tm.shutdown();
    }

    private void verifyTying(LogicalObjectTree LOT) throws NoSuchFieldException {
        LogicalObjectKey lok_tieStart = LOT.getKey(tieStart);
        LogicalObjectKey lok_tieEnd   = LOT.getKey(tieEnd);
        Assertions.assertSame(lok_tieEnd, lok_tieStart.get(note.getClass().getDeclaredField("nextTied")));
        Assertions.assertSame(null, lok_tieStart.get(note.getClass().getDeclaredField("previousTied")));
        Assertions.assertSame(lok_tieStart, lok_tieEnd.get(note.getClass().getDeclaredField("previousTied")));
        Assertions.assertSame(null, lok_tieEnd.get(note.getClass().getDeclaredField("nextTied")));
    }

    private Workcopy createTransactionWorkcopy() {
        FullScore fullScore = new FullScore();
        track = new Track(fullScore);
        staff = new Staff(track, true);
        voice = new Voice(track, 0);
        Workcopy w = tm.workcopies.get(fullScore);

        NoteTimeTick ntt = new NoteTimeTick(track, Fraction.ZERO);
        NoteGroup noteGroup = new NoteGroup(ntt, staff, voice, 8, true);
        note = new Note(noteGroup, 69, false, NoteName.A);

        ntt = new NoteTimeTick(track, Fraction.getFraction(8, 1));
        noteGroup = new NoteGroup(ntt, staff, voice, 8, true);
        tieStart = new Note(noteGroup, 69, false, NoteName.A);

        ntt = new NoteTimeTick(track, Fraction.getFraction(16, 1));
        noteGroup = new NoteGroup(ntt, staff, voice, 8, true);
        tieEnd = new Note(noteGroup, 69, false, NoteName.A);
        tieStart.tieWith(tieEnd);

        tm.enableTransactionsForRootEntity(fullScore);
        return tm.workcopies.get(fullScore);
    }

    @Test
    public void testLOTCreation() throws NoSuchFieldException {
        Workcopy workcopy = createTransactionWorkcopy();
        tm.enableTransactionsForRootEntity(workcopy.rootEntity);
        LogicalObjectTree LOT = tm.workcopies.get(workcopy.rootEntity).LOT;
        //test if LOT contains all classes of JOT and nothing more
        Assertions.assertEquals(LOT.size(), 13);
        Assertions.assertTrue(LOT.containsValue(workcopy.rootEntity));
        Assertions.assertTrue(LOT.containsValue(track));
        Assertions.assertTrue(LOT.containsValue(staff));
        Assertions.assertTrue(LOT.containsValue(voice));
        Assertions.assertTrue(LOT.containsValue(note));
        Assertions.assertTrue(LOT.containsValue(tieStart));
        Assertions.assertTrue(LOT.containsValue(tieEnd));
        verifyTying(LOT);
    }

    @Test
    public void testDataModelDeepCopy() {
        Workcopy workcopy = createTransactionWorkcopy();
        ((FullScore) workcopy.rootEntity).name = "unique field";
        tm.enableTransactionsForRootEntity(workcopy.rootEntity);
        FullScore copy = (FullScore) tm.getWorkcopyOf(workcopy.rootEntity);
        Assertions.assertSame("unique field", copy.name);
    }

    @Test
    public void testPullingAChange() {
        Workcopy workcopy = createTransactionWorkcopy();
        FullScore read = (FullScore) tm.getWorkcopyOf(workcopy.rootEntity);
        note.setPitch(30);
        note.setAccidental(true);
        Assertions.assertTrue(workcopy.locallyChangedOrCreated.contains(note));
        tm.commit(workcopy.rootEntity);
        //get note of read workcopy
        Note note = ((NoteGroup) read.getTrack(0).getNTT(Fraction.ZERO).getNGOT(read.getTrack(0).getVoice(0))).getNote(0);
        //check note is untouched before pull
        Assertions.assertSame(note.getPitch(), 69);
        Assertions.assertSame(note.getAccidental(), false);
        //now pull and check if changed made it into the object
        System.out.println("pulling...");
        tm.pull(read);
        Assertions.assertSame(note.getPitch(), 30);
        Assertions.assertSame(note.getAccidental(), true);
    }

    @Test
    public void testPullingAChangeAndSubsequentCreation() {
        Workcopy workcopy = createTransactionWorkcopy();
        FullScore read = (FullScore) tm.getWorkcopyOf(workcopy.rootEntity);
        NoteGroup ng = note.getOwner();
        ng.stemUp = false;
        Note newNote = new Note(ng, 80, false, NoteName.A);
        Assertions.assertTrue(workcopy.locallyChangedOrCreated.contains(ng));
        Assertions.assertTrue(workcopy.locallyChangedOrCreated.contains(newNote));
        tm.commit(workcopy.rootEntity);
        System.out.println("pulling...");
        tm.pull(read);
    }

    @Test
    public void testPullingCreations() {
        Workcopy workcopy = createTransactionWorkcopy();
        FullScore read = (FullScore) tm.getWorkcopyOf(workcopy.rootEntity);
        //create new NoteGroup and 2 notes at its place
        NoteGroup newNoteGroup = new NoteGroup(track.getNTT(Fraction.ZERO), staff, voice, 4, false);
        Note nn1 = new Note(newNoteGroup, 51, false, NoteName.D);
        Note nn2 = new Note(newNoteGroup, 34, true, NoteName.B);
        Assertions.assertTrue(workcopy.locallyChangedOrCreated.contains(newNoteGroup));
        Assertions.assertTrue(workcopy.locallyChangedOrCreated.contains(nn1));
        Assertions.assertTrue(workcopy.locallyChangedOrCreated.contains(nn2));
        Assertions.assertSame(workcopy.locallyChangedOrCreated.size(), 3);
        tm.commit(workcopy.rootEntity);
        System.out.println("pulling...");
        tm.pull(read);
    }

    @Test
    public void testPullingDeletions() {
        Workcopy workcopy = createTransactionWorkcopy();
        FullScore read = (FullScore) tm.getWorkcopyOf(workcopy.rootEntity);
        NoteGroup noteGroup = ((NoteGroup) ((FullScore)workcopy.rootEntity).getTrack(0).getNTT(Fraction.ZERO).getNGOT(voice));
        Note note = noteGroup.getNote(0);
        noteGroup.clear();
        Assertions.assertTrue(workcopy.locallyDeleted.contains(noteGroup));
        Assertions.assertTrue(workcopy.locallyDeleted.contains(note));
        tm.commit(workcopy.rootEntity);
        System.out.println("pulling...");
        tm.pull(read);
    }

    @Test
    public void testDeletionOverridesChangedOrCreatedObjectsInCommitAlsoPull() {
        Workcopy workcopy = createTransactionWorkcopy();
        FullScore read = (FullScore) tm.getWorkcopyOf(workcopy.rootEntity);
        NoteTimeTick ntt = track.getNTT(Fraction.ZERO);
        NoteGroup noteGroup = ((NoteGroup)track.getNTT(Fraction.ZERO).getNGOT(voice));
        Note note = noteGroup.getNote(0);
        //change a note
        note.setPitch(30);
        note.setAccidental(true);
        Assertions.assertTrue(workcopy.locallyChangedOrCreated.contains(note)); //aspects automatically listed changed object
        //delete same note and its owner -> change should not be appearing in commit but its gets in local deletion list at first
        noteGroup.clear();
        Assertions.assertTrue(workcopy.locallyDeleted.contains(noteGroup));     //aspects automatically listed deleted noteGroup
        Assertions.assertTrue(workcopy.locallyDeleted.contains(note));          //and its child due to how clear must work
        //create new NoteGroup and 2 notes at its place
        NoteGroup newNoteGroup = new NoteGroup(ntt, staff, voice, 16, false);
        Note newNote = new Note(newNoteGroup, 51, false, NoteName.D);
        Note newNote2= new Note(newNoteGroup, 34, true, NoteName.B);
        Assertions.assertTrue(workcopy.locallyChangedOrCreated.contains(newNoteGroup));
        Assertions.assertTrue(workcopy.locallyChangedOrCreated.contains(newNote));
        Assertions.assertTrue(workcopy.locallyChangedOrCreated.contains(newNote2));
        Assertions.assertSame(5, workcopy.locallyChangedOrCreated.size());
        //delete one of the created notes -> this notes creation should not be appearing in commit
        newNote2.clear();

        tm.commit(workcopy.rootEntity);
        Commit commit = tm.commits.get(tm.commits.firstKey());
        Assertions.assertEquals(0, commit.changeRecords.size());    //no change
        Assertions.assertEquals(2, commit.creationRecords.size());  //only two creations

        System.out.println("pulling...");
        tm.pull(read);
    }

    @Test
    public void testPullingSeveralCommitsAtOnceAndCleanup() {
        Workcopy workcopy = createTransactionWorkcopy();
        FullScore read = (FullScore) tm.getWorkcopyOf(workcopy.rootEntity);
        NoteTimeTick ntt = track.getNTT(Fraction.ZERO);
        NoteGroup noteGroup = ((NoteGroup)track.getNTT(Fraction.ZERO).getNGOT(voice));
        Note note = noteGroup.getNote(0);
        //change a note
        note.setPitch(30);
        note.setAccidental(true);
        tm.commit(workcopy.rootEntity);
        //now delete same note and its owner
        noteGroup.clear();
        tm.commit(workcopy.rootEntity);
        Assertions.assertSame(2, tm.commits.size());
        System.out.println("pulling...");
        tm.pull(read);
        Assertions.assertSame(0, tm.commits.size());
        //create new NoteGroup and 2 notes at its place
        NoteGroup newNoteGroup = new NoteGroup(ntt, staff, voice, 16, false);
                       new Note(newNoteGroup, 51, false, NoteName.D);
        Note newNote2= new Note(newNoteGroup, 34, true, NoteName.B);
        tm.commit(workcopy.rootEntity);
        //delete one of the created notes -> this notes creation should not be appearing in commit
        newNote2.clear();
        tm.commit(workcopy.rootEntity);

        System.out.println("pulling...");
        tm.pull(read);
        Assertions.assertSame(0, tm.commits.size());
    }
}