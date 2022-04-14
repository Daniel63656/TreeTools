package com.immersive.transactions;

import com.immersive.test_model.*;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class LogicalKeyTests {
    LogicalObjectTree LOT = new LogicalObjectTree();
    Track track;
    Voice voice;
    Staff staff;
    Note note, tieStart, tieEnd;

    private FullScore createSomeClasses() {
        FullScore fullScore = new FullScore();
        track = new Track(fullScore);
        staff = new Staff(track, true);
        voice = new Voice(track, 0);

        NoteTimeTick ntt = new NoteTimeTick(track, 0L);
        note = new Note(new NoteGroup(ntt, staff, voice, 8, true), 69, false, NoteName.A);

        ntt = new NoteTimeTick(this.track, 8L);
        tieStart = new Note(new NoteGroup(ntt, this.staff, this.voice, 8, true), 69, false, NoteName.A);

        ntt = new NoteTimeTick(this.track, 16L);
        tieEnd = new Note(new NoteGroup(ntt, this.staff, this.voice, 8, true), 69, false, NoteName.A);
        tieStart.tieWith(tieEnd);

        return fullScore;
    }

    @Test
    public void testLogicalKeysBeingImmutable() throws NoSuchFieldException {
        createSomeClasses();

        LogicalObjectKey lok = LOT.createLogicalObjectKey(staff, null, false);
        //content in lok must be same as in staff itself
        Assertions.assertSame((Boolean)lok.get(staff.getClass().getDeclaredField("treble")), staff.isTreble());
        //now change the field
        staff.setTreble(false);
        Assertions.assertNotSame((Boolean)lok.get(staff.getClass().getDeclaredField("treble")), staff.isTreble());

        LogicalObjectKey lok2 = LOT.createLogicalObjectKey(note, null, false);
        //content in lok must be same as in note itself
        Assertions.assertSame((Integer)lok2.get(note.getClass().getDeclaredField("pitch")), note.getPitch());
        //now change the field
        note.setPitch(30);
        Assertions.assertNotSame((Integer)lok2.get(note.getClass().getDeclaredField("pitch")), note.getPitch());
    }

    @Test
    public void testLogicalKeyContainingInheritedFields() throws NoSuchFieldException {
        FullScore fullScore = createSomeClasses();
        NoteGroup noteGroup = ((NoteGroup) fullScore.getTrack(0).getNTT(0).getNGOT(voice));

        LogicalObjectKey lok = LOT.createLogicalObjectKey(noteGroup, null, false);
        Assertions.assertTrue(lok.containsKey(NoteGroupOrTuplet.class.getDeclaredField("duration")));
    }

    @Test
    public void testCrossReferencesInKeys() throws NoSuchFieldException {
        createSomeClasses();
        LogicalObjectKey lok_tieStart = LOT.createLogicalObjectKey(tieStart, null, false);
        LogicalObjectKey lok_tieEnd   = LOT.createLogicalObjectKey(tieEnd, null, false);

        Assertions.assertSame((LogicalObjectKey) lok_tieStart.get(note.getClass().getDeclaredField("nextTied")), lok_tieEnd);
        Assertions.assertSame((LogicalObjectKey) lok_tieStart.get(note.getClass().getDeclaredField("previousTied")), null);
        Assertions.assertSame((LogicalObjectKey) lok_tieEnd.  get(note.getClass().getDeclaredField("previousTied")), lok_tieStart);
        Assertions.assertSame((LogicalObjectKey) lok_tieEnd.  get(note.getClass().getDeclaredField("nextTied")), null);
    }
}