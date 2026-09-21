package com.diamond.gdmusic;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class GdMusicApiBudgetTest {
    @Test public void exhaustedBudgetStopsAllEndpointsAndFallbacks() {
        while (RequestTracker.tryAcquire()) { }
        GdMusicApi api = new GdMusicApi();
        Track track = new Track("1", "netease", "后来", "刘若英", "", "pic", "lyric");
        AtomicInteger rejected = new AtomicInteger();
        GdMusicApi.TrackCallback callback = new GdMusicApi.TrackCallback() {
            public void onSuccess(Track value) { fail("Request must not be dispatched"); }
            public void onError(Exception error) {
                assertTrue(error instanceof GdMusicApi.RateLimitException);
                rejected.incrementAndGet();
            }
        };
        api.getAudioUrl(track, callback);
        api.getPicUrl(track, callback);
        api.getLyric(track, callback);
        api.resolveExternalTrack(track, 320, callback);
        api.resolveLyrics(track, null, callback);
        api.searchTracks("后来", "netease", 15, 1, new GdMusicApi.SearchCallback() {
            public void onSuccess(List<Track> tracks) { fail("Request must not be dispatched"); }
            public void onError(Exception error) {
                assertTrue(error instanceof GdMusicApi.RateLimitException);
                rejected.incrementAndGet();
            }
        });
        assertEquals(6, rejected.get());
        assertEquals(50, RequestTracker.countLastFiveMinutes());
    }
}
