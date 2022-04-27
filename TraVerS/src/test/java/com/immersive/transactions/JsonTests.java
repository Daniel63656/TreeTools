package com.immersive.transactions;

import com.immersive.test_model.Fo;
import com.immersive.test_model.Foo;
import com.immersive.test_model.FullScore;
import com.immersive.test_model.Note;
import com.immersive.test_model.NoteGroup;
import com.immersive.test_model.NoteName;
import com.immersive.test_model.NoteTimeTick;
import com.immersive.test_model.Staff;
import com.immersive.test_model.Track;
import com.immersive.test_model.Voice;

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
        fullScore.name = "MyFullScore";
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

        return fullScore;
    }

    @Test
    public void testSerializationAndDeserialization() {
        FullScore fullScore = createFullScore();
        fullScore.foo = new Foo();
        String json = JsonParser.toJson(fullScore, true);
        System.out.println(json);
        FullScore fs = JsonParser.fromJson(json, FullScore.class);
        System.out.println(fs.name);
    }

}