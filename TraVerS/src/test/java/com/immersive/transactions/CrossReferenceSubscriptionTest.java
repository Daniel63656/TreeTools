package com.immersive.transactions;

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

public class CrossReferenceSubscriptionTest {
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

    private void verifyTying(LogicalObjectTree LOT) throws NoSuchFieldException {
        LogicalObjectKey lok_tieStart = LOT.getKey(tieStart);
        LogicalObjectKey lok_tieEnd   = LOT.getKey(tieEnd);
        Assertions.assertSame(lok_tieEnd, (LogicalObjectKey) lok_tieStart.get(note.getClass().getDeclaredField("nextTied")));
        Assertions.assertSame(null, (LogicalObjectKey) lok_tieStart.get(note.getClass().getDeclaredField("previousTied")));
        Assertions.assertSame(lok_tieStart, (LogicalObjectKey) lok_tieEnd.get(note.getClass().getDeclaredField("previousTied")));
        Assertions.assertSame(null, (LogicalObjectKey) lok_tieEnd.get(note.getClass().getDeclaredField("nextTied")));
        //test subscription
        Assertions.assertTrue(lok_tieStart.subscribedLOKs.containsKey(lok_tieEnd));
        Assertions.assertEquals(1, lok_tieStart.subscribedLOKs.size());
        Assertions.assertTrue(lok_tieEnd.subscribedLOKs.containsKey(lok_tieStart));
        Assertions.assertEquals(1, lok_tieEnd.subscribedLOKs.size());
    }

    @Test
    public void testOnlyOneTiedNoteChanged() throws NoSuchFieldException {
        Workcopy workcopy = createTransactionWorkcopy();
        tieStart.setPitch(30);
        Assertions.assertTrue(workcopy.locallyChangedOrCreated.contains(tieStart));
        verifyTying(tm.workcopies.get(workcopy.rootEntity).LOT);
        tm.commit(workcopy.rootEntity);
        verifyTying(tm.workcopies.get(workcopy.rootEntity).LOT);
    }

    @Test
    public void testOnlyBothTiedNoteChanged() throws NoSuchFieldException {
        Workcopy workcopy = createTransactionWorkcopy();
        tieStart.setPitch(30);
        tieEnd.setPitch(30);
        Assertions.assertTrue(workcopy.locallyChangedOrCreated.contains(tieStart));
        Assertions.assertTrue(workcopy.locallyChangedOrCreated.contains(tieEnd));
        verifyTying(tm.workcopies.get(workcopy.rootEntity).LOT);
        tm.commit(workcopy.rootEntity);
        verifyTying(tm.workcopies.get(workcopy.rootEntity).LOT);
    }

    @Test
    public void testTiedNoteDeleted() throws NoSuchFieldException {
        Workcopy workcopy = createTransactionWorkcopy();
        tieStart.clear();
        Assertions.assertTrue(workcopy.locallyChangedOrCreated.contains(tieStart));
        Assertions.assertTrue(workcopy.locallyChangedOrCreated.contains(tieEnd));
        tm.commit(workcopy.rootEntity);

        LogicalObjectTree LOT = tm.workcopies.get(workcopy.rootEntity).LOT;
        Assertions.assertSame(null, LOT.getKey(tieStart));
        LogicalObjectKey lok_tieEnd = LOT.getKey(tieEnd);
        Assertions.assertSame(null, (LogicalObjectKey) lok_tieEnd.get(note.getClass().getDeclaredField("previousTied")));
        Assertions.assertSame(null, (LogicalObjectKey) lok_tieEnd.get(note.getClass().getDeclaredField("nextTied")));
        Assertions.assertEquals(0, lok_tieEnd.subscribedLOKs.size());
    }

    @Test
    public void testTiedNoteCreated() throws NoSuchFieldException {
        Workcopy workcopy = createTransactionWorkcopy();
        tieStart.setPitch(30);
        NoteTimeTick ntt = new NoteTimeTick(track, 24L);
        NoteGroup noteGroup = new NoteGroup(ntt, staff, voice, 8, true);
        Note newTied = new Note(noteGroup, 69, false, NoteName.A);
        tieEnd.tieWith(newTied);
        tm.commit(workcopy.rootEntity);

        LogicalObjectTree LOT = tm.workcopies.get(workcopy.rootEntity).LOT;
        LogicalObjectKey lok_tieStart = LOT.getKey(tieStart);
        LogicalObjectKey lok_tieMiddle = LOT.getKey(tieEnd);
        LogicalObjectKey lok_tieEnd = LOT.getKey(newTied);
        Assertions.assertSame(lok_tieMiddle, (LogicalObjectKey) lok_tieStart.get(note.getClass().getDeclaredField("nextTied")));
        Assertions.assertSame(null, (LogicalObjectKey) lok_tieStart.get(note.getClass().getDeclaredField("previousTied")));
        Assertions.assertSame(lok_tieStart, (LogicalObjectKey) lok_tieMiddle.get(note.getClass().getDeclaredField("previousTied")));
        Assertions.assertSame(lok_tieEnd, (LogicalObjectKey) lok_tieMiddle.get(note.getClass().getDeclaredField("nextTied")));
        Assertions.assertSame(lok_tieMiddle, (LogicalObjectKey) lok_tieEnd.get(note.getClass().getDeclaredField("previousTied")));
        Assertions.assertSame(null, (LogicalObjectKey) lok_tieEnd.get(note.getClass().getDeclaredField("nextTied")));
        //test subscription
        Assertions.assertTrue(lok_tieStart.subscribedLOKs.containsKey(lok_tieMiddle));
        Assertions.assertEquals(1, lok_tieStart.subscribedLOKs.size());
        Assertions.assertTrue(lok_tieMiddle.subscribedLOKs.containsKey(lok_tieStart));
        Assertions.assertTrue(lok_tieMiddle.subscribedLOKs.containsKey(lok_tieEnd));
        Assertions.assertEquals(2, lok_tieMiddle.subscribedLOKs.size());
        Assertions.assertTrue(lok_tieEnd.subscribedLOKs.containsKey(lok_tieMiddle));
        Assertions.assertEquals(1, lok_tieStart.subscribedLOKs.size());
    }
}
