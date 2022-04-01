package com.immersive.core;

import com.immersive.test_model.FullScore;
import com.immersive.test_model.Note;
import com.immersive.test_model.NoteGroup;
import com.immersive.test_model.NoteName;
import com.immersive.test_model.NoteTimeTick;
import com.immersive.test_model.Staff;
import com.immersive.test_model.Track;
import com.immersive.test_model.Voice;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TransactionTests {
    TransactionManager tm = TransactionManager.getInstance();
    Track track;
    Voice voice;
    Staff staff;
    Note note, tieStart, tieEnd;

    @AfterEach
    public void cleanUp(){
        tm.shutdown();
    }

    private Workcopy createTransactionWorkcopy() {
        FullScore fullScore = new FullScore();
        track = new Track(fullScore);
        staff = new Staff(track, true);
        voice = new Voice(track, 0);

        NoteTimeTick ntt = new NoteTimeTick(track, 0L);
        NoteGroup noteGroup = new NoteGroup(ntt, staff, voice, 8, true);
        note = new Note(noteGroup, 69, false, NoteName.A);

        ntt = new NoteTimeTick(track, 8L);
        noteGroup = new NoteGroup(ntt, staff, voice, 8, true);
        tieStart = new Note(noteGroup, 69, false, NoteName.A);

        ntt = new NoteTimeTick(track, 16L);
        noteGroup = new NoteGroup(ntt, staff, voice, 8, true);
        tieEnd = new Note(noteGroup, 69, false, NoteName.A);
        tieStart.tieWith(tieEnd);

        tm.enableTransactionsForRootEntity(fullScore);
        return tm.workcopies.get(fullScore);
    }

    @Test
    public void testLOTCreation() {
        FullScore fullScore = new FullScore();
        Track track = new Track(fullScore);
        Staff staff = new Staff(track, true);
        Voice voice = new Voice(track, 0);
        NoteTimeTick ntt = new NoteTimeTick(track, 0L);
        NoteGroup noteGroup = new NoteGroup(ntt, staff, voice, 8, true);
        Note note = new Note(noteGroup, 69, false, NoteName.A);

        tm.enableTransactionsForRootEntity(fullScore);
        LogicalObjectTree LOT = tm.workcopies.get(fullScore).LOT;
        //test if LOT contains all classes of JOT and nothing more
        Assertions.assertTrue(LOT.containsValue(fullScore));
        Assertions.assertTrue(LOT.containsValue(track));
        Assertions.assertTrue(LOT.containsValue(staff));
        Assertions.assertTrue(LOT.containsValue(voice));
        Assertions.assertTrue(LOT.containsValue(ntt));
        Assertions.assertTrue(LOT.containsValue(noteGroup));
        Assertions.assertTrue(LOT.containsValue(note));
        Assertions.assertEquals(LOT.size(), 7);
    }

    @Test
    public void testPullingAChange(){
        Workcopy workcopy = createTransactionWorkcopy();
        FullScore read = (FullScore) tm.getWorkcopyOf(workcopy.rootEntity);
        note.setPitch(30);
        note.setAccidental(true);
        Assertions.assertTrue(workcopy.locallyChangedOrCreated.contains(note));
        tm.commit(workcopy.rootEntity);
        //get note of read workcopy
        Note note = ((NoteGroup) read.getTrack(0).getNTT(0).getNGOT(read.getTrack(0).getVoice(0))).getNote(0);
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
    public void testPullingCreations() {
        Workcopy workcopy = createTransactionWorkcopy();
        FullScore read = (FullScore) tm.getWorkcopyOf(workcopy.rootEntity);
        //create new NoteGroup and 2 notes at its place
        NoteGroup newNoteGroup = new NoteGroup(track.getNTT(0), staff, voice, 4, false);
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
        NoteGroup noteGroup = ((NoteGroup) ((FullScore)workcopy.rootEntity).getTrack(0).getNTT(0).getNGOT(voice));
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
        NoteTimeTick ntt = track.getNTT(0);
        NoteGroup noteGroup = ((NoteGroup)track.getNTT(0).getNGOT(voice));
        Note note = noteGroup.getNote(0);
        //change a note
        note.setPitch(30);
        note.setAccidental(true);
        Assertions.assertTrue(workcopy.locallyChangedOrCreated.contains(note)); //aspects automatically listed changed object
        //delete same note and its owner -> change should not be appear in commit but its gets in local deletion list at first
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
        Assertions.assertSame(workcopy.locallyChangedOrCreated.size(), 4);
        //delete one of the created notes -> this notes creation should not be appear in commit
        newNote2.clear();

        tm.commit(workcopy.rootEntity);
        Commit commit = tm.commits.get(tm.commits.firstKey());
        Assertions.assertEquals(commit.changeRecords.size(), 0);    //no change
        Assertions.assertEquals(commit.creationRecords.size(), 2);  //only two creations

        System.out.println("pulling...");
        tm.pull(read);
    }

    @Test
    public void testPullingSeveralCommitsAtOnce() {
        Workcopy workcopy = createTransactionWorkcopy();
        FullScore read = (FullScore) tm.getWorkcopyOf(workcopy.rootEntity);
        NoteTimeTick ntt = track.getNTT(0);
        NoteGroup noteGroup = ((NoteGroup)track.getNTT(0).getNGOT(voice));
        Note note = noteGroup.getNote(0);
        //change a note
        note.setPitch(30);
        note.setAccidental(true);
        tm.commit(workcopy.rootEntity);
        //now delete same note and its owner
        noteGroup.clear();
        tm.commit(workcopy.rootEntity);
        //create new NoteGroup and 2 notes at its place
        NoteGroup newNoteGroup = new NoteGroup(ntt, staff, voice, 16, false);
        Note newNote = new Note(newNoteGroup, 51, false, NoteName.D);
        Note newNote2= new Note(newNoteGroup, 34, true, NoteName.B);
        tm.commit(workcopy.rootEntity);
        //delete one of the created notes -> this notes creation should not be appear in commit
        newNote2.clear();
        tm.commit(workcopy.rootEntity);

        Assertions.assertSame(tm.commits.size(), 4);
        System.out.println("pulling...");
        tm.pull(read);
    }
}