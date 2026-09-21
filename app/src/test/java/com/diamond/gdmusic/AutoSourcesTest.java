package com.diamond.gdmusic;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class AutoSourcesTest {
    @Test public void defaultsAndValidation() {
        assertEquals(Arrays.asList("netease", "joox", "bilibili"), AutoSourcePreferences.selected());
        assertEquals(Arrays.asList("joox", "kuwo"), AutoSourcePreferences.sanitize(
                Arrays.asList("joox", "apple", "joox", "invalid", "kuwo")));
        assertTrue(AutoSourcePreferences.sanitize(Collections.emptyList()).isEmpty());
    }
    @Test public void automaticFallbackUsesOnlySelectedProvidersEvenForAnUnselectedOriginal() {
        List<String> calls = new ArrayList<>();
        GdMusicApi api = fake(Arrays.asList("joox", "bilibili"), calls);
        api.resolveExternalTrack(reference(), 320, ignored());
        assertEquals(Arrays.asList("joox", "bilibili"), calls);
    }
    @Test public void emptySelectionDisablesFallbackButExplicitSourceStillWorks() {
        List<String> calls = new ArrayList<>();
        GdMusicApi api = fake(Collections.emptyList(), calls);
        api.resolveExternalTrack(reference(), 320, ignored());
        assertTrue(calls.isEmpty());
        api.resolveTrackFromSource(reference(), "joox", 320, ignored());
        assertEquals(Collections.singletonList("joox"), calls);
    }
    @Test public void disablingFallbackStillAllowsCurrentTrackLyrics() {
        List<String> calls = new ArrayList<>();
        GdMusicApi api = new GdMusicApi() {
            @Override List<String> automaticSources() { return Collections.emptyList(); }
            @Override public void getLyric(Track track, TrackCallback callback) {
                calls.add(track.source);
                callback.onError(new java.io.IOException("No lyrics"));
            }
        };
        Track track = reference();
        track.lyricId = "lyric";
        api.resolveLyrics(track, null, new GdMusicApi.TrackCallback() {
            public void onSuccess(Track value) { fail(); }
            public void onError(Exception error) { }
        });
        assertEquals(Collections.singletonList("netease"), calls);
    }
    private static Track reference() { return new Track("id", "netease", "七里香", "周杰伦", "", "", ""); }
    private static GdMusicApi fake(List<String> selected, List<String> calls) {
        return new GdMusicApi() {
            @Override List<String> automaticSources() { return selected; }
            @Override public void searchTracks(String q, String source, int count, int page, SearchCallback callback) {
                calls.add(source);
                callback.onSuccess(Collections.emptyList());
            }
        };
    }
    private static GdMusicApi.TrackCallback ignored() {
        return new GdMusicApi.TrackCallback() {
            public void onSuccess(Track track) { fail("No fixture candidates"); }
            public void onError(Exception error) { assertTrue(error instanceof GdMusicApi.SourcesExhaustedException); }
        };
    }
}
