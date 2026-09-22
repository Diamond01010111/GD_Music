package com.diamond.gdmusic;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Durable queue metadata only: never persist expiring audio URLs or playback position. */
final class PlaybackSnapshot {
    enum Mode { LIST_LOOP, SINGLE_LOOP, RANDOM }
    final List<Track> tracks;
    final int currentIndex;
    final Mode mode;

    PlaybackSnapshot(List<Track> tracks, int currentIndex, Mode mode) {
        this.tracks = new ArrayList<>(tracks);
        this.currentIndex = tracks.isEmpty() ? 0 : Math.max(0, Math.min(currentIndex, tracks.size() - 1));
        this.mode = mode;
    }

    String encode() {
        try {
            JSONArray queue = new JSONArray();
            for (Track t : tracks) {
                queue.put(new JSONObject().put("id", t.id).put("source", t.source)
                        .put("name", t.name).put("artist", t.artist).put("album", t.album)
                        .put("picId", t.picId).put("lyricId", t.lyricId).put("picUrl", t.picUrl)
                        .put("requestedBitrate", t.requestedBitrate)
                        .put("externalMetadata", t.externalMetadata));
            }
            return new JSONObject().put("version", 1).put("queue", queue)
                    .put("currentIndex", currentIndex).put("mode", mode.name()).toString();
        } catch (org.json.JSONException e) {
            throw new IllegalStateException("Cannot serialize playback queue", e);
        }
    }

    static PlaybackSnapshot decode(String json) {
        List<Track> tracks = new ArrayList<>();
        Mode mode = Mode.LIST_LOOP;
        int selected = 0;
        try {
            JSONObject root = new JSONObject(json);
            if (root.optInt("version") != 1) return new PlaybackSnapshot(tracks, 0, mode);
            try { mode = Mode.valueOf(root.optString("mode")); }
            catch (IllegalArgumentException ignored) { }
            JSONArray queue = root.optJSONArray("queue");
            int originalIndex = Math.max(0, root.optInt("currentIndex"));
            if (queue != null) for (int i = 0; i < queue.length(); i++) {
                JSONObject item = queue.optJSONObject(i);
                if (item == null || item.optString("id").isEmpty()
                        || item.optString("source").isEmpty()) continue;
                if (i <= originalIndex) selected = tracks.size();
                Track t = new Track(item.optString("id"), item.optString("source"),
                        item.optString("name"), item.optString("artist"), item.optString("album"),
                        item.optString("picId"), item.optString("lyricId"));
                t.picUrl = item.optString("picUrl", null);
                t.requestedBitrate = item.optInt("requestedBitrate");
                t.externalMetadata = item.optBoolean("externalMetadata");
                tracks.add(t);
            }
        } catch (Exception ignored) {
            tracks.clear();
            mode = Mode.LIST_LOOP;
        }
        return new PlaybackSnapshot(tracks, selected, mode);
    }
}
