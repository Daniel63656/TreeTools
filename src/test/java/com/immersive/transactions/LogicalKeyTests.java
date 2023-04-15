package com.immersive.transactions;

import com.immersive.test_model.*;
import com.immersive.transactions.LogicalObjectTree.LogicalObjectKey;


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

        NoteTimeTick ntt = new NoteTimeTick(track, Fraction.ZERO);
        note = new Note(new NoteGroup(ntt, staff, voice, 8, true), 69, false, NoteName.A);

        ntt = new NoteTimeTick(this.track, Fraction.getFraction(8, 1));
        tieStart = new Note(new NoteGroup(ntt, this.staff, this.voice, 8, true), 69, false, NoteName.A);

        ntt = new NoteTimeTick(this.track, Fraction.getFraction(16, 1));
        tieEnd = new Note(new NoteGroup(ntt, this.staff, this.voice, 8, true), 69, false, NoteName.A);
        tieStart.tieWith(tieEnd);

        return fullScore;
    }

    @Test
    public void testLogicalKeysBeingImmutable() throws NoSuchFieldException {
        createSomeClasses();

        LogicalObjectKey lok = LOT.createLogicalObjectKey(staff);
        //content in lok must be same as in staff itself
        Assertions.assertSame(lok.get(staff.getClass().getDeclaredField("treble")), staff.isTreble());
        //now change the field
        staff.setTreble(false);
        Assertions.assertNotSame(lok.get(staff.getClass().getDeclaredField("treble")), staff.isTreble());

        LogicalObjectKey lok2 = LOT.createLogicalObjectKey(note);
        //content in lok must be same as in note itself
        Assertions.assertSame(lok2.get(note.getClass().getDeclaredField("pitch")), note.getPitch());
        //now change the field
        note.setPitch(30);
        Assertions.assertNotSame(lok2.get(note.getClass().getDeclaredField("pitch")), note.getPitch());
    }

    @Test
    public void testLogicalKeyContainingInheritedFields() throws NoSuchFieldException {
        FullScore fullScore = createSomeClasses();
        NoteGroup noteGroup = ((NoteGroup) fullScore.getTrack(0).getNTT(Fraction.ZERO).getNGOT(voice));

        LogicalObjectKey lok = LOT.createLogicalObjectKey(noteGroup);
        Assertions.assertTrue(lok.containsKey(NoteGroupOrTuplet.class.getDeclaredField("duration")));
    }

    @Test
    public void testCrossReferencesInKeys() throws NoSuchFieldException {
        createSomeClasses();
        LogicalObjectKey lok_tieStart = LOT.createLogicalObjectKey(tieStart);
        LogicalObjectKey lok_tieEnd   = LOT.createLogicalObjectKey(tieEnd);

        Assertions.assertSame(lok_tieStart.get(note.getClass().getDeclaredField("nextTied")), lok_tieEnd);
        Assertions.assertSame(lok_tieStart.get(note.getClass().getDeclaredField("previousTied")), null);
        Assertions.assertSame(lok_tieEnd.  get(note.getClass().getDeclaredField("previousTied")), lok_tieStart);
        Assertions.assertSame(lok_tieEnd.  get(note.getClass().getDeclaredField("nextTied")), null);
    }
}