package net.scoreworks.treetools;


import net.scoreworks.test_model.*;
import net.scoreworks.treetools.commits.Commit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

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
        Remote.ObjectState lok_tieStart = LOT.getKey(tieStart);
        Remote.ObjectState lok_tieEnd   = LOT.getKey(tieEnd);
        Assertions.assertSame(lok_tieEnd, lok_tieStart.getFields().get(note.getClass().getDeclaredField("nextTied")));
        Assertions.assertSame(null, lok_tieStart.getFields().get(note.getClass().getDeclaredField("previousTied")));
        Assertions.assertSame(lok_tieStart, lok_tieEnd.getFields().get(note.getClass().getDeclaredField("previousTied")));
        Assertions.assertSame(null, lok_tieEnd.getFields().get(note.getClass().getDeclaredField("nextTied")));
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
        Remote remote = repository.remote;
        //test if LOT contains all classes of JOT and nothing more
        Assertions.assertEquals(remote.size(), 13);
        Assertions.assertTrue(remote.containsValue(fullScore));
        Assertions.assertTrue(remote.containsValue(track));
        Assertions.assertTrue(remote.containsValue(staff));
        Assertions.assertTrue(remote.containsValue(voice));
        Assertions.assertTrue(remote.containsValue(note));
        Assertions.assertTrue(remote.containsValue(tieStart));
        Assertions.assertTrue(remote.containsValue(tieEnd));
        verifyTying(remote);

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
        noteGroup.remove();
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
        noteGroup.remove();
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
        newNote2.remove();

        fullScore.commit();
        Commit commit = tm.commits.get(tm.commits.firstKey());
        Assertions.assertEquals(0, commit.getChangeRecords().size());    //no change
        Assertions.assertEquals(2, commit.getCreationRecords().size());  //only two creations

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
        noteGroup.remove();
        fullScore.commit();
        Assertions.assertSame(2, tm.commits.size());
        read.pull();
        Assertions.assertSame(0, tm.commits.size());
        //create new NoteGroup and 2 notes at its place
        NoteGroup newNoteGroup = new NoteGroup(ntt, staff, voice, 16, false);
                       new Note(newNoteGroup, 51, false, NoteName.D);
        Note newNote2= new Note(newNoteGroup, 34, true, NoteName.B);
        fullScore.commit();
        //delete one of the created notes -> this notes creation should not be appearing in commit
        newNote2.remove();

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
        read.pull();

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
        tieStart.remove();
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

    @Test
    public void testDeletionRecordsHoldStateBeforeTheCommit() throws NoSuchFieldException {
        tieStart.setPitch(30);
        tieEnd.getOwner().stemUp = false;
        tieEnd.remove();
        Remote.ObjectState tieStartBeforeChange = repository.remote.getKey(tieStart);
        Remote.ObjectState tieEndNoteGroupBeforeChange = repository.remote.getKey(tieEnd.getOwner());

        Commit commit = fullScore.commit();

        //check that the deletion records "previous tied" and "owner" points at the unchanged states
        for (Map.Entry<Remote.ObjectState, Object[]> entry : commit.getDeletionRecords().entrySet()) { //only one deletionRecord
            Assertions.assertEquals(tieStartBeforeChange, entry.getKey().getFields().get(Note.class.getDeclaredField("previousTied")));
            Assertions.assertEquals(tieEndNoteGroupBeforeChange, entry.getValue()[0]);
        }

        read.pull();
    }

    @Test
    public void testDeletionRecordsAreCreatedForSubsequentChildren() {
        new Note(note.getOwner(), 40, false, NoteName.A); //this creation gets erased by the deletion
        note.getOwner().getOwner().remove();
        Commit commit = fullScore.commit();

        Assertions.assertEquals(3, commit.getDeletionRecords().size());
    }

    @Test
    public void testCleanUpAndPullDeletion() {
        note.getOwner().remove();
        voice.remove(); //this causes removal of all NoteGroups

        fullScore.commit();
        read.pull();
    }

    @Test
    public void testKeyMigration() {
        Voice newVoice = new Voice(track, 1);
        NoteGroup noteGroup = note.getOwner();
        noteGroup.migrate(newVoice);
        Assertions.assertTrue(repository.locallyChangedContains(noteGroup));
        fullScore.commit();
        read.pull();
    }
    
    
}