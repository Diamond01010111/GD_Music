package com.diamond.gdmusic.data

import android.os.Handler
import android.os.Looper
import com.diamond.gdmusic.Track
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class NeteasePlaylist(
    val id: String,
    val name: String,
    val coverUrl: String,
    val trackCount: Int,
    val creatorId: String
) {
    fun isCreatedBy(userId: String): Boolean = creatorId == userId
}

class NeteasePlaylistRepository {
    interface PlaylistsCallback {
        fun onSuccess(playlists: List<NeteasePlaylist>)
        fun onError(error: Throwable)
    }

    interface TracksCallback {
        fun onSuccess(tracks: List<Track>)
        fun onError(error: Throwable)
    }

    private val client = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .build()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun loadPublicPlaylists(
        userId: String,
        callback: (Result<List<NeteasePlaylist>>) -> Unit
    ) {
        loadPage(
            userId = userId,
            offset = 0,
            playlists = mutableListOf(),
            callback = callback
        )
    }

    fun loadPublicPlaylistsForJava(
        userId: String,
        callback: PlaylistsCallback
    ) {
        loadPublicPlaylists(userId) { result ->
            result.onSuccess(callback::onSuccess)
                .onFailure(callback::onError)
        }
    }

    fun searchPlaylists(
        keyword: String,
        page: Int,
        limit: Int = SEARCH_PAGE_SIZE,
        callback: (Result<List<NeteasePlaylist>>) -> Unit
    ) {
        if (keyword.isBlank()) {
            callback(Result.failure(IllegalArgumentException("搜索关键词不能为空")))
            return
        }
        val safeLimit = limit.coerceIn(1, 100)
        val url = PLAYLIST_SEARCH_URL.toHttpUrl().newBuilder()
            .addQueryParameter("s", keyword.trim())
            .addQueryParameter("type", "1000")
            .addQueryParameter("limit", safeLimit.toString())
            .addQueryParameter("offset", ((page.coerceAtLeast(1) - 1) * safeLimit).toString())
            .build()

        execute(url.toString()) { root ->
            val array = root.optJSONObject("result")?.optJSONArray("playlists")
                ?: return@execute emptyList()
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.opt("id")?.toString().orEmpty()
                    if (id.isBlank() || id == "null") continue
                    add(
                        NeteasePlaylist(
                            id = id,
                            name = item.optString("name", "未命名歌单"),
                            coverUrl = item.optString("coverImgUrl")
                                .replace("http://", "https://"),
                            trackCount = item.optInt("trackCount", 0),
                            creatorId = item.optJSONObject("creator")
                                ?.opt("userId")
                                ?.toString()
                                .orEmpty()
                        )
                    )
                }
            }
        }.onResult(callback)
    }

    /** Loads the public NetEase chart playlists used by the home-page recommendation row. */
    fun loadToplists(
        limit: Int = TOPLIST_LIMIT,
        callback: (Result<List<NeteasePlaylist>>) -> Unit
    ) {
        execute(TOPLIST_URL) { root ->
            val array = root.optJSONArray("list")
                ?: throw IOException("网易云没有返回榜单数据")
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.opt("id")?.toString().orEmpty()
                    if (id.isBlank() || id == "null") continue
                    add(
                        NeteasePlaylist(
                            id = id,
                            name = item.optString("name", "网易云榜单"),
                            coverUrl = item.optString("coverImgUrl")
                                .replace("http://", "https://"),
                            trackCount = item.optInt("trackCount", 0),
                            creatorId = item.optJSONObject("creator")
                                ?.opt("userId")
                                ?.toString()
                                .orEmpty()
                        )
                    )
                }
            }.take(limit.coerceIn(1, TOPLIST_LIMIT))
        }.onResult(callback)
    }

    fun loadPlaylistTracks(
        playlistId: String,
        callback: (Result<List<Track>>) -> Unit
    ) {
        val modernUrl = PLAYLIST_DETAIL_URL.toHttpUrl().newBuilder()
            .addQueryParameter("id", playlistId)
            .addQueryParameter("n", MAX_PLAYLIST_TRACKS.toString())
            .addQueryParameter("s", "0")
            .build()

        loadPlaylistSnapshot(modernUrl.toString()) { modernResult ->
            modernResult.onSuccess { snapshot ->
                loadAllTrackDetails(snapshot, callback)
            }.onFailure {
                val legacyUrl = LEGACY_PLAYLIST_DETAIL_URL.toHttpUrl().newBuilder()
                    .addQueryParameter("id", playlistId)
                    .build()
                loadPlaylistSnapshot(legacyUrl.toString()) { legacyResult ->
                    legacyResult.onSuccess { snapshot ->
                        loadAllTrackDetails(snapshot, callback)
                    }.onFailure { error ->
                        callback(Result.failure(error))
                    }
                }
            }
        }
    }

    fun loadPlaylistTracksForJava(
        playlistId: String,
        callback: TracksCallback
    ) {
        loadPlaylistTracks(playlistId) { result ->
            result.onSuccess(callback::onSuccess)
                .onFailure(callback::onError)
        }
    }

    private fun loadPlaylistSnapshot(
        url: String,
        callback: (Result<PlaylistSnapshot>) -> Unit
    ) {
        execute(url) { root ->
            val playlist = root.optJSONObject("playlist")
                ?: root.optJSONObject("result")
                ?: throw IOException("网易云没有返回歌单详情")
            val tracks = buildList {
                val array = playlist.optJSONArray("tracks")
                if (array != null) {
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        parseTrack(item)?.let(::add)
                    }
                }
            }
            val trackIds = buildList {
                val array = playlist.optJSONArray("trackIds")
                if (array != null) {
                    for (index in 0 until array.length()) {
                        val id = array.optJSONObject(index)
                            ?.opt("id")
                            ?.toString()
                            .orEmpty()
                        if (id.isNotBlank() && id != "null") add(id)
                    }
                }
            }.ifEmpty { tracks.map { track -> track.id } }

            PlaylistSnapshot(trackIds = trackIds, tracks = tracks)
        }.onResult(callback)
    }

    private fun loadAllTrackDetails(
        snapshot: PlaylistSnapshot,
        callback: (Result<List<Track>>) -> Unit
    ) {
        if (snapshot.trackIds.isEmpty()) {
            callback(Result.success(snapshot.tracks))
            return
        }

        val tracksById = snapshot.tracks.associateByTo(linkedMapOf()) { it.id }
        val missingIds = snapshot.trackIds.filterNot(tracksById::containsKey)
        if (missingIds.isEmpty()) {
            callback(Result.success(orderedTracks(snapshot.trackIds, tracksById)))
            return
        }

        loadSongDetailBatches(
            ids = missingIds,
            offset = 0,
            tracksById = tracksById
        ) { result ->
            result.onSuccess {
                callback(Result.success(orderedTracks(snapshot.trackIds, tracksById)))
            }.onFailure { error ->
                callback(Result.failure(error))
            }
        }
    }

    private fun loadSongDetailBatches(
        ids: List<String>,
        offset: Int,
        tracksById: MutableMap<String, Track>,
        callback: (Result<Unit>) -> Unit
    ) {
        if (offset >= ids.size) {
            callback(Result.success(Unit))
            return
        }

        val batch = ids.drop(offset).take(SONG_DETAIL_BATCH_SIZE)
        loadSongDetailBatch(batch) { result ->
            result.onSuccess { tracks ->
                tracks.forEach { track -> tracksById[track.id] = track }
                loadSongDetailBatches(
                    ids,
                    offset + batch.size,
                    tracksById,
                    callback
                )
            }.onFailure { error ->
                callback(Result.failure(error))
            }
        }
    }

    private fun loadSongDetailBatch(
        ids: List<String>,
        callback: (Result<List<Track>>) -> Unit
    ) {
        val legacyUrl = SONG_DETAIL_URL.toHttpUrl().newBuilder()
            .addQueryParameter("ids", ids.joinToString(prefix = "[", postfix = "]"))
            .build()

        execute(legacyUrl.toString(), ::parseSongs).onResult { legacyResult ->
            legacyResult.onSuccess { callback(Result.success(it)) }
                .onFailure {
                    val body = FormBody.Builder()
                        .add(
                            "c",
                            ids.joinToString(prefix = "[", postfix = "]") { id ->
                                "{\"id\":$id}"
                            }
                        )
                        .build()
                    val request = baseRequestBuilder(SONG_DETAIL_V3_URL)
                        .post(body)
                        .build()
                    executeRequest(request, ::parseSongs).onResult(callback)
                }
        }
    }

    private fun parseSongs(root: JSONObject): List<Track> {
        val array = root.optJSONArray("songs")
            ?: throw IOException("网易云没有返回歌曲详情")
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                parseTrack(item)?.let(::add)
            }
        }
    }

    private fun orderedTracks(
        ids: List<String>,
        tracksById: Map<String, Track>
    ): List<Track> = ids.mapNotNull(tracksById::get)

    private fun loadPage(
        userId: String,
        offset: Int,
        playlists: MutableList<NeteasePlaylist>,
        callback: (Result<List<NeteasePlaylist>>) -> Unit
    ) {
        val url = USER_PLAYLIST_URL.toHttpUrl().newBuilder()
            .addQueryParameter("uid", userId)
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .addQueryParameter("offset", offset.toString())
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Referer", "https://music.163.com/")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                deliver(callback, Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use {
                        val body = it.body?.string().orEmpty()
                        if (!it.isSuccessful) {
                            throw IOException("网易云请求失败：HTTP ${it.code}")
                        }

                        val root = JSONObject(body)
                        val code = root.optInt("code", it.code)
                        if (code != 200) {
                            val message = root.optString("message", "接口返回错误")
                            throw IOException("网易云请求失败：$message（$code）")
                        }

                        val array = root.optJSONArray("playlist")
                            ?: throw IOException("网易云没有返回歌单数据")

                        for (index in 0 until array.length()) {
                            val item = array.optJSONObject(index) ?: continue
                            val creator = item.optJSONObject("creator")
                            val playlistId = item.opt("id")?.toString().orEmpty()

                            if (playlistId.isBlank()) continue

                            playlists += NeteasePlaylist(
                                id = playlistId,
                                name = item.optString("name", "未命名歌单"),
                                coverUrl = item.optString("coverImgUrl")
                                    .replace("http://", "https://"),
                                trackCount = item.optInt("trackCount", 0),
                                creatorId = creator?.opt("userId")?.toString().orEmpty()
                            )
                        }

                        val hasMore = root.optBoolean("more", false)
                        if (hasMore && array.length() > 0) {
                            loadPage(
                                userId = userId,
                                offset = offset + array.length(),
                                playlists = playlists,
                                callback = callback
                            )
                        } else {
                            deliver(
                                callback,
                                Result.success(playlists.distinctBy { playlist -> playlist.id })
                            )
                        }
                    }
                } catch (e: Exception) {
                    deliver(callback, Result.failure(e))
                }
            }
        })
    }

    private fun <T> execute(
        url: String,
        transform: (JSONObject) -> T
    ): PendingResult<T> {
        return executeRequest(baseRequestBuilder(url).get().build(), transform)
    }

    private fun <T> executeRequest(
        request: Request,
        transform: (JSONObject) -> T
    ): PendingResult<T> {
        return PendingResult { callback ->
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    deliverResult(callback, Result.failure(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use {
                            val body = it.body?.string().orEmpty()
                            if (!it.isSuccessful) {
                                throw IOException("网易云请求失败：HTTP ${it.code}")
                            }

                            val root = JSONObject(body)
                            val code = root.optInt("code", it.code)
                            if (code != 200) {
                                val message = root.optString("message", "接口返回错误")
                                throw IOException("网易云请求失败：$message（$code）")
                            }
                            deliverResult(callback, Result.success(transform(root)))
                        }
                    } catch (e: Exception) {
                        deliverResult(callback, Result.failure(e))
                    }
                }
            })
        }
    }

    private fun baseRequestBuilder(url: String): Request.Builder {
        return Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Referer", "https://music.163.com/")
            .header("User-Agent", USER_AGENT)
    }

    private fun parseTrack(item: JSONObject): Track? {
        val id = item.opt("id")?.toString().orEmpty()
        if (id.isBlank() || id == "null") return null

        val album = item.optJSONObject("al") ?: item.optJSONObject("album")
        val artists = item.optJSONArray("ar") ?: item.optJSONArray("artists")
        val artistNames = buildList {
            if (artists != null) {
                for (index in 0 until artists.length()) {
                    val name = artists.optJSONObject(index)?.optString("name").orEmpty()
                    if (name.isNotBlank()) add(name)
                }
            }
        }
        val picId = album?.opt("pic_str")?.toString()
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?: album?.opt("picId")?.toString()
                ?.takeIf { it.isNotBlank() && it != "null" }
            ?: album?.opt("pic")?.toString().orEmpty()

        return Track(
            id,
            "netease",
            item.optString("name", "未知歌曲"),
            artistNames.joinToString("、"),
            album?.optString("name").orEmpty(),
            picId,
            id
        ).apply {
            picUrl = album?.optString("picUrl").orEmpty()
                .replace("http://", "https://")
            externalMetadata = true
        }
    }

    private fun deliver(
        callback: (Result<List<NeteasePlaylist>>) -> Unit,
        result: Result<List<NeteasePlaylist>>
    ) {
        mainHandler.post {
            callback(result)
        }
    }

    private fun <T> deliverResult(
        callback: (Result<T>) -> Unit,
        result: Result<T>
    ) {
        mainHandler.post { callback(result) }
    }

    private class PendingResult<T>(
        private val starter: ((Result<T>) -> Unit) -> Unit
    ) {
        fun onResult(callback: (Result<T>) -> Unit) {
            starter(callback)
        }
    }

    private data class PlaylistSnapshot(
        val trackIds: List<String>,
        val tracks: List<Track>
    )

    private companion object {
        const val USER_PLAYLIST_URL = "https://music.163.com/api/user/playlist/"
        const val PLAYLIST_SEARCH_URL = "https://music.163.com/api/search/get"
        const val TOPLIST_URL = "https://music.163.com/api/toplist"
        const val PLAYLIST_DETAIL_URL = "https://music.163.com/api/v6/playlist/detail"
        const val LEGACY_PLAYLIST_DETAIL_URL = "https://music.163.com/api/playlist/detail"
        const val SONG_DETAIL_URL = "https://music.163.com/api/song/detail/"
        const val SONG_DETAIL_V3_URL = "https://music.163.com/api/v3/song/detail"
        const val PAGE_SIZE = 100
        const val MAX_PLAYLIST_TRACKS = 100000
        const val SONG_DETAIL_BATCH_SIZE = 200
        const val SEARCH_PAGE_SIZE = 30
        const val TOPLIST_LIMIT = 20
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
    }
}
