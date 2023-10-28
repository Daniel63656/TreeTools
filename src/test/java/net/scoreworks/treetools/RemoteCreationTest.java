package net.scoreworks.treetools;


import net.scoreworks.test_model.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


public class RemoteCreationTest {
    Remote remote;
    FullScore fullScore;
    Track track;
    Voice voice;
    Staff staff;
    Note note, tieStart, tieEnd;

    @BeforeEach
    public void setVerbose() {
        createSomeClasses();
        Repository repository = new Repository(fullScore, null);
        remote = repository.remote;
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
        Remote.ObjectState lok = remote.createObjectState(staff);
        //content in lok must be same as in staff itself
        Assertions.assertSame(lok.getFields().get(staff.getClass().getDeclaredField("treble")), staff.isTreble());
        //now change the field -> LOK stays the same
        staff.setTreble(false);
        Assertions.assertNotSame(lok.getFields().get(staff.getClass().getDeclaredField("treble")), staff.isTreble());

        Remote.ObjectState lok2 = remote.createObjectState(note);
        //content in lok must be same as in note itself
        Assertions.assertSame(lok2.getFields().get(note.getClass().getDeclaredField("pitch")), note.getPitch());
        //now change the field -> LOK stays the same
        note.setPitch(30);
        Assertions.assertNotSame(lok2.getFields().get(note.getClass().getDeclaredField("pitch")), note.getPitch());
    }

    @Test
    public void testLogicalKeyContainingInheritedFields() throws NoSuchFieldException {
        NoteGroup noteGroup = ((NoteGroup) fullScore.getTrack(0).getNTT(Fraction.ZERO).getNGOT(voice));
        Remote.ObjectState lok = remote.createObjectState(noteGroup);
        Assertions.assertTrue(lok.getFields().containsKey(NoteGroupOrTuplet.class.getDeclaredField("duration")));
    }

    @Test
    public void testCrossReferencesInKeys() throws NoSuchFieldException {
        Remote.ObjectState lok_tieStart = remote.createObjectState(tieStart);
        Remote.ObjectState lok_tieEnd   = remote.createObjectState(tieEnd);

        Assertions.assertSame(lok_tieStart.getFields().get(note.getClass().getDeclaredField("nextTied")), lok_tieEnd);
        Assertions.assertSame(lok_tieStart.getFields().get(note.getClass().getDeclaredField("previousTied")), null);
        Assertions.assertSame(lok_tieEnd.getFields().  get(note.getClass().getDeclaredField("previousTied")), lok_tieStart);
        Assertions.assertSame(lok_tieEnd.getFields().  get(note.getClass().getDeclaredField("nextTied")), null);
    }
}