package com.diamond.gdmusic

import android.Manifest
import android.content.ComponentName
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.diamond.gdmusic.model.SearchCategory
import com.diamond.gdmusic.data.NeteasePlaylist
import com.diamond.gdmusic.data.NeteasePlaylistRepository
import com.diamond.gdmusic.ui.MusicApp
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap

@UnstableApi
class ComposeMainActivity : ComponentActivity() {

    private lateinit var api: GdMusicApi
    private lateinit var localPlaylistStore: LocalPlaylistStore
    private lateinit var neteaseRepository: NeteasePlaylistRepository
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController by mutableStateOf<MediaController?>(null)
    private var lastRootBackAt = 0L
    private val lyricCache = ConcurrentHashMap<String, Track>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        api = GdMusicApi()
        localPlaylistStore = LocalPlaylistStore(this)
        neteaseRepository = NeteasePlaylistRepository()
        requestNotificationPermission()
        connectToPlaybackService()

        setContent {
            var darkMode by remember {
                mutableStateOf(PlaybackPreferences.darkMode(this))
            }
            var defaultBitrate by remember {
                mutableStateOf(PlaybackPreferences.defaultBitrate(this))
            }
            var showLyricTranslation by remember {
                mutableStateOf(PlaybackPreferences.showLyricTranslation(this))
            }
            MaterialTheme(
                colorScheme = if (darkMode) darkColorScheme() else lightColorScheme()
            ) {
                val controller = mediaController
                var currentTrack by remember { mutableStateOf<Track?>(null) }
                var artworkUrl by remember { mutableStateOf("") }
                var artworkTrackKey by remember { mutableStateOf("") }
                var isPlaying by remember { mutableStateOf(false) }
                var playMode by remember { mutableStateOf(PlaybackMode.LIST_LOOP) }
                var queue by remember { mutableStateOf<List<Track>>(emptyList()) }
                var currentIndex by remember { mutableStateOf(-1) }
                var playbackProgress by remember { mutableStateOf(0f) }
                var playbackPositionMs by remember { mutableStateOf(0L) }
                var playbackDurationMs by remember { mutableStateOf(0L) }
                var localPlaylists by remember {
                    mutableStateOf(localPlaylistStore.playlists)
                }

                DisposableEffect(Unit) {
                    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                        runOnUiThread {
                            localPlaylists = localPlaylistStore.playlists
                        }
                    }
                    localPlaylistStore.registerOnChangeListener(listener)
                    onDispose {
                        localPlaylistStore.unregisterOnChangeListener(listener)
                    }
                }

                fun syncPlayerState() {
                    if (controller == null) {
                        currentTrack = null
                        artworkUrl = ""
                        isPlaying = false
                        queue = emptyList()
                        currentIndex = -1
                        playbackProgress = 0f
                        playbackPositionMs = 0L
                        playbackDurationMs = 0L
                        return
                    }

                    val nextTrack = TrackMediaItem.toTrack(controller.currentMediaItem)
                    val nextTrackKey = nextTrack?.let { "${it.source}:${it.id}" }.orEmpty()
                    currentTrack = nextTrack
                    if (nextTrackKey != artworkTrackKey) {
                        artworkTrackKey = nextTrackKey
                        artworkUrl = nextTrack?.picUrl.orEmpty()
                    } else if (!nextTrack?.picUrl.isNullOrBlank()) {
                        artworkUrl = nextTrack?.picUrl.orEmpty()
                    }
                    isPlaying = controller.isPlaying
                    playMode = playbackMode(controller)
                    queue = (0 until controller.mediaItemCount).mapNotNull { index ->
                        TrackMediaItem.toTrack(controller.getMediaItemAt(index))
                    }
                    currentIndex = if (controller.mediaItemCount == 0) {
                        -1
                    } else {
                        controller.currentMediaItemIndex
                    }
                }

                LaunchedEffect(Unit) {
                    refreshMissingLocalArtwork {
                        localPlaylists = localPlaylistStore.playlists
                    }
                }

                DisposableEffect(controller) {
                    if (controller == null) {
                        onDispose { }
                    } else {
                        val listener = object : Player.Listener {
                            override fun onEvents(
                                player: Player,
                                events: Player.Events
                            ) {
                                syncPlayerState()
                            }
                        }
                        controller.addListener(listener)
                        syncPlayerState()
                        onDispose {
                            controller.removeListener(listener)
                        }
                    }
                }

                LaunchedEffect(controller) {
                    val connectedController = controller ?: return@LaunchedEffect
                    while (true) {
                        val duration = connectedController.duration
                        val position = connectedController.currentPosition
                        playbackProgress = if (duration > 0L && position >= 0L) {
                            (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        playbackPositionMs = position.coerceAtLeast(0L)
                        playbackDurationMs = duration.coerceAtLeast(0L)
                        delay(500)
                    }
                }

                MusicApp(
                    nowPlayingTrack = currentTrack,
                    artworkUrl = artworkUrl,
                    isPlaying = isPlaying,
                    playMode = playMode,
                    queue = queue,
                    currentIndex = currentIndex,
                    playbackProgress = playbackProgress,
                    playbackPositionMs = playbackPositionMs,
                    playbackDurationMs = playbackDurationMs,
                    localPlaylists = localPlaylists,
                    defaultBitrate = defaultBitrate,
                    darkMode = darkMode,
                    showLyricTranslation = showLyricTranslation,

                    onRequestSearch = { keyword, category, source, page, callback ->
                        requestTracks(keyword, category, source, page, callback)
                    },

                    onRequestLyrics = ::requestLyrics,
                    onSwitchCurrentSource = ::switchCurrentSource,
                    onChangeCurrentQuality = ::changeCurrentQuality,

                    onRequestNeteasePlaylists = ::requestNeteasePlaylists,
                    onImportNeteasePlaylist = { playlist, tracks, callback ->
                        importNeteasePlaylist(playlist, tracks) { result ->
                            if (result.isSuccess) {
                                localPlaylists = localPlaylistStore.playlists
                            }
                            callback(result)
                        }
                    },

                    onDefaultBitrateChange = { bitrate ->
                        PlaybackPreferences.setDefaultBitrate(this, bitrate)
                        defaultBitrate = AudioQuality.fromBitrate(bitrate).bitrate
                    },
                    onDarkModeChange = { enabled ->
                        PlaybackPreferences.setDarkMode(this, enabled)
                        darkMode = enabled
                    },
                    onShowLyricTranslationChange = { enabled ->
                        PlaybackPreferences.setShowLyricTranslation(this, enabled)
                        showLyricTranslation = enabled
                    },

                    onPlayResults = ::playTracks,

                    onRecommendedSongClick = ::searchAndPlayFirst,

                    onPlayPause = {
                        controllerOrWarn()?.let { player ->
                            if (player.isPlaying) player.pause() else player.play()
                        }
                    },

                    onSwitchPlayMode = ::switchPlaybackMode,

                    onSkipPrevious = {
                        controllerOrWarn()?.seekToPreviousMediaItem()
                    },

                    onSkipNext = {
                        controllerOrWarn()?.seekToNext()
                    },

                    onSeekTo = { positionMs ->
                        controllerOrWarn()?.seekTo(positionMs.coerceAtLeast(0L))
                    },

                    onQueueTrackClick = { index ->
                        controllerOrWarn()?.let { player ->
                            if (index in 0 until player.mediaItemCount) {
                                player.seekToDefaultPosition(index)
                                player.play()
                            }
                        }
                    },

                    onRemoveQueueTrack = { index ->
                        controllerOrWarn()?.let { player ->
                            if (index in 0 until player.mediaItemCount) {
                                player.removeMediaItem(index)
                                showToast("已从播放列表删除")
                            }
                        }
                    },

                    onClearQueue = {
                        controllerOrWarn()?.let { player ->
                            player.stop()
                            player.clearMediaItems()
                            showToast("播放列表已清空")
                        }
                    },

                    onPlayNext = { track ->
                        controllerOrWarn()?.let { player ->
                            val item = phoneMediaItem(track, player.mediaItemCount)
                            if (player.mediaItemCount == 0) {
                                player.setMediaItem(item)
                                player.prepare()
                                player.play()
                            } else {
                                val insertAt = (player.currentMediaItemIndex + 1)
                                    .coerceAtMost(player.mediaItemCount)
                                player.addMediaItem(insertAt, item)
                            }
                            showToast("下一首播放：${track.name}")
                        }
                    },

                    onAddToPlaylist = { track ->
                        controllerOrWarn()?.let { player ->
                            player.addMediaItem(phoneMediaItem(track, player.mediaItemCount))
                            showToast("已加入播放列表：${track.name}")
                        }
                    },

                    onCreateLocalPlaylist = { name, track ->
                        saveWithArtwork(track) { resolvedTrack ->
                            val playlist = localPlaylistStore.createPlaylist(name, resolvedTrack)
                            localPlaylists = localPlaylistStore.playlists
                            if (playlist != null) {
                                showToast("已创建收藏并添加：${track.name}")
                            } else {
                                showToast("收藏名称不能为空")
                            }
                        }
                    },

                    onCreateEmptyFavorite = { name ->
                        val favorite = localPlaylistStore.createPlaylist(name)
                        localPlaylists = localPlaylistStore.playlists
                        if (favorite != null) {
                            showToast("已创建收藏：${favorite.name}")
                        } else {
                            showToast("收藏名称不能为空")
                        }
                    },

                    onAddToLocalPlaylist = { playlistId, track ->
                        saveWithArtwork(track) { resolvedTrack ->
                            val added = localPlaylistStore.addTrackToPlaylist(
                                playlistId,
                                resolvedTrack
                            )
                            localPlaylists = localPlaylistStore.playlists
                            if (added) {
                                showToast("已收藏：${track.name}")
                            } else {
                                showToast("歌曲已在该收藏中")
                            }
                        }
                    },

                    onDeleteFavorite = { favoriteId ->
                        val deleted = localPlaylistStore.deletePlaylist(favoriteId)
                        localPlaylists = localPlaylistStore.playlists
                        showToast(if (deleted) "收藏已删除" else "删除收藏失败")
                    },

                    onRemoveLocalPlaylistTrack = { playlistId, track ->
                        val removed = localPlaylistStore.removeTrackFromPlaylist(
                            playlistId,
                            track
                        )
                        localPlaylists = localPlaylistStore.playlists
                        showToast(
                            if (removed) "已移出当前收藏：${track.name}" else "移出收藏失败"
                        )
                    },

                    onRootBack = ::handleRootBack
                )
            }
        }
    }

    private fun connectToPlaybackService() {
        val token = SessionToken(
            this,
            ComponentName(this, AutoPlaybackService::class.java)
        )
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                try {
                    mediaController = future.get()
                } catch (error: Exception) {
                    showToast("播放器服务连接失败：${error.message.orEmpty()}")
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    private fun playTracks(tracks: List<Track>, startIndex: Int) {
        val controller = controllerOrWarn() ?: return
        if (tracks.isEmpty() || startIndex !in tracks.indices) return

        val items = tracks.mapIndexed { index, track ->
            phoneMediaItem(track, index)
        }
        controller.setMediaItems(items, startIndex, 0L)
        controller.prepare()
        controller.play()
    }

    private fun phoneMediaItem(track: Track, index: Int) = TrackMediaItem.create(
        "phone:${SystemClock.elapsedRealtime()}:$index:${track.source}:${track.id}",
        track
    )

    private fun searchAndPlayFirst(keyword: String) {
        api.searchTracks(
            keyword,
            object : GdMusicApi.SearchCallback {
                override fun onSuccess(tracks: List<Track>) {
                    runOnUiThread {
                        if (tracks.isEmpty()) {
                            showToast("没有搜索结果")
                        } else {
                            playTracks(tracks, 0)
                        }
                    }
                }

                override fun onError(e: Exception) {
                    runOnUiThread { showToast("搜索失败：${e.message.orEmpty()}") }
                }
            }
        )
    }

    private fun switchPlaybackMode() {
        val controller = controllerOrWarn() ?: return
        when (playbackMode(controller)) {
            PlaybackMode.LIST_LOOP -> {
                controller.shuffleModeEnabled = false
                controller.repeatMode = Player.REPEAT_MODE_ONE
                showToast("单曲循环")
            }
            PlaybackMode.SINGLE_LOOP -> {
                controller.repeatMode = Player.REPEAT_MODE_ALL
                controller.shuffleModeEnabled = true
                showToast("随机播放")
            }
            PlaybackMode.RANDOM -> {
                controller.shuffleModeEnabled = false
                controller.repeatMode = Player.REPEAT_MODE_ALL
                showToast("列表循环")
            }
        }
    }

    private fun playbackMode(player: Player): PlaybackMode = when {
        player.shuffleModeEnabled -> PlaybackMode.RANDOM
        player.repeatMode == Player.REPEAT_MODE_ONE -> PlaybackMode.SINGLE_LOOP
        else -> PlaybackMode.LIST_LOOP
    }

    private fun controllerOrWarn(): MediaController? {
        val controller = mediaController
        if (controller == null) {
            showToast("播放器正在连接，请稍后重试")
        }
        return controller
    }

    private fun refreshMissingLocalArtwork(onChanged: () -> Unit) {
        localPlaylistStore.playlists.forEach { playlist ->
            val coverTrack = playlist.coverTrack ?: return@forEach
            if (!coverTrack.picUrl.isNullOrBlank() && coverTrack.picUrl != "null") {
                return@forEach
            }

            api.getPicUrl(
                coverTrack,
                object : GdMusicApi.TrackCallback {
                    override fun onSuccess(updatedTrack: Track) {
                        runOnUiThread {
                            if (localPlaylistStore.updateTrackInPlaylist(
                                    playlist.id,
                                    updatedTrack
                                )
                            ) {
                                onChanged()
                            }
                        }
                    }

                    override fun onError(e: Exception) {
                        // 保留默认封面，下次启动时再次尝试。
                    }
                }
            )
        }
    }

    private fun saveWithArtwork(track: Track, onReady: (Track) -> Unit) {
        if (!track.picUrl.isNullOrBlank()) {
            onReady(track)
            return
        }

        api.getPicUrl(
            track,
            object : GdMusicApi.TrackCallback {
                override fun onSuccess(updatedTrack: Track) {
                    runOnUiThread { onReady(updatedTrack) }
                }

                override fun onError(e: Exception) {
                    runOnUiThread { onReady(track) }
                }
            }
        )
    }

    private fun requestTracks(
        keyword: String,
        category: SearchCategory,
        source: String,
        page: Int,
        callback: (Result<List<Track>>) -> Unit
    ) {
        if (keyword.isBlank()) {
            callback(Result.failure(IllegalArgumentException("搜索关键词不能为空")))
            return
        }
        if (category == SearchCategory.NETEASE_PLAYLIST) {
            callback(
                Result.failure(
                    IllegalStateException("GD 音乐台 API 暂不支持网易云歌单搜索")
                )
            )
            return
        }

        val requestSource = when (category) {
            SearchCategory.SONG -> source
            SearchCategory.ALBUM -> "${source}_album"
            SearchCategory.NETEASE_PLAYLIST -> "netease"
        }
        api.searchTracks(
            keyword,
            requestSource,
            SEARCH_RESULT_COUNT,
            page.coerceAtLeast(FIRST_PAGE),
            object : GdMusicApi.SearchCallback {
                override fun onSuccess(tracks: List<Track>) {
                    tracks.forEach { it.source = source }
                    runOnUiThread { callback(Result.success(tracks)) }
                }

                override fun onError(e: Exception) {
                    runOnUiThread { callback(Result.failure(e)) }
                }
            }
        )
    }

    private fun requestLyrics(
        track: Track,
        preferredSource: String?,
        callback: (Result<Track>) -> Unit
    ) {
        val cacheKey = lyricCacheKey(track, preferredSource)
        lyricCache[cacheKey]?.let { cached ->
            callback(Result.success(cached))
            return
        }

        api.resolveLyrics(
            track,
            preferredSource,
            object : GdMusicApi.TrackCallback {
                override fun onSuccess(updatedTrack: Track) {
                    lyricCache[cacheKey] = updatedTrack
                    lyricCache[lyricCacheKey(track, updatedTrack.source)] = updatedTrack
                    runOnUiThread { callback(Result.success(updatedTrack)) }
                }

                override fun onError(e: Exception) {
                    runOnUiThread { callback(Result.failure(e)) }
                }
            }
        )
    }

    private fun switchCurrentSource(
        reference: Track,
        source: String,
        callback: (Result<Unit>) -> Unit
    ) {
        api.resolveTrackFromSource(
            reference,
            source,
            if (reference.requestedBitrate > 0) {
                reference.requestedBitrate
            } else {
                PlaybackPreferences.defaultBitrate(this)
            },
            object : GdMusicApi.TrackCallback {
                override fun onSuccess(resolvedTrack: Track) {
                    runOnUiThread {
                        resolvedTrack.requestedBitrate = reference.requestedBitrate
                        replaceCurrentTrack(resolvedTrack, callback)
                    }
                }

                override fun onError(e: Exception) {
                    runOnUiThread { callback(Result.failure(e)) }
                }
            }
        )
    }

    private fun changeCurrentQuality(
        reference: Track,
        bitrate: Int,
        callback: (Result<Unit>) -> Unit
    ) {
        reference.requestedBitrate = AudioQuality.fromBitrate(bitrate).bitrate
        reference.audioUrl = ""
        reference.audioUrlCachedAt = 0L
        replaceCurrentTrack(reference, callback)
    }

    private fun replaceCurrentTrack(
        track: Track,
        callback: (Result<Unit>) -> Unit
    ) {
        val controller = mediaController
        if (controller == null || controller.mediaItemCount == 0) {
            callback(Result.failure(IllegalStateException("播放器未连接")))
            return
        }

        val index = controller.currentMediaItemIndex
        val position = controller.currentPosition.coerceAtLeast(0L)
        val shouldResume = controller.playWhenReady
        val mediaId = controller.currentMediaItem?.mediaId
            ?: "replace:${SystemClock.elapsedRealtime()}:${track.id}"
        controller.replaceMediaItem(index, TrackMediaItem.create(mediaId, track))
        controller.seekTo(index, position)
        controller.prepare()
        if (shouldResume) controller.play()
        callback(Result.success(Unit))
    }

    private fun requestNeteasePlaylists(
        keyword: String,
        page: Int,
        callback: (Result<List<NeteasePlaylist>>) -> Unit
    ) {
        neteaseRepository.searchPlaylists(
            keyword,
            page,
            SEARCH_RESULT_COUNT,
            callback
        )
    }

    private fun importNeteasePlaylist(
        playlist: NeteasePlaylist,
        tracks: List<Track>,
        callback: (Result<Unit>) -> Unit
    ) {
        val imported = localPlaylistStore.createPlaylist(playlist.name, tracks)
        if (imported == null) {
            callback(Result.failure(IllegalStateException("创建收藏失败")))
        } else {
            callback(Result.success(Unit))
        }
    }

    private fun lyricCacheKey(track: Track, preferredSource: String?): String =
        listOf(
            track.name.trim().lowercase(),
            track.artist.trim().lowercase(),
            preferredSource.orEmpty()
        ).joinToString("|")

    private fun handleRootBack() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastRootBackAt <= EXIT_CONFIRM_WINDOW_MS) {
            finish()
            return
        }
        lastRootBackAt = now
        showToast("再按一次返回键退出")
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST
            )
        }
    }

    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mediaController = null
        super.onDestroy()
    }

    private companion object {
        const val SEARCH_RESULT_COUNT = 30
        const val FIRST_PAGE = 1
        const val NOTIFICATION_PERMISSION_REQUEST = 1001
        const val EXIT_CONFIRM_WINDOW_MS = 2_000L
    }
}
