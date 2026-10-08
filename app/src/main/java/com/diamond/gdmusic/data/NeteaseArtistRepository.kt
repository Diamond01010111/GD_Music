package com.diamond.gdmusic.data

import android.os.Handler
import android.os.Looper
import com.diamond.gdmusic.GdMusicApi
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

    fun findArtist(
        name: String,
        songTitle: String,
        neteaseSongId: String?,
        callback: (Result<NeteaseArtist>) -> Unit
    ) {
        val artistName = foldChinese(name.trim())
        if (artistName.isBlank() || songTitle.isBlank()) {
            callback(Result.failure(IllegalArgumentException("歌曲或艺人名称不能为空")))
            return
        }

        fun resolveFromSong(songId: String) {
            val url = "https://music.163.com/api/song/detail/".toHttpUrl().newBuilder()
                .addQueryParameter("ids", "[$songId]")
                .build().toString()
            request(url) { root ->
                val song = root.optJSONArray("songs")?.optJSONObject(0)
                    ?: throw IOException("网易云没有返回歌曲详情")
                val artists = song.optJSONArray("ar") ?: song.optJSONArray("artists")
                    ?: throw IOException("歌曲没有艺人信息")
                (0 until artists.length()).mapNotNull(artists::optJSONObject)
                    .firstOrNull { candidate ->
                        val names = mutableListOf(candidate.optString("name"))
                        val aliases = candidate.optJSONArray("alias")
                            ?: candidate.optJSONArray("alia")
                        if (aliases != null) for (index in 0 until aliases.length()) {
                            names.add(aliases.optString(index))
                        }
                        names.any { foldChinese(it) == artistName }
                    }?.opt("id")?.toString()?.takeIf { it.isNotBlank() && it != "null" }
                    ?: throw IOException("歌曲中未找到艺人：$name")
            }.onResult { result ->
                result.onSuccess { id -> loadArtist(id, callback) }
                    .onFailure { callback(Result.failure(it)) }
            }
        }

        if (neteaseSongId?.all(Char::isDigit) == true) {
            resolveFromSong(neteaseSongId)
            return
        }

        // Other sources have different song IDs. Ask the existing GD NetEase search for an ID;
        // only accept a song whose title and artist both match the selected track.
        GdMusicApi().searchTracks(songTitle, "netease", 15, 1,
            object : GdMusicApi.SearchCallback {
                override fun onSuccess(tracks: List<Track>) {
                    val title = foldChinese(songTitle)
                    val match = tracks.firstOrNull { track ->
                        foldChinese(track.name) == title &&
                            track.artist.split(Regex("[、,，/&]")).any { artist ->
                                foldChinese(artist.trim()) == artistName
                            }
                    }
                    if (match == null) {
                        handler.post {
                            callback(Result.failure(IOException("网易云中未找到匹配的艺人歌曲")))
                        }
                    } else {
                        resolveFromSong(match.id)
                    }
                }

                override fun onError(error: Exception) {
                    handler.post { callback(Result.failure(error)) }
                }
            }
        )
    }

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
