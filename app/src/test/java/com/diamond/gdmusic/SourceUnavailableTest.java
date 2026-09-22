package com.diamond.gdmusic;

import org.junit.Test;
import static org.junit.Assert.*;
import org.json.JSONObject;
import okhttp3.*;
import java.util.*;

public class SourceUnavailableTest {
    @Test public void response503AndEmptyUrlAreTemporarySourceFailures() throws Exception {
        GdMusicApi api = new GdMusicApi();
        List<String> notices = new ArrayList<>();
        SourceUnavailableException.Listener listener = notices::add;
        SourceUnavailableException.addListener(listener);
        try {
            Response response = new Response.Builder().request(new Request.Builder()
                    .url("https://music-api.gdstudio.xyz/api.php?types=url&source=joox").build())
                    .protocol(Protocol.HTTP_1_1).code(503).message("Unavailable")
                    .body(ResponseBody.create(MediaType.get("text/plain"), "unavailable")).build();
            try { api.requireSuccessfulBody(response, "播放地址"); fail(); }
            catch (SourceUnavailableException expected) { assertEquals("joox", expected.source); }
            for (String body : Arrays.asList("{\"url\":\"\"}", "{\"url\":null}", "{}")) {
                try { api.parseAudioUrl("joox", new JSONObject(body), body); fail(); }
                catch (SourceUnavailableException expected) { assertEquals("joox", expected.source); }
            }
            assertEquals(Arrays.asList("joox", "joox", "joox", "joox"), notices);
        } finally { SourceUnavailableException.removeListener(listener); }
    }
    @Test public void unavailableCandidateSkipsRemainingCandidatesAndContinuesNextSource() {
        List<String> calls = new ArrayList<>();
        GdMusicApi api = new GdMusicApi() {
            @Override List<String> automaticSources() { return Arrays.asList("joox", "netease"); }
            @Override public void searchTracks(String q, String source, int count, int page, SearchCallback callback) {
                List<Track> results = new ArrayList<>();
                for (int i = 0; i < 3; i++) results.add(new Track(""+i, source, "七里香", "周杰伦", "", "", ""));
                callback.onSuccess(results);
            }
            @Override public void getAudioUrl(Track track, int bitrate, TrackCallback callback) {
                calls.add(track.source + track.id);
                if (track.source.equals("joox")) callback.onError(new SourceUnavailableException("joox", "HTTP 503"));
                else { track.audioUrl = "https://example.com/audio.mp3"; callback.onSuccess(track); }
            }
        };
        api.resolveExternalTrack(new Track("a", "apple", "七里香", "周杰伦", "", "", ""), 320,
            new GdMusicApi.TrackCallback() {
                public void onSuccess(Track track) { assertEquals("netease", track.source); }
                public void onError(Exception error) { fail(error.toString()); }
            });
        assertEquals(Arrays.asList("joox0", "netease0"), calls);
    }
}
