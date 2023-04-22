package com.immersive.transactions;

import com.immersive.test_model.*;
import com.immersive.transactions.LogicalObjectTree.LogicalObjectKey;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class HistoryTests {
    TransactionManager tm = TransactionManager.getInstance();
    Track track;
    Voice voice;
    Staff staff;
    Note note, note2, note3, tieStart, tieEnd;

    @AfterEach
    public void cleanUp(){
        tm.shutdown();
    }

    private Workcopy createTransactionWorkcopy() {
        FullScore fullScore = new FullScore();
        track = new Track(fullScore);
        staff = new Staff(track, true);
        voice = new Voice(track, 0);

        NoteTimeTick ntt = new NoteTimeTick(track, Fraction.ZERO);
        NoteGroup noteGroup = new NoteGroup(ntt, staff, voice, 8, true);
        note = new Note(noteGroup, 69, false, NoteName.A);

        ntt = new NoteTimeTick(track, Fraction.getFraction(8, 1));
        noteGroup = new NoteGroup(ntt, staff, voice, 8, true);
        note2 = new Note(noteGroup, 69, false, NoteName.A);

        ntt = new NoteTimeTick(track, Fraction.getFraction(16, 1));
        noteGroup = new NoteGroup(ntt, staff, voice, 8, true);
        note3 = new Note(noteGroup, 69, false, NoteName.A);

        tm.enableTransactionsForRootEntity(fullScore);
        return tm.workcopies.get(fullScore);
    }

    private Workcopy createTransactionWorkcopyWithTie() {
        FullScore fullScore = new FullScore();
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
        tieEnd = new Note(noteGroup, 69, false, NoteName.A);
        tieStart.tieWith(tieEnd);

        tm.enableTransactionsForRootEntity(fullScore);
        return tm.workcopies.get(fullScore);
    }

    @Test
    public void testParamsDependencyChanging() {
        Workcopy workcopy = createTransactionWorkcopy();
        tm.enableUndoRedos(64);
        NoteGroup noteGroup = note.getOwner();
        Note newNote = new Note(noteGroup, 60, false, NoteName.E);
        note.clear();
        tm.commit(workcopy.rootEntity);
        noteGroup.stemUp = false;
        tm.commit(workcopy.rootEntity);
        noteGroup.stemUp = true;
        tm.commit(workcopy.rootEntity);

        LogicalObjectKey NG_key = workcopy.LOT.getKey(noteGroup);
        for (Object[] objects : tm.history.ongoingCommit.creationRecords.values())
            objects[0] = NG_key;
        for (Object[] objects : tm.history.ongoingCommit.deletionRecords.values())
            objects[0] = NG_key;
    }

    @Test
    public void testSimpleUndo() {
        Workcopy workcopy = createTransactionWorkcopy();
        tm.enableUndoRedos(64);
        NoteGroup noteGroup = note.getOwner();
        note.setPitch(30);
        new Note(noteGroup, 40, false, NoteName.A);
        tm.commit(workcopy.rootEntity);
        note2.setPitch(40);
        tm.commit(workcopy.rootEntity);
        tm.createUndoState();
        workcopy.rootEntity.undo();
        Assertions.assertEquals(69, note.getPitch());
    }

    /*@Test
    public void testUndoAndRedo() {
        Workcopy workcopy = createTransactionWorkcopyWithTie();
        tm.enableUndoRedos(64);
        tieStart.setPitch(30);

        NoteGroup ng = note.getOwner();
        new Note(ng, 60, false, NoteName.A);
        note.clear();

        tm.commit(workcopy.rootEntity);
        tm.createUndoState();
        workcopy.rootEntity.undo();
        workcopy.rootEntity.redo();

        note.setPitch(70);
        tm.commit(workcopy.rootEntity);
    }*/

    /*@Test
    public void testOldSubscribersGettingRemoved() {
        Workcopy workcopy = createTransactionWorkcopy();
        tm.enableUndoRedos(64);
        note2.tieWith(note3);
        tm.commit(workcopy.rootEntity);
        tm.createUndoState();
        Commit undo = workcopy.rootEntity.undo();
        for (LogicalObjectKey after : undo.changeRecords.values())
            Assertions.assertSame(0, after.subscribedLOKs.size());
    }*/
}