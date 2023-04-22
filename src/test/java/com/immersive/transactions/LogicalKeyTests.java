package com.immersive.transactions;

import com.immersive.test_model.*;
import com.immersive.transactions.Remote.ObjectState;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


public class LogicalKeyTests {
    Remote LOT = new Remote();
    FullScore fullScore;
    Track track;
    Voice voice;
    Staff staff;
    Note note, tieStart, tieEnd;

    @BeforeEach
    public void setVerbose() {
        createSomeClasses();
    }

    private void createSomeClasses() {
        fullScore = new FullScore();
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
    }

    @Test
    public void testLogicalKeysBeingImmutable() throws NoSuchFieldException {
        ObjectState lok = LOT.createObjectState(staff);
        //content in lok must be same as in staff itself
        Assertions.assertSame(lok.get(staff.getClass().getDeclaredField("treble")), staff.isTreble());
        //now change the field -> LOK stays the same
        staff.setTreble(false);
        Assertions.assertNotSame(lok.get(staff.getClass().getDeclaredField("treble")), staff.isTreble());

        ObjectState lok2 = LOT.createObjectState(note);
        //content in lok must be same as in note itself
        Assertions.assertSame(lok2.get(note.getClass().getDeclaredField("pitch")), note.getPitch());
        //now change the field -> LOK stays the same
        note.setPitch(30);
        Assertions.assertNotSame(lok2.get(note.getClass().getDeclaredField("pitch")), note.getPitch());
    }

    @Test
    public void testLogicalKeyContainingInheritedFields() throws NoSuchFieldException {
        NoteGroup noteGroup = ((NoteGroup) fullScore.getTrack(0).getNTT(Fraction.ZERO).getNGOT(voice));
        ObjectState lok = LOT.createObjectState(noteGroup);
        Assertions.assertTrue(lok.containsKey(NoteGroupOrTuplet.class.getDeclaredField("duration")));
    }

    @Test
    public void testCrossReferencesInKeys() throws NoSuchFieldException {
        ObjectState lok_tieStart = LOT.createObjectState(tieStart);
        ObjectState lok_tieEnd   = LOT.createObjectState(tieEnd);

        Assertions.assertSame(lok_tieStart.crossReferences.get(note.getClass().getDeclaredField("nextTied")), lok_tieEnd);
        Assertions.assertSame(lok_tieStart.crossReferences.get(note.getClass().getDeclaredField("previousTied")), null);
        Assertions.assertSame(lok_tieEnd.crossReferences.  get(note.getClass().getDeclaredField("previousTied")), lok_tieStart);
        Assertions.assertSame(lok_tieEnd.crossReferences.  get(note.getClass().getDeclaredField("nextTied")), null);
    }
}