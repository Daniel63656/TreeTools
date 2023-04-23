package com.immersive.transactions;


import com.immersive.test_model.*;
import com.immersive.transactions.Remote.ObjectState;


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TransactionTests {
    TransactionManager tm = TransactionManager.getInstance();
    Repository repository;
    FullScore fullScore, read;
    Track track;
    Voice voice;
    Staff staff;
    Note note, tieStart, tieEnd;

    @BeforeEach
    public void prepareFullScores() {
        tm.setVerbose(true);
        createFullScoreWithTransactionsEnabled();
        read = (FullScore) tm.clone(fullScore);
    }
    @AfterEach
    public void cleanUp(){
        tm.shutdown();
    }

    private void createFullScoreWithTransactionsEnabled() {
        fullScore = new FullScore();
        fullScore.name = "unique field value";
        track = new Track(fullScore);
        staff = new Staff(track, true);
        voice = new Voice(track, 0);

        NoteTimeTick ntt = new NoteTimeTick(track, Fraction.ZERO);
        NoteGroup noteGroup = new NoteGroup(ntt, staff, voice, 8, true);
        note = new Note(noteGroup, 69, false, NoteName.A);

        ntt = new NoteTimeTick(track, Fraction.getFraction(8, 1));
        noteGroup = new NoteGroup(ntt, staff, voice, 8, true);
        tieStart = new Note(noteGroup, 69, false, NoteName.A);

        ntt = new NoteTimeTick(track, Fraction.getFraction(16, 1));
        noteGroup = new NoteGroup(ntt, staff, voice, 8, true);
        tieEnd = new Note(noteGroup, 69, false, NoteName.B);
        tieStart.tieWith(tieEnd);

        tm.enableTransactionsForRootEntity(fullScore);
        repository = tm.repositories.get(fullScore);
    }

    private void verifyTying(Remote LOT) throws NoSuchFieldException {
        ObjectState lok_tieStart = LOT.getKey(tieStart);
        ObjectState lok_tieEnd   = LOT.getKey(tieEnd);
        Assertions.assertSame(lok_tieEnd, lok_tieStart.crossReferences.get(note.getClass().getDeclaredField("nextTied")));
        Assertions.assertSame(null, lok_tieStart.crossReferences.get(note.getClass().getDeclaredField("previousTied")));
        Assertions.assertSame(lok_tieStart, lok_tieEnd.crossReferences.get(note.getClass().getDeclaredField("previousTied")));
        Assertions.assertSame(null, lok_tieEnd.crossReferences.get(note.getClass().getDeclaredField("nextTied")));
    }

    private Note getNoteInFullScoreAt(FullScore fullScore, Fraction fraction) {
        Voice voice = fullScore.getTrack(0).getVoice(0);
        return ((NoteGroup) fullScore.getTrack(0).getNTT(fraction).getNGOT(voice)).getNote(0);
    }

    private void verifyTying(FullScore fullScore) {
        Note tieStart = getNoteInFullScoreAt(fullScore, Fraction.getFraction(8, 1));
        Note tieEnd   = getNoteInFullScoreAt(fullScore, Fraction.getFraction(16, 1));

        Assertions.assertEquals(tieEnd, tieStart.getNextTied());
        Assertions.assertEquals(tieStart, tieEnd.getPreviousTied());
    }

    @Test
    public void testLOTCreation() throws NoSuchFieldException {
        Remote LOT = repository.remote;
        //test if LOT contains all classes of JOT and nothing more
        Assertions.assertEquals(LOT.size(), 13);
        Assertions.assertTrue(LOT.containsValue(fullScore));
        Assertions.assertTrue(LOT.containsValue(track));
        Assertions.assertTrue(LOT.containsValue(staff));
        Assertions.assertTrue(LOT.containsValue(voice));
        Assertions.assertTrue(LOT.containsValue(note));
        Assertions.assertTrue(LOT.containsValue(tieStart));
        Assertions.assertTrue(LOT.containsValue(tieEnd));
        verifyTying(LOT);

        //test if fields of RootEntity were copied as well
        Assertions.assertSame("unique field value", read.name);
    }

    @Test
    public void testPullingAChange() {
        note.setPitch(30);
        note.setAccidental(true);
        Assertions.assertTrue(repository.locallyChangedContains(note));
        fullScore.commit();
        //get note of read workcopy
        Note note = ((NoteGroup) read.getTrack(0).getNTT(Fraction.ZERO).getNGOT(read.getTrack(0).getVoice(0))).getNote(0);
        //check note is untouched before pull
        Assertions.assertSame(69, note.getPitch());
        Assertions.assertSame(false, note.getAccidental());
        //now pull and check if changed made it into the object
        System.out.println("pulling...");
        read.pull();
        Assertions.assertSame(note.getPitch(), 30);
        Assertions.assertSame(note.getAccidental(), true);
    }

    @Test
    public void testPullingAChangeAndSubsequentCreation() {
        NoteGroup ng = note.getOwner();
        ng.stemUp = false;
        Note newNote = new Note(ng, 80, false, NoteName.A);
        Assertions.assertTrue(repository.locallyChangedContains(ng));
        Assertions.assertTrue(repository.locallyCreatedContains(newNote));
        fullScore.commit();
        System.out.println("pulling...");
        read.pull();
    }

    @Test
    public void testPullingCreations() {
        //create new NoteGroup and 2 notes at its place
        NoteGroup newNoteGroup = new NoteGroup(track.getNTT(Fraction.ZERO), staff, voice, 4, false);
        Note nn1 = new Note(newNoteGroup, 51, false, NoteName.D);
        Note nn2 = new Note(newNoteGroup, 34, true, NoteName.B);
        Assertions.assertTrue(repository.locallyCreatedContains(newNoteGroup));
        Assertions.assertTrue(repository.locallyCreatedContains(nn1));
        Assertions.assertTrue(repository.locallyCreatedContains(nn2));

        fullScore.commit();
        read.pull();
    }

    @Test
    public void testPullingDeletions() {
        NoteGroup noteGroup = ((NoteGroup) ((FullScore) repository.rootEntity).getTrack(0).getNTT(Fraction.ZERO).getNGOT(voice));
        Note note = noteGroup.getNote(0);
        noteGroup.clear();
        Assertions.assertTrue(repository.locallyDeletedContains(noteGroup));
        Assertions.assertTrue(repository.locallyDeletedContains(note));

        fullScore.commit();
        read.pull();
    }

    @Test
    public void testDeletionOverridesChangedOrCreatedObjectsInCommitAndPullAfterwards() {
        NoteTimeTick ntt = track.getNTT(Fraction.ZERO);
        NoteGroup noteGroup = ((NoteGroup)track.getNTT(Fraction.ZERO).getNGOT(voice));
        Note note = noteGroup.getNote(0);
        //change a note
        note.setPitch(30);
        note.setAccidental(true);
        Assertions.assertTrue(repository.locallyChangedContains(note)); //aspects automatically listed changed object
        //delete same note and its owner -> change should not be appearing in commit but its gets in local deletion list at first
        noteGroup.clear();
        Assertions.assertTrue(repository.locallyDeletedContains(noteGroup));     //aspects automatically listed deleted noteGroup
        Assertions.assertTrue(repository.locallyDeletedContains(note));          //and its child due to how clear must work
        //create new NoteGroup and 2 notes at its place
        NoteGroup newNoteGroup = new NoteGroup(ntt, staff, voice, 16, false);
        Note newNote = new Note(newNoteGroup, 51, false, NoteName.D);
        Note newNote2= new Note(newNoteGroup, 34, true, NoteName.B);
        Assertions.assertTrue(repository.locallyCreatedContains(newNoteGroup));
        Assertions.assertTrue(repository.locallyCreatedContains(newNote));
        Assertions.assertTrue(repository.locallyCreatedContains(newNote2));
        //delete one of the created notes -> this notes creation should not be appearing in commit
        newNote2.clear();

        fullScore.commit();
        Commit commit = tm.commits.get(tm.commits.firstKey());
        Assertions.assertEquals(0, commit.changeRecords.size());    //no change
        Assertions.assertEquals(2, commit.creationRecords.size());  //only two creations

        read.pull();
    }

    @Test
    public void testPullingSeveralCommitsAtOnceAndCleanup() {
        NoteTimeTick ntt = track.getNTT(Fraction.ZERO);
        NoteGroup noteGroup = ((NoteGroup)track.getNTT(Fraction.ZERO).getNGOT(voice));
        Note note = noteGroup.getNote(0);
        //change a note
        note.setPitch(30);
        note.setAccidental(true);
        fullScore.commit();
        //now delete same note and its owner
        noteGroup.clear();
        fullScore.commit();
        Assertions.assertSame(2, tm.commits.size());
        System.out.println("pulling...");
        read.pull();
        Assertions.assertSame(0, tm.commits.size());
        //create new NoteGroup and 2 notes at its place
        NoteGroup newNoteGroup = new NoteGroup(ntt, staff, voice, 16, false);
                       new Note(newNoteGroup, 51, false, NoteName.D);
        Note newNote2= new Note(newNoteGroup, 34, true, NoteName.B);
        fullScore.commit();
        //delete one of the created notes -> this notes creation should not be appearing in commit
        newNote2.clear();

        fullScore.commit();
        read.pull();
        //cleaned up all commits because only workcopy (read) is at last commit
        Assertions.assertSame(0, tm.commits.size());
    }


    //==========test cross-references==========

    @Test
    public void testOneWayCrossReferenceChangeGetsLogged() {
        NoteGroup group = note.getOwner();
        group.setStaff(null);
        repository.locallyChangedContains(note.getOwner());

        fullScore.commit();
        read.pull();
        Assertions.assertNull(getNoteInFullScoreAt(read, Fraction.ZERO).getOwner().getStaff());
    }

    @Test
    public void testTiedNotesChangingOneAtATime() throws NoSuchFieldException {
        tieStart.setPitch(30);
        Assertions.assertTrue(repository.locallyChangedContains(tieStart));
        verifyTying(repository.remote);
        fullScore.commit();

        tieEnd.setPitch(30);
        fullScore.commit();

        read.pull();
        verifyTying(read);
    }

    @Test
    public void testTiedNotesChangingAtOnce() {
        tieStart.setPitch(30);
        tieEnd.setPitch(30);
        Assertions.assertTrue(repository.locallyChangedContains(tieStart));
        Assertions.assertTrue(repository.locallyChangedContains(tieEnd));

        fullScore.commit();
        read.pull();
        verifyTying(read);
    }

    @Test
    public void testTiedNoteGetsDeleted() {
        //this causes to untie and therefore changes tieEnd
        tieStart.clear();
        Assertions.assertTrue(repository.locallyChangedContains(tieEnd));
        //but tieStart itself should not appear in the local changes despite the untying, because it gets deleted
        Assertions.assertFalse(repository.locallyChangedContains(tieStart));
        Assertions.assertTrue(repository.locallyDeletedContains(tieStart));

        fullScore.commit();
        read.pull();
        Assertions.assertNull(getNoteInFullScoreAt(fullScore, Fraction.getFraction(16, 1)).getPreviousTied());
    }

    @Test
    public void testTieExpandsToNewNote() {
        tieEnd.setPitch(30);
        NoteTimeTick ntt = new NoteTimeTick(track, Fraction.getFraction(24, 1));
        NoteGroup noteGroup = new NoteGroup(ntt, staff, voice, 8, true);
        Note newTied = new Note(noteGroup, 69, false, NoteName.A);
        tieEnd.tieWith(newTied);

        fullScore.commit();
        read.pull();

        Note oldTieEndInRead = getNoteInFullScoreAt(read, Fraction.getFraction(16, 1));
        Note newTiedInRead = getNoteInFullScoreAt(read, Fraction.getFraction(24, 1));
        Assertions.assertEquals(oldTieEndInRead, newTiedInRead.getPreviousTied());
        Assertions.assertEquals(newTiedInRead, oldTieEndInRead.getNextTied());
    }

    @Test
    public void testCrossReferenceChange() {
        NoteTimeTick ntt = new NoteTimeTick(track, Fraction.getFraction(24, 1));
        NoteGroup noteGroup = new NoteGroup(ntt, staff, voice, 8, true);
        Note newTied = new Note(noteGroup, 69, false, NoteName.A);
        tieStart.tieWith(newTied);
        Assertions.assertTrue(repository.locallyCreatedContains(newTied));
        Assertions.assertTrue(repository.locallyChangedContains(tieStart));
        Assertions.assertFalse(repository.locallyChangedContains(tieEnd));

        fullScore.commit();
        read.pull();

        Note tieStart = getNoteInFullScoreAt(read, Fraction.getFraction(8, 1));
        Note newTiedInRead = getNoteInFullScoreAt(read, Fraction.getFraction(24, 1));
        Assertions.assertEquals(tieStart, newTiedInRead.getPreviousTied());
        Assertions.assertEquals(newTiedInRead, tieStart.getNextTied());
    }

    //==========test cross-references combined with undos==========
    @Test
    public void testDeletionRecordsHoldStateBeforeTheCommit() throws NoSuchFieldException {
        tieStart.setPitch(30);
        tieEnd.clear();
        ObjectState tieStartBeforeChange = repository.remote.getKey(tieStart);
        Commit commit = fullScore.commit();

        //check that the deletion records "previous tied" points at the unchanged state
        for (ObjectState deletion : commit.deletionRecords.keySet()) {
            Assertions.assertEquals(tieStartBeforeChange, deletion.crossReferences.get(Note.class.getDeclaredField("previousTied")));
        }
    }

    @Test
    public void testDeletionRecordsHoldStateBeforeTheCommitTracingCrossReferences() throws NoSuchFieldException {
        tieStart.setPitch(30);
        ObjectState tieStartBeforeChange = repository.remote.getKey(tieStart);
        fullScore.commit();
        ObjectState tieStartAfterChange = repository.remote.getKey(tieStart);

        //at this point the cross-referenced state of tie start is no longer in the remote because tie start changed
        Assertions.assertEquals(tieStartBeforeChange, repository.remote.getKey(tieEnd).crossReferences.get(Note.class.getDeclaredField("previousTied")));

        //now remove tieEnd and check if cross-referenced state was updated
        tieEnd.clear();
        Commit commit = fullScore.commit();

        //check that the deletion records "previous tied" points at the unchanged state
        for (ObjectState deletion : commit.deletionRecords.keySet()) {
            Assertions.assertEquals(tieStartAfterChange, deletion.crossReferences.get(Note.class.getDeclaredField("previousTied")));
        }
    }
    
    
}