package com.diamond.gdmusic.data

import android.os.Handler
import android.os.Looper
import com.diamond.gdmusic.Track
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.text.Normalizer
import java.util.concurrent.TimeUnit

data class NeteaseArtist(
    val id: String,
    val name: String,
    val aliases: List<String>,
    val coverUrl: String,
    val coverType: String,
    val description: String
)

data class NeteaseAlbum(
    val id: String,
    val name: String,
    val coverUrl: String,
    val songCount: Int
)

/** NetEase supplies metadata only. Playback of these tracks is resolved through GD API. */
class NeteaseArtistRepository {
    private val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
    private val handler = Handler(Looper.getMainLooper())

    fun findArtist(name: String, callback: (Result<NeteaseArtist>) -> Unit) {
        val query = com.diamond.gdmusic.ChineseText.simplified(
            Normalizer.normalize(name.trim(), Normalizer.Form.NFKC)
        )
        if (query.isBlank()) {
            callback(Result.failure(IllegalArgumentException("艺人名称不能为空")))
            return
        }
        val url = "https://music.163.com/api/search/get".toHttpUrl().newBuilder()
            .addQueryParameter("s", query)
            .addQueryParameter("type", "100")
            .addQueryParameter("limit", "30")
            .build().toString()
        request(url) { root ->
            val items = root.optJSONObject("result")?.optJSONArray("artists")
                ?: throw IOException("未找到艺人")
            val normalized = foldChinese(query)
            val matched = (0 until items.length()).mapNotNull(items::optJSONObject)
                .firstOrNull { candidate ->
                    val names = buildList {
                        add(candidate.optString("name"))
                        val aliases = candidate.optJSONArray("alias")
                            ?: candidate.optJSONArray("transNames")
                        if (aliases != null) for (i in 0 until aliases.length()) {
                            add(aliases.optString(i))
                        }
                    }
                    names.any { foldChinese(it) == normalized }
                } ?: throw IOException("未找到精确匹配的艺人：$name")
            matched.opt("id")?.toString()?.takeIf { it != "null" && it.isNotBlank() }
                ?: throw IOException("艺人缺少 ID")
        }.onResult { result ->
            result.onSuccess { id -> loadArtist(id, callback) }
                .onFailure { callback(Result.failure(it)) }
        }
    }

    fun loadArtist(id: String, callback: (Result<NeteaseArtist>) -> Unit) {
        val url = "https://music.163.com/api/artist/$id"
        request(url) { root ->
            val data = root.optJSONObject("data")
            val artist = data?.optJSONObject("artist") ?: root.optJSONObject("artist")
                ?: throw IOException("网易云没有返回艺人详情")
            val aliases = artist.optJSONArray("alias") ?: artist.optJSONArray("aliases")
            val names = if (aliases == null) emptyList() else (0 until aliases.length())
                .map { aliases.optString(it) }.filter(String::isNotBlank)
            NeteaseArtist(
                id = id,
                name = artist.optString("name"),
                aliases = names,
                coverUrl = artist.optString("cover").ifBlank { artist.optString("picUrl") }
                    .replace("http://", "https://"),
                coverType = artist.optString("type").ifBlank { "艺人封面" },
                description = artist.optString("briefDesc")
                    .ifBlank { artist.optString("description") }
            )
        }.onResult(callback)
    }

    fun loadAlbums(id: String, offset: Int, callback: (Result<List<NeteaseAlbum>>) -> Unit) {
        val url = "https://music.163.com/api/artist/albums/$id".toHttpUrl().newBuilder()
            .addQueryParameter("limit", "30")
            .addQueryParameter("offset", offset.coerceAtLeast(0).toString())
            .build().toString()
        request(url) { root ->
            val albums = root.optJSONArray("hotAlbums") ?: root.optJSONArray("albums")
                ?: throw IOException("网易云没有返回艺人专辑")
            (0 until albums.length()).mapNotNull { index ->
                val item = albums.optJSONObject(index) ?: return@mapNotNull null
                val albumId = item.opt("id")?.toString().orEmpty()
                if (albumId.isBlank() || albumId == "null") return@mapNotNull null
                NeteaseAlbum(
                    albumId,
                    item.optString("name"),
                    item.optString("picUrl").replace("http://", "https://"),
                    item.optInt("size")
                )
            }
        }.onResult(callback)
    }

    fun loadAlbumTracks(id: String, callback: (Result<List<Track>>) -> Unit) {
        request("https://music.163.com/api/v1/album/$id") { root ->
            val songs = root.optJSONArray("songs")
                ?: throw IOException("网易云没有返回专辑歌曲")
            (0 until songs.length()).mapNotNull { index ->
                val song = songs.optJSONObject(index) ?: return@mapNotNull null
                val songId = song.opt("id")?.toString().orEmpty()
                if (songId.isBlank() || songId == "null") return@mapNotNull null
                val album = song.optJSONObject("al") ?: song.optJSONObject("album")
                val artists = song.optJSONArray("ar") ?: song.optJSONArray("artists")
                val artistNames = if (artists == null) emptyList() else (0 until artists.length())
                    .mapNotNull { artists.optJSONObject(it)?.optString("name") }
                    .filter(String::isNotBlank)
                Track(
                    songId, "netease", song.optString("name"),
                    artistNames.joinToString("、"), album?.optString("name").orEmpty(),
                    album?.opt("picId")?.toString().orEmpty(), songId
                ).apply {
                    picUrl = album?.optString("picUrl").orEmpty()
                        .replace("http://", "https://")
                    externalMetadata = true
                }
            }
        }.onResult(callback)
    }

    private fun <T> request(url: String, parse: (JSONObject) -> T): Pending<T> = Pending { callback ->
        val request = Request.Builder().url(url)
            .header("Referer", "https://music.163.com/")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
            .get().build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                handler.post { callback(Result.failure(error)) }
            }
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        if (!it.isSuccessful) throw IOException("网易云请求失败：HTTP ${it.code}")
                        val root = JSONObject(it.body?.string().orEmpty())
                        if (root.optInt("code", 200) != 200) {
                            throw IOException("网易云请求失败：${root.optString("message")}")
                        }
                        parse(root)
                    }
                }
                handler.post { callback(result) }
            }
        })
    }

    private class Pending<T>(val start: ((Result<T>) -> Unit) -> Unit) {
        fun onResult(callback: (Result<T>) -> Unit) = start(callback)
    }

    private fun foldChinese(value: String): String {
        return com.diamond.gdmusic.ChineseText.simplified(
            Normalizer.normalize(value, Normalizer.Form.NFKC)
        ).lowercase()
    }
}
