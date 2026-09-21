package com.diamond.gdmusic;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GdMusicApiArtistMatchTest {

    @Test public void matchesTraditionalJooxArtists() {
        assertTrue(GdMusicApi.hasMatchingArtist("周杰伦、温岚", "周杰倫 / 溫嵐"));
        assertTrue(GdMusicApi.hasMatchingArtist("陈奕迅", "陳奕迅"));
        assertTrue(GdMusicApi.hasMatchingArtist("张学友", "張學友"));
        assertFalse(GdMusicApi.hasMatchingArtist("陈奕迅", "陳小春"));
        org.junit.Assert.assertEquals("愛與誠", ChineseText.traditional("爱与诚"));
        org.junit.Assert.assertEquals("后来", ChineseText.simplified("後來"));
    }

    @Test
    public void matchesWhenAtLeastOneChineseArtistIsShared() {
        assertTrue(GdMusicApi.hasMatchingArtist("周杰伦、温岚", "周杰伦"));
    }

    @Test
    public void matchesArtistsAcrossDifferentSeparatorsAndCase() {
        assertTrue(
                GdMusicApi.hasMatchingArtist(
                        "Taylor Swift / Ed Sheeran",
                        "taylor swift, Post Malone"
                )
        );
    }

    @Test
    public void rejectsSameTitleCandidateWithDifferentArtists() {
        assertFalse(GdMusicApi.hasMatchingArtist("周杰伦", "陈奕迅、林俊杰"));
    }
}
