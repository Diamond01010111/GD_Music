package com.diamond.gdmusic;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LocalPlaylistStore {

    private static final String PREF_NAME = "local_playlists";
    private static final String KEY_PLAYLISTS = "playlists";
    private static final String KEY_FAVORITES = "favorites";
    private static final String LEGACY_PLAYLIST_NAME = "我的收藏";
    public static final String LIKED_PLAYLIST_NAME = "我喜欢的";

    private final SharedPreferences preferences;

    public static class LocalPlaylist {
        public final String id;
        public final String name;
        public final List<Track> tracks;

        public LocalPlaylist(String id, String name, List<Track> tracks) {
            this.id = id;
            this.name = name;
            this.tracks = tracks;
        }

        public Track getCoverTrack() {
            return tracks.isEmpty() ? null : tracks.get(0);
        }
    }

    public LocalPlaylistStore(Context context) {
        preferences = context.getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
        );
        migrateLegacyFavoritesIfNeeded();
    }

    public List<LocalPlaylist> getPlaylists() {
        List<LocalPlaylist> playlists = new ArrayList<>();

        try {
            JSONArray array = new JSONArray(
                    preferences.getString(KEY_PLAYLISTS, "[]")
            );

            for (int index = 0; index < array.length(); index++) {
                JSONObject object = array.getJSONObject(index);
                playlists.add(
                        new LocalPlaylist(
                                object.optString("id", UUID.randomUUID().toString()),
                                object.optString("name", "未命名歌单"),
                                readTracks(object.optJSONArray("tracks"))
                        )
                );
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }

        moveLikedPlaylistFirst(playlists);
        return playlists;
    }

    public LocalPlaylist createPlaylist(String name) {
        String normalizedName = name == null ? "" : name.trim();

        if (normalizedName.isEmpty()) {
            return null;
        }

        List<LocalPlaylist> playlists = getPlaylists();
        LocalPlaylist playlist = new LocalPlaylist(
                UUID.randomUUID().toString(),
                normalizedName,
                new ArrayList<>()
        );

        if (LIKED_PLAYLIST_NAME.equals(normalizedName)) {
            playlists.add(0, playlist);
        } else {
            playlists.add(playlist);
        }
        savePlaylists(playlists);
        return playlist;
    }

    public LocalPlaylist createPlaylist(String name, Track firstTrack) {
        LocalPlaylist playlist = createPlaylist(name);

        if (playlist != null && firstTrack != null) {
            addTrackToPlaylist(playlist.id, firstTrack);
            return findPlaylist(playlist.id);
        }

        return playlist;
    }

    public LocalPlaylist createPlaylist(String name, List<Track> importedTracks) {
        String normalizedName = name == null ? "" : name.trim();
        if (normalizedName.isEmpty() || importedTracks == null) {
            return null;
        }

        List<Track> tracks = new ArrayList<>();
        for (Track track : importedTracks) {
            if (track != null && isValidTrack(track) && !containsTrack(tracks, track)) {
                tracks.add(track);
            }
        }

        List<LocalPlaylist> playlists = getPlaylists();
        LocalPlaylist playlist = new LocalPlaylist(
                UUID.randomUUID().toString(),
                normalizedName,
                tracks
        );
        playlists.add(playlist);
        savePlaylists(playlists);
        return playlist;
    }

    public boolean addTrackToPlaylist(String playlistId, Track track) {
        if (playlistId == null || track == null || !isValidTrack(track)) {
            return false;
        }

        List<LocalPlaylist> playlists = getPlaylists();

        for (LocalPlaylist playlist : playlists) {
            if (!playlist.id.equals(playlistId)) {
                continue;
            }

            if (containsTrack(playlist.tracks, track)) {
                return false;
            }

            playlist.tracks.add(track);
            savePlaylists(playlists);
            return true;
        }

        return false;
    }

    public boolean updateTrackInPlaylist(String playlistId, Track updatedTrack) {
        if (playlistId == null || updatedTrack == null || !isValidTrack(updatedTrack)) {
            return false;
        }

        List<LocalPlaylist> playlists = getPlaylists();

        for (LocalPlaylist playlist : playlists) {
            if (!playlist.id.equals(playlistId)) {
                continue;
            }

            for (int index = 0; index < playlist.tracks.size(); index++) {
                if (sameTrack(playlist.tracks.get(index), updatedTrack)) {
                    playlist.tracks.set(index, updatedTrack);
                    savePlaylists(playlists);
                    return true;
                }
            }
        }

        return false;
    }

    public boolean removeTrackFromPlaylist(String playlistId, Track track) {
        if (playlistId == null || track == null) {
            return false;
        }

        List<LocalPlaylist> playlists = getPlaylists();

        for (LocalPlaylist playlist : playlists) {
            if (!playlist.id.equals(playlistId)) {
                continue;
            }

            for (int index = 0; index < playlist.tracks.size(); index++) {
                Track existing = playlist.tracks.get(index);

                if (sameTrack(existing, track)) {
                    playlist.tracks.remove(index);
                    savePlaylists(playlists);
                    return true;
                }
            }
        }

        return false;
    }

    public boolean deletePlaylist(String playlistId) {
        List<LocalPlaylist> playlists = getPlaylists();

        for (int index = 0; index < playlists.size(); index++) {
            if (playlists.get(index).id.equals(playlistId)) {
                playlists.remove(index);
                savePlaylists(playlists);
                return true;
            }
        }

        return false;
    }

    /*
     * Kept for compatibility with the previous single-favorites API.
     * Old callers use a default local playlist named "我的收藏".
     */
    public boolean addToFavorite(Track track) {
        List<LocalPlaylist> playlists = getPlaylists();
        LocalPlaylist favorites = null;

        for (LocalPlaylist playlist : playlists) {
            if (LEGACY_PLAYLIST_NAME.equals(playlist.name)) {
                favorites = playlist;
                break;
            }
        }

        if (favorites == null) {
            favorites = createPlaylist(LEGACY_PLAYLIST_NAME);
        }

        return favorites != null && addTrackToPlaylist(favorites.id, track);
    }

    public List<Track> getFavoriteTracks() {
        for (LocalPlaylist playlist : getPlaylists()) {
            if (LEGACY_PLAYLIST_NAME.equals(playlist.name)) {
                return new ArrayList<>(playlist.tracks);
            }
        }

        return new ArrayList<>();
    }

    /** Adds a track to the pinned notification favorite collection. */
    public boolean addToLiked(Track track) {
        if (track == null || !isValidTrack(track)) {
            return false;
        }

        List<LocalPlaylist> playlists = getPlaylists();
        LocalPlaylist liked = null;
        for (LocalPlaylist playlist : playlists) {
            if (LIKED_PLAYLIST_NAME.equals(playlist.name)) {
                liked = playlist;
                break;
            }
        }

        boolean added = false;
        if (liked == null) {
            liked = new LocalPlaylist(
                    UUID.randomUUID().toString(),
                    LIKED_PLAYLIST_NAME,
                    new ArrayList<>()
            );
            playlists.add(0, liked);
        }
        if (!containsTrack(liked.tracks, track)) {
            liked.tracks.add(track);
            added = true;
        }

        moveLikedPlaylistFirst(playlists);
        savePlaylists(playlists);
        return added;
    }

    public boolean isLiked(Track track) {
        if (track == null) {
            return false;
        }
        for (LocalPlaylist playlist : getPlaylists()) {
            if (LIKED_PLAYLIST_NAME.equals(playlist.name)) {
                return containsTrack(playlist.tracks, track);
            }
        }
        return false;
    }

    public void registerOnChangeListener(
            SharedPreferences.OnSharedPreferenceChangeListener listener
    ) {
        preferences.registerOnSharedPreferenceChangeListener(listener);
    }

    public void unregisterOnChangeListener(
            SharedPreferences.OnSharedPreferenceChangeListener listener
    ) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener);
    }

    private LocalPlaylist findPlaylist(String playlistId) {
        for (LocalPlaylist playlist : getPlaylists()) {
            if (playlist.id.equals(playlistId)) {
                return playlist;
            }
        }

        return null;
    }

    private void migrateLegacyFavoritesIfNeeded() {
        if (preferences.contains(KEY_PLAYLISTS)) {
            return;
        }

        List<Track> legacyTracks = readTracksFromRaw(
                preferences.getString(KEY_FAVORITES, "[]")
        );

        List<LocalPlaylist> playlists = new ArrayList<>();

        if (!legacyTracks.isEmpty()) {
            playlists.add(
                    new LocalPlaylist(
                            UUID.randomUUID().toString(),
                            LEGACY_PLAYLIST_NAME,
                            legacyTracks
                    )
            );
        }

        savePlaylists(playlists);
    }

    private List<Track> readTracksFromRaw(String raw) {
        try {
            return readTracks(new JSONArray(raw == null ? "[]" : raw));
        } catch (Exception exception) {
            exception.printStackTrace();
            return new ArrayList<>();
        }
    }

    private List<Track> readTracks(JSONArray array) {
        List<Track> tracks = new ArrayList<>();

        if (array == null) {
            return tracks;
        }

        for (int index = 0; index < array.length(); index++) {
            try {
                JSONObject object = array.getJSONObject(index);
                Track track = new Track(
                        object.optString("id", ""),
                        object.optString("source", "netease"),
                        object.optString("name", ""),
                        object.optString("artist", ""),
                        object.optString("album", ""),
                        object.optString("picId", ""),
                        object.optString("lyricId", "")
                );

                track.picUrl = object.optString("picUrl", "");
                tracks.add(track);
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        }

        return tracks;
    }

    private void savePlaylists(List<LocalPlaylist> playlists) {
        try {
            JSONArray array = new JSONArray();

            for (LocalPlaylist playlist : playlists) {
                JSONObject object = new JSONObject();
                object.put("id", playlist.id);
                object.put("name", playlist.name);
                object.put("tracks", writeTracks(playlist.tracks));
                array.put(object);
            }

            preferences.edit()
                    .putString(KEY_PLAYLISTS, array.toString())
                    .apply();
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    private void moveLikedPlaylistFirst(List<LocalPlaylist> playlists) {
        for (int index = 0; index < playlists.size(); index++) {
            if (LIKED_PLAYLIST_NAME.equals(playlists.get(index).name)) {
                if (index > 0) {
                    LocalPlaylist liked = playlists.remove(index);
                    playlists.add(0, liked);
                }
                return;
            }
        }
    }

    private JSONArray writeTracks(List<Track> tracks) throws Exception {
        JSONArray array = new JSONArray();

        for (Track track : tracks) {
            JSONObject object = new JSONObject();
            object.put("id", track.id);
            object.put("source", track.source);
            object.put("name", track.name);
            object.put("artist", track.artist);
            object.put("album", track.album);
            object.put("picId", track.picId);
            object.put("lyricId", track.lyricId);
            object.put("picUrl", track.picUrl == null ? "" : track.picUrl);
            array.put(object);
        }

        return array;
    }

    private boolean containsTrack(List<Track> tracks, Track target) {
        for (Track track : tracks) {
            if (sameTrack(track, target)) {
                return true;
            }
        }

        return false;
    }

    private boolean sameTrack(Track first, Track second) {
        return first.id.equals(second.id)
                && first.source.equals(second.source);
    }

    private boolean isValidTrack(Track track) {
        return track.id != null
                && !track.id.isEmpty()
                && track.source != null
                && !track.source.isEmpty();
    }
}
