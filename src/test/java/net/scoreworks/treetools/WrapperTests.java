package net.scoreworks.treetools;

import net.scoreworks.test_model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

public class WrapperTests {
    TransactionManager tm = TransactionManager.getInstance();
    FullScore fullScore, read;
    Track track;
    Voice voice;
    Staff staff;
    Note note, readNote;

    WrapperScope writeWs, readWs;
    NoteWrapper writeNw, readNw;
    NoteGroupWrapper writeNgw, readNgw;


    @BeforeEach
    public void prepareFullScores() {
        createFullScoreWithTransactionsEnabled();
        read = (FullScore) tm.clone(fullScore);
        readNote = ((NoteGroup) read.getTrack(0).getNTT(Fraction.ZERO).getNGOT(read.getTrack(0).getVoice(0))).getNote(0);

        writeWs = new WrapperScope(fullScore);
        readWs  = new WrapperScope(read);

        writeNw = new NoteWrapper(writeWs, note);
        readNw  = new NoteWrapper(readWs, readNote);
        writeNgw = new NoteGroupWrapper(writeWs, note.getOwner());
        readNgw  = new NoteGroupWrapper(readWs, readNote.getOwner());
    }

    @AfterEach
    public void cleanUp(){
        tm.shutdown();
    }

    private void createFullScoreWithTransactionsEnabled() {
        fullScore = new FullScore();
        track = new Track(fullScore);
        staff = new Staff(track, true);
        voice = new Voice(track, 0);

        NoteTimeTick ntt = new NoteTimeTick(track, Fraction.ZERO);
        NoteGroup noteGroup = new NoteGroup(ntt, staff, voice, 8, true);
        note = new Note(noteGroup, 69, false, NoteName.A);
        tm.enableTransactionsForRootEntity(fullScore);
    }

    @Test
    public void testWrapperDetectsChanges() {
        note.setPitch(30);
        Assertions.assertTrue(writeNw.changeDetected);
        Assertions.assertFalse(readNw.changeDetected);

        fullScore.commit();
        read.pull();
        Assertions.assertTrue(readNw.changeDetected);
    }

    @Test
    public void testWrapperDetectsDeletions() {
        note.getOwner().remove();
        Assertions.assertTrue(writeNw.removalDetected);
        Assertions.assertTrue(writeNgw.removalDetected);
        Assertions.assertFalse(readNw.removalDetected);
        Assertions.assertFalse(readNgw.removalDetected);

        fullScore.commit();
        read.pull();
        Assertions.assertTrue(readNw.removalDetected);
        Assertions.assertTrue(readNgw.removalDetected);
    }

    @Test
    public void testRemoveNotifiesOwner() {
        note.remove();
        Assertions.assertTrue(writeNgw.changeDetected);
        Assertions.assertFalse(readNgw.changeDetected);

        fullScore.commit();
        read.pull();
        Assertions.assertTrue(readNgw.changeDetected);
    }

    @Test
    public void testCreationNotifiesOwner() {
        new Note(note.owner, 40, false, NoteName.B);
        Assertions.assertTrue(writeNgw.changeDetected);
        Assertions.assertFalse(readNgw.changeDetected);

        fullScore.commit();
        read.pull();
        Assertions.assertTrue(readNgw.changeDetected);
    }

    private static class WrapperScope implements net.scoreworks.treetools.WrapperScope {
        private final Map<MutableObject, Wrapper<?>> registeredWrappers = new HashMap<>();

        //it is good practise to add the wrapper to the RootEntity in the constructor
        public WrapperScope(FullScore fullScore) {
            fullScore.addWrapperScope(this);
        }

        @Override
        public Map<MutableObject, Wrapper<?>> getRegisteredWrappers() {
            return registeredWrappers;
        }
    }

    private static class NoteWrapper extends Wrapper<Note> {
        boolean changeDetected, removalDetected;

        public NoteWrapper(WrapperScope ws, Note wrapped) {
            super(ws, wrapped);
        }
        @Override
        public void onWrappedRemoved() {
            System.out.println("Detected note deletion");
            removalDetected = true;
        }
        @Override
        public void onWrappedChanged() {
            System.out.println("Detected note change");
            changeDetected = true;
        }
    }

    private static class NoteGroupWrapper extends Wrapper<NoteGroup> {
        boolean changeDetected, removalDetected;

        public NoteGroupWrapper(WrapperScope ws, NoteGroup wrapped) {
            super(ws, wrapped);
        }
        @Override
        public void onWrappedRemoved() {
            System.out.println("Detected noteGroup deletion");
            removalDetected = true;
        }
        @Override
        public void onWrappedChanged() {
            System.out.println("Detected noteGroup change");
            changeDetected = true;
        }
    }
}