package com.diamond.gdmusic;

import org.junit.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

public class SourceFallbackTest {
    private static Track song(String id, String source, String name, String artist) {
        return new Track(id, source, name, artist, "", "", "lyric");
    }
    private static final Track REFERENCE = song("ref", "apple", "七里香", "周杰伦");
    private static class UnavailableApi extends GdMusicApi {
        int searches, audioRequests, lyricRequests;
        List<String> sources = new ArrayList<>();
        @Override public void searchTracks(String keyword, String source, int count, int page, SearchCallback callback) {
            searches++;
            sources.add(source);
            List<Track> tracks = new ArrayList<>();
            for (int i = 0; i < 15; i++) tracks.add(song("id" + i, source, "七里香", "周杰倫"));
            callback.onSuccess(tracks);
        }
        @Override public void getAudioUrl(Track track, int bitrate, TrackCallback callback) {
            audioRequests++;
            callback.onError(new java.io.IOException("unavailable"));
        }
        @Override public void getLyric(Track track, TrackCallback callback) {
            lyricRequests++;
            callback.onError(new java.io.IOException("unavailable"));
        }
    }
    @Test public void fifteenMatchesPerSourceCannotConsumeFiftyRequests() {
        UnavailableApi api = new UnavailableApi();
        AtomicBoolean failed = new AtomicBoolean();
        api.resolveExternalTrack(REFERENCE, 320, new GdMusicApi.TrackCallback() {
            public void onSuccess(Track t) { fail(); }
            public void onError(Exception error) {
                assertTrue(error instanceof GdMusicApi.SourcesExhaustedException);
                failed.set(true);
            }
        });
        assertTrue(failed.get());
        assertEquals(Arrays.asList("netease", "joox", "bilibili"), api.sources);
        assertEquals(3, api.searches);
        assertEquals(9, api.audioRequests);
    }
    @Test public void matchingRequiresTitleAndArtistAndDeduplicatesIds() {
        Track match = song("1", "joox", "七里香", "周杰倫");
        assertEquals(Collections.singletonList(match), GdMusicApi.matchingArtistCandidates(REFERENCE,
            Arrays.asList(song("2", "joox", "晴天", "周杰倫"),
                song("3", "joox", "七里香", "其他歌手"), match, match)));
    }
    @Test public void lateSearchAfterTimeoutDoesNotContinueFallback() {
        AtomicBoolean active = new AtomicBoolean(true);
        UnavailableApi api = new UnavailableApi() {
            @Override public void searchTracks(String keyword, String source, int count, int page, SearchCallback callback) {
                active.set(false); // The blocking player caller has timed out.
                super.searchTracks(keyword, source, count, page, callback);
            }
        };
        api.resolveExternalTrack(REFERENCE, 320, new GdMusicApi.TrackCallback() {
            public boolean isActive() { return active.get(); }
            public void onSuccess(Track t) { fail(); }
            public void onError(Exception error) { fail(); }
        });
        assertEquals(1, api.searches);
        assertEquals(0, api.audioRequests);
    }
    @Test public void lyricsHaveAnIndependentSmallCandidateLimit() {
        UnavailableApi api = new UnavailableApi();
        api.resolveLyrics(REFERENCE, null, new GdMusicApi.TrackCallback() {
            public void onSuccess(Track t) { fail(); }
            public void onError(Exception error) { }
        });
        assertEquals(3, api.searches);
        assertEquals(3, api.lyricRequests);
    }
}
