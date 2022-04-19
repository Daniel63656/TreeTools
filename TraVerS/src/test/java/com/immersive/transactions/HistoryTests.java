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

        NoteTimeTick ntt = new NoteTimeTick(track, 0L);
        NoteGroup noteGroup = new NoteGroup(ntt, staff, voice, 8, true);
        note = new Note(noteGroup, 69, false, NoteName.A);

        ntt = new NoteTimeTick(track, 8L);
        noteGroup = new NoteGroup(ntt, staff, voice, 8, true);
        note2 = new Note(noteGroup, 69, false, NoteName.A);

        ntt = new NoteTimeTick(track, 16L);
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
    public void testAddingCommitChanges() {
        Workcopy workcopy = createTransactionWorkcopy();
        LogicalObjectKey before = workcopy.LOT.getKey(note2);
        note.setPitch(30);
        note2.setPitch(31);
        Commit first = tm.commit(workcopy.rootEntity);
        Assertions.assertSame(2, first.changeRecords.size());
        note2.setPitch(32);
        note3.setPitch(33);
        Commit second = tm.commit(workcopy.rootEntity);
        LogicalObjectKey after = workcopy.LOT.getKey(note2);
        Assertions.assertSame(2, second.changeRecords.size());

        second.addTo(first);
        Assertions.assertSame(3, first.changeRecords.size());
        Assertions.assertTrue(first.changeRecords.containsKey(before));
        Assertions.assertSame(after, first.changeRecords.get(before));
    }

    @Test
    public void testCreationAndDeletion() {
        Workcopy workcopy = createTransactionWorkcopy();
        NoteGroup noteGroup = ((NoteGroup)track.getNTT(0).getNGOT(voice));
        
        Note newNote = new Note(noteGroup, 40, false, NoteName.A);
        note.clear();
        Commit first = tm.commit(workcopy.rootEntity);

        newNote.setPitch(70);
        Commit second = tm.commit(workcopy.rootEntity);

        newNote.clear();
        new Note(noteGroup, 40, false, NoteName.A);
        Commit third = tm.commit(workcopy.rootEntity);

        third.addTo(second);
        second.addTo(first);
        Assertions.assertSame(1, second.creationRecords.size());
        Assertions.assertSame(1, second.deletionRecords.size());
        Assertions.assertSame(0, second.changeRecords.size());
        Assertions.assertSame(1, first.creationRecords.size());
        Assertions.assertSame(1, first.deletionRecords.size());
        Assertions.assertSame(0, first.changeRecords.size());
    }

    @Test
    public void testSimpleUndo() {
        Workcopy workcopy = createTransactionWorkcopy();
        tm.enableUndoRedos(64);
        NoteGroup noteGroup = ((NoteGroup)track.getNTT(0).getNGOT(voice));
        note.setPitch(30);
        new Note(noteGroup, 40, false, NoteName.A);
        tm.commit(workcopy.rootEntity);
        note2.setPitch(40);
        tm.commit(workcopy.rootEntity);
        tm.createUndoState();
        workcopy.rootEntity.undo();
        Assertions.assertEquals(69, note.getPitch());
    }

    @Test
    public void test() {
        Workcopy workcopy = createTransactionWorkcopyWithTie();
        tm.enableUndoRedos(64);
        tieStart.setPitch(30);
        tm.commit(workcopy.rootEntity);
        tm.createUndoState();
        workcopy.rootEntity.undo();

        tieEnd.setPitch(30);
        tm.commit(workcopy.rootEntity);

    }
}