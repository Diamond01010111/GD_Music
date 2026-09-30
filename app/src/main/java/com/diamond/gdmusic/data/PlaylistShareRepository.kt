package com.diamond.gdmusic.data

import com.diamond.gdmusic.LocalPlaylistStore
import com.diamond.gdmusic.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class PlaylistShareResult(
    val shareId: String,
    val shareUrl: String,
    val expiresAt: Long
)

data class SharedPlaylist(
    val shareId: String,
    val name: String,
    val tracks: List<Track>,
    val expiresAt: Long
)

class PlaylistShareRepository {
    private val client = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun share(playlist: LocalPlaylistStore.LocalPlaylist): Result<PlaylistShareResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (playlist.tracks.isEmpty()) throw IOException("空歌单无法分享")
                val tracks = JSONArray()
                playlist.tracks.forEach { track ->
                    tracks.put(JSONObject().apply {
                        put("id", track.id.orEmpty())
                        put("source", track.source.orEmpty())
                        put("name", track.name.orEmpty())
                        put("artist", track.artist.orEmpty())
                        put("album", track.album.orEmpty())
                        put("picId", track.picId.orEmpty())
                        put("lyricId", track.lyricId.orEmpty())
                        put("picUrl", track.picUrl.orEmpty())
                        put("externalMetadata", track.externalMetadata)
                    })
                }
                val body = JSONObject()
                    .put("playlistName", playlist.name)
                    .put("tracks", tracks)
                    .toString()
                    .toRequestBody(JSON_MEDIA_TYPE)
                val request = Request.Builder().url(PLAYLISTS_URL).post(body).build()
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    val json = runCatching { JSONObject(text) }.getOrNull()
                    if (!response.isSuccessful) {
                        throw IOException(json?.optString("error")?.takeIf { it.isNotBlank() }
                            ?: "分享失败（HTTP ${response.code}）")
                    }
                    val shareId = json?.optString("shareId").orEmpty()
                    if (!SHARE_CODE.matches(shareId)) throw IOException("分享服务返回了无效分享码")
                    PlaylistShareResult(
                        shareId = shareId,
                        shareUrl = "$IMPORT_URL/$shareId",
                        expiresAt = json?.optLong("expiresAt") ?: 0L
                    )
                }
            }
        }

    suspend fun load(value: String): Result<SharedPlaylist> = withContext(Dispatchers.IO) {
        runCatching {
            val shareId = extractShareId(value)
                ?: throw IllegalArgumentException("请输入有效的 10 位分享码")
            val request = Request.Builder().url("$PLAYLISTS_URL/$shareId").get().build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(text) }.getOrNull()
                if (!response.isSuccessful) {
                    throw IOException(json?.optString("error")?.takeIf { it.isNotBlank() }
                        ?: "导入失败（HTTP ${response.code}）")
                }
                if (json?.optString("format") != "gd-music-playlist" || json.optInt("version") != 1) {
                    throw IOException("不支持的歌单分享格式")
                }
                val array = json.optJSONArray("tracks") ?: JSONArray()
                val tracks = buildList {
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        val id = item.optString("id").trim()
                        val source = item.optString("source").trim()
                        val name = item.optString("name").trim()
                        val artist = item.optString("artist").trim()
                        if (id.isBlank() || source.isBlank() || name.isBlank() || artist.isBlank()) continue
                        add(Track(
                            id, source, name, artist,
                            item.optString("album"), item.optString("picId"), item.optString("lyricId")
                        ).apply {
                            picUrl = item.optString("picUrl")
                            externalMetadata = item.optBoolean("externalMetadata", source == "apple")
                        })
                    }
                }.distinctBy { "${it.source}:${it.id}" }
                if (tracks.isEmpty()) throw IOException("分享歌单中没有可导入的歌曲")
                SharedPlaylist(
                    shareId = shareId,
                    name = json.optString("playlistName").trim().ifBlank { "导入歌单" },
                    tracks = tracks,
                    expiresAt = json.optLong("expiresAt")
                )
            }
        }
    }

    companion object {
        const val HOST = "gd-music-share-api.gdmusic.workers.dev"
        private const val BASE_URL = "https://$HOST"
        private const val PLAYLISTS_URL = "$BASE_URL/api/playlists"
        private const val IMPORT_URL = "$BASE_URL/import"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val SHARE_CODE = Regex("[A-Z0-9]{10}")

        fun extractShareId(value: String?): String? {
            val normalized = value.orEmpty().trim().uppercase()
            return SHARE_CODE.findAll(normalized).lastOrNull()?.value
        }
    }
}
