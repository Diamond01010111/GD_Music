package com.diamond.gdmusic.data

import android.content.Context
import com.diamond.gdmusic.LocalPlaylistStore
import com.diamond.gdmusic.Track
import org.json.JSONArray
import org.json.JSONObject

data class RecentPlaylist(
    val id: String,
    val name: String,
    val coverUrl: String,
    val trackCount: Int,
    val type: Type = Type.NETEASE
) {
    enum class Type { NETEASE, LOCAL }
}

/**
 * Persists only tracks that have actually started playing.  Keeping this separate from the
 * search history prevents merely viewing a result from appearing as a recent play.
 */
class PlaybackHistoryStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun recentTracks(): List<Track> = readArray(TRACKS_KEY).mapNotNull(::trackFromJson)

    fun recentPlaylists(): List<RecentPlaylist> = readArray(PLAYLISTS_KEY)
        .mapNotNull(::playlistFromJson)

    fun recordTrack(track: Track) {
        val key = trackKey(track)
        val updated = listOf(track) + recentTracks().filterNot { trackKey(it) == key }
        writeArray(TRACKS_KEY, updated.take(MAX_TRACKS).map(::trackToJson))
    }

    fun recordPlaylist(playlist: NeteasePlaylist) {
        val recent = RecentPlaylist(
            id = playlist.id,
            name = playlist.name,
            coverUrl = playlist.coverUrl,
            trackCount = playlist.trackCount,
            type = RecentPlaylist.Type.NETEASE
        )
        saveRecentPlaylist(recent)
    }

    fun recordLocalPlaylist(playlist: LocalPlaylistStore.LocalPlaylist) {
        val recent = RecentPlaylist(
            id = playlist.id,
            name = playlist.name,
            coverUrl = playlist.coverTrack?.picUrl.orEmpty(),
            trackCount = playlist.tracks.size,
            type = RecentPlaylist.Type.LOCAL
        )
        saveRecentPlaylist(recent)
    }

    private fun saveRecentPlaylist(recent: RecentPlaylist) {
        val updated = listOf(recent) + recentPlaylists().filterNot {
            it.id == recent.id && it.type == recent.type
        }
        writeArray(PLAYLISTS_KEY, updated.take(MAX_PLAYLISTS).map(::playlistToJson))
    }

    private fun readArray(key: String): List<JSONObject> = runCatching {
        val array = JSONArray(preferences.getString(key, "[]"))
        List(array.length()) { index -> array.optJSONObject(index) }.filterNotNull()
    }.getOrDefault(emptyList())

    private fun writeArray(key: String, values: List<JSONObject>) {
        val array = JSONArray()
        values.forEach(array::put)
        preferences.edit().putString(key, array.toString()).apply()
    }

    private fun trackToJson(track: Track) = JSONObject()
        .put("id", track.id)
        .put("source", track.source)
        .put("name", track.name)
        .put("artist", track.artist)
        .put("album", track.album)
        .put("picId", track.picId)
        .put("lyricId", track.lyricId)
        .put("picUrl", track.picUrl)
        .put("externalMetadata", track.externalMetadata)
        .put("requestedBitrate", track.requestedBitrate)

    private fun trackFromJson(value: JSONObject): Track? {
        val id = value.optString("id")
        val name = value.optString("name")
        if (id.isBlank() || name.isBlank()) return null
        return Track(
            id,
            value.optString("source"),
            name,
            value.optString("artist"),
            value.optString("album"),
            value.optString("picId"),
            value.optString("lyricId")
        ).apply {
            picUrl = value.optString("picUrl")
            externalMetadata = value.optBoolean("externalMetadata", false)
            requestedBitrate = value.optInt("requestedBitrate", 0)
        }
    }

    private fun playlistToJson(playlist: RecentPlaylist) = JSONObject()
        .put("id", playlist.id)
        .put("name", playlist.name)
        .put("coverUrl", playlist.coverUrl)
        .put("trackCount", playlist.trackCount)
        .put("type", playlist.type.name)

    private fun playlistFromJson(value: JSONObject): RecentPlaylist? {
        val id = value.optString("id")
        val name = value.optString("name")
        if (id.isBlank() || name.isBlank()) return null
        return RecentPlaylist(
            id = id,
            name = name,
            coverUrl = value.optString("coverUrl"),
            trackCount = value.optInt("trackCount", 0),
            type = runCatching {
                RecentPlaylist.Type.valueOf(value.optString("type", "NETEASE"))
            }.getOrDefault(RecentPlaylist.Type.NETEASE)
        )
    }

    private fun trackKey(track: Track): String = "${track.source}:${track.id}"

    private companion object {
        const val PREFERENCES_NAME = "playback_history"
        const val TRACKS_KEY = "recent_tracks"
        const val PLAYLISTS_KEY = "recent_playlists"
        const val MAX_TRACKS = 50
        const val MAX_PLAYLISTS = 5
    }
}
