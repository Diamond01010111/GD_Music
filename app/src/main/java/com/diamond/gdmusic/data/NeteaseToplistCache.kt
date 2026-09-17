package com.diamond.gdmusic.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Cached public chart data and the five chart playlists most recently opened from Home. */
object NeteaseToplistCache {
    private const val PREFERENCES_NAME = "netease_toplist_preferences"
    private const val TOPLISTS_KEY = "cached_toplists"
    private const val LAST_REFRESH_KEY = "last_refresh"
    private const val RECENTLY_BROWSED_KEY = "recently_browsed"
    private const val REFRESH_INTERVAL_MS = 24L * 60L * 60L * 1000L
    private const val MAX_RECENTLY_BROWSED = 5

    fun readToplists(context: Context): List<NeteasePlaylist> =
        readPlaylists(preferences(context).getString(TOPLISTS_KEY, "[]"))

    fun saveToplists(context: Context, playlists: List<NeteasePlaylist>) {
        preferences(context).edit()
            .putString(TOPLISTS_KEY, writePlaylists(playlists))
            .putLong(LAST_REFRESH_KEY, System.currentTimeMillis())
            .apply()
    }

    fun shouldRefresh(context: Context): Boolean {
        val lastRefresh = preferences(context).getLong(LAST_REFRESH_KEY, 0L)
        return lastRefresh == 0L || System.currentTimeMillis() - lastRefresh >= REFRESH_INTERVAL_MS
    }

    fun recentlyBrowsed(context: Context): List<NeteasePlaylist> =
        readPlaylists(preferences(context).getString(RECENTLY_BROWSED_KEY, "[]"))

    fun recordBrowsing(context: Context, playlist: NeteasePlaylist) {
        val recent = listOf(playlist) + recentlyBrowsed(context).filterNot { it.id == playlist.id }
        preferences(context).edit()
            .putString(RECENTLY_BROWSED_KEY, writePlaylists(recent.take(MAX_RECENTLY_BROWSED)))
            .apply()
    }

    private fun readPlaylists(json: String?): List<NeteasePlaylist> = try {
        val array = JSONArray(json ?: "[]")
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id")
                if (id.isBlank()) continue
                add(
                    NeteasePlaylist(
                        id = id,
                        name = item.optString("name", "未命名歌单"),
                        coverUrl = item.optString("coverUrl"),
                        trackCount = item.optInt("trackCount", 0),
                        creatorId = item.optString("creatorId")
                    )
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun writePlaylists(playlists: List<NeteasePlaylist>): String {
        val array = JSONArray()
        playlists.forEach { playlist ->
            array.put(
                JSONObject()
                    .put("id", playlist.id)
                    .put("name", playlist.name)
                    .put("coverUrl", playlist.coverUrl)
                    .put("trackCount", playlist.trackCount)
                    .put("creatorId", playlist.creatorId)
            )
        }
        return array.toString()
    }

    private fun preferences(context: Context) = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )
}
