package com.diamond.gdmusic;

import org.junit.Test;
import org.json.JSONObject;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class PlaybackSnapshotTest {
    private Track track(String id) {
        return new Track(id, "joox", "七里香", "周杰倫", "七里香", "cover", "lyric");
    }

    @Test public void preservesOrderDuplicatesAndCurrentOccurrence() {
        Track first = track("a");
        first.externalMetadata = true;
        first.requestedBitrate = 320;
        first.picUrl = "https://example.com/cover";
        PlaybackSnapshot restored = PlaybackSnapshot.decode(new PlaybackSnapshot(
                Arrays.asList(first, track("b"), track("a")), 2, PlaybackSnapshot.Mode.RANDOM).encode());
        assertEquals(3, restored.tracks.size());
        assertEquals("a", restored.tracks.get(0).id);
        assertEquals("b", restored.tracks.get(1).id);
        assertEquals("a", restored.tracks.get(2).id);
        assertEquals(2, restored.currentIndex);
        assertTrue(restored.tracks.get(0).externalMetadata);
        assertEquals(320, restored.tracks.get(0).requestedBitrate);
        assertEquals(first.picUrl, restored.tracks.get(0).picUrl);
    }

    @Test public void restoresAllModesIncludingEmptyQueue() {
        for (PlaybackSnapshot.Mode mode : PlaybackSnapshot.Mode.values()) {
            PlaybackSnapshot restored = PlaybackSnapshot.decode(new PlaybackSnapshot(
                    Collections.emptyList(), 9, mode).encode());
            assertEquals(mode, restored.mode);
            assertTrue(restored.tracks.isEmpty());
            assertEquals(0, restored.currentIndex);
        }
    }

    @Test public void excludesExpiringResourcesAndProgress() throws Exception {
        Track t = track("a");
        t.audioUrl = "https://example.com/expired";
        t.audioUrlCachedAt = 123;
        t.lyric = "cached lyrics";
        String json = new PlaybackSnapshot(Collections.singletonList(t), 0,
                PlaybackSnapshot.Mode.LIST_LOOP).encode();
        JSONObject root = new JSONObject(json);
        assertFalse(root.has("position"));
        assertFalse(root.getJSONArray("queue").getJSONObject(0).has("audioUrl"));
        Track restored = PlaybackSnapshot.decode(json).tracks.get(0);
        assertNull(restored.audioUrl);
        assertEquals(0, restored.audioUrlCachedAt);
        assertNull(restored.lyric);
    }

    @Test public void corruptSnapshotDefaultsSafely() {
        PlaybackSnapshot restored = PlaybackSnapshot.decode("invalid");
        assertTrue(restored.tracks.isEmpty());
        assertEquals(PlaybackSnapshot.Mode.LIST_LOOP, restored.mode);
    }

    @Test public void skippingInvalidEntriesKeepsCurrentTrack() {
        PlaybackSnapshot restored = PlaybackSnapshot.decode(
                "{\"version\":1,\"mode\":\"BAD\",\"currentIndex\":2,\"queue\":["
                + "{\"id\":\"a\",\"source\":\"netease\"},{},"
                + "{\"id\":\"b\",\"source\":\"joox\"}]}");
        assertEquals(1, restored.currentIndex);
        assertEquals("b", restored.tracks.get(restored.currentIndex).id);
        assertEquals(PlaybackSnapshot.Mode.LIST_LOOP, restored.mode);
    }

    @Test public void clearQueueReplacesPreviousSnapshot() {
        String stored = new PlaybackSnapshot(Arrays.asList(track("a"), track("b")), 1,
                PlaybackSnapshot.Mode.SINGLE_LOOP).encode();
        assertEquals(2, PlaybackSnapshot.decode(stored).tracks.size());
        stored = new PlaybackSnapshot(Collections.emptyList(), 0,
                PlaybackSnapshot.Mode.SINGLE_LOOP).encode();
        assertTrue(PlaybackSnapshot.decode(stored).tracks.isEmpty());
    }
}
