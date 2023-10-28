package net.scoreworks.treetools;


import net.scoreworks.test_model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class JsonTests {
    TransactionManager tm = TransactionManager.getInstance();
    Track track;
    Voice voice;
    Staff staff;
    Note note, tieStart, tieEnd;

    @AfterEach
    public void cleanUp(){
        tm.shutdown();
    }

    private FullScore createFullScore() {
        FullScore fullScore = new FullScore();
        fullScore.name = "MyScore";
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

        return fullScore;
    }

    @Test
    public void testSerializationAndDeserialization() {
        FullScore fullScore = createFullScore();
        String json = JsonParser.toJson(fullScore, true);
        System.out.println(json);
        FullScore fs = JsonParser.fromJson(json, FullScore.class);
        System.out.println(fs.name);
    }
}