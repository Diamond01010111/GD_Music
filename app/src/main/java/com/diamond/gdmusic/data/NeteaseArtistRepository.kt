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
    val description: String,
    val hotSongs: List<Track>
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

        // The public artist-search (type=100) can return no matches even for known artists.
        // Search songs first and use the artist ID attached to a matching song.
        val songUrl = searchUrl(query, 1)
        request(songUrl) { root ->
            val songs = root.optJSONObject("result")?.optJSONArray("songs")
                ?: throw IOException("网易云没有返回歌曲搜索结果")
            val normalized = foldChinese(query)
            (0 until songs.length()).asSequence()
                .mapNotNull(songs::optJSONObject)
                .flatMap { song ->
                    val artists = song.optJSONArray("ar") ?: song.optJSONArray("artists")
                    if (artists == null) emptySequence()
                    else (0 until artists.length()).asSequence()
                        .mapNotNull(artists::optJSONObject)
                }
                .firstOrNull { candidate ->
                    val names = mutableListOf(candidate.optString("name"))
                    val aliases = candidate.optJSONArray("alias")
                        ?: candidate.optJSONArray("alia")
                    if (aliases != null) for (index in 0 until aliases.length()) {
                        names.add(aliases.optString(index))
                    }
                    names.any { foldChinese(it) == normalized }
                }?.opt("id")?.toString()?.takeIf { it.isNotBlank() && it != "null" }
                ?: throw IOException("歌曲结果中没有精确匹配的艺人")
        }.onResult { songResult ->
            songResult.onSuccess { id ->
                loadArtist(id, callback)
            }.onFailure {
                // Some artists have no song in the first search page; try artist search once.
                request(searchUrl(query, 100)) { root ->
                    val artists = root.optJSONObject("result")?.optJSONArray("artists")
                        ?: throw IOException("未找到艺人：$name")
                    val normalized = foldChinese(query)
                    (0 until artists.length()).mapNotNull(artists::optJSONObject)
                        .firstOrNull { candidate ->
                            val aliases = candidate.optJSONArray("alias")
                                ?: candidate.optJSONArray("transNames")
                            foldChinese(candidate.optString("name")) == normalized ||
                                (aliases != null && (0 until aliases.length()).any { index ->
                                    foldChinese(aliases.optString(index)) == normalized
                                })
                        }?.opt("id")?.toString()?.takeIf { it.isNotBlank() && it != "null" }
                        ?: throw IOException("未找到精确匹配的艺人：$name")
                }.onResult { artistResult ->
                    artistResult.onSuccess { id -> loadArtist(id, callback) }
                        .onFailure { callback(Result.failure(it)) }
                }
            }
        }
    }

    private fun searchUrl(query: String, type: Int): String =
        "https://music.163.com/api/search/get".toHttpUrl().newBuilder()
            .addQueryParameter("s", query)
            .addQueryParameter("type", type.toString())
            .addQueryParameter("limit", "100")
            .build().toString()

    fun loadArtist(id: String, callback: (Result<NeteaseArtist>) -> Unit) {
        val url = "https://music.163.com/api/v1/artist/$id".toHttpUrl().newBuilder()
            .addQueryParameter("ext", "true")
            .addQueryParameter("top", "50")
            .build().toString()
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
                coverType = when (artist.optInt("type", 0)) {
                    1 -> "男歌手"
                    2 -> "女歌手"
                    3 -> "乐队组合"
                    else -> "未提供"
                },
                description = artist.optString("briefDesc")
                    .ifBlank { artist.optString("description") },
                hotSongs = root.optJSONArray("hotSongs")?.let { songs ->
                    (0 until minOf(songs.length(), 50)).mapNotNull { index ->
                        songs.optJSONObject(index)?.let(::parseTrack)
                    }
                }.orEmpty()
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
                songs.optJSONObject(index)?.let(::parseTrack)
            }
        }.onResult(callback)
    }

    private fun parseTrack(song: JSONObject): Track? {
        val songId = song.opt("id")?.toString().orEmpty()
        if (songId.isBlank() || songId == "null") return null
        val album = song.optJSONObject("al") ?: song.optJSONObject("album")
        val artists = song.optJSONArray("ar") ?: song.optJSONArray("artists")
        val artistNames = if (artists == null) emptyList() else (0 until artists.length())
            .mapNotNull { artists.optJSONObject(it)?.optString("name") }
            .filter(String::isNotBlank)
        return Track(
            songId, "netease", song.optString("name"),
            artistNames.joinToString("、"), album?.optString("name").orEmpty(),
            album?.opt("pic_str")?.toString()
                ?: album?.opt("picId")?.toString().orEmpty(), songId
        ).apply {
            picUrl = album?.optString("picUrl").orEmpty()
                .replace("http://", "https://")
            externalMetadata = true
        }
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
