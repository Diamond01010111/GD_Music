package com.diamond.gdmusic.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import coil3.compose.AsyncImage
import com.diamond.gdmusic.LocalPlaylistStore
import com.diamond.gdmusic.PlaybackMode
import com.diamond.gdmusic.Track
import com.diamond.gdmusic.data.NeteasePlaylist
import com.diamond.gdmusic.model.AppPage
import com.diamond.gdmusic.model.SearchCategory
import com.diamond.gdmusic.ui.components.AutoSizeSingleLineText
import com.diamond.gdmusic.ui.components.MiniPlayer
import com.diamond.gdmusic.ui.components.QueueBottomSheet
import com.diamond.gdmusic.ui.screens.FavoriteScreen
import com.diamond.gdmusic.ui.screens.HomeScreen
import com.diamond.gdmusic.ui.screens.NeteasePlaylistScreen
import com.diamond.gdmusic.ui.screens.PlayerDetailScreen
import com.diamond.gdmusic.ui.screens.SearchResultsScreen
import com.diamond.gdmusic.ui.screens.SearchScreen

@Composable
fun MusicApp(
    nowPlayingTrack: Track?,
    artworkUrl: String,
    isPlaying: Boolean,
    playMode: PlaybackMode,
    queue: List<Track>,
    currentIndex: Int,
    playbackProgress: Float,
    playbackPositionMs: Long,
    playbackDurationMs: Long,
    localPlaylists: List<LocalPlaylistStore.LocalPlaylist>,
    defaultBitrate: Int,
    darkMode: Boolean,
    showLyricTranslation: Boolean,

    onRequestSearch: (
        keyword: String,
        category: SearchCategory,
        source: String,
        page: Int,
        callback: (Result<List<Track>>) -> Unit
    ) -> Unit,

    onRequestNeteasePlaylists: (
        keyword: String,
        page: Int,
        callback: (Result<List<NeteasePlaylist>>) -> Unit
    ) -> Unit,
    onImportNeteasePlaylist: (
        playlist: NeteasePlaylist,
        tracks: List<Track>,
        callback: (Result<Unit>) -> Unit
    ) -> Unit,

    onRequestLyrics: (Track, String?, (Result<Track>) -> Unit) -> Unit,
    onSwitchCurrentSource: (Track, String, (Result<Unit>) -> Unit) -> Unit,
    onChangeCurrentQuality: (Track, Int, (Result<Unit>) -> Unit) -> Unit,
    onDefaultBitrateChange: (Int) -> Unit,
    onDarkModeChange: (Boolean) -> Unit,
    onShowLyricTranslationChange: (Boolean) -> Unit,

    onPlayResults: (
        tracks: List<Track>,
        index: Int
    ) -> Unit,

    onRecommendedSongClick: (String) -> Unit,
    onPlayPause: () -> Unit,
    onSwitchPlayMode: () -> Unit,
    onQueueTrackClick: (Int) -> Unit,
    onRemoveQueueTrack: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onCreateLocalPlaylist: (String, Track) -> Unit,
    onCreateEmptyFavorite: (String) -> Unit,
    onAddToLocalPlaylist: (String, Track) -> Unit,
    onDeleteFavorite: (String) -> Unit,
    onRemoveLocalPlaylistTrack: (String, Track) -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPlayNext: (Track) -> Unit,
    onRootBack: () -> Unit
) {
    var currentPage by remember {
        mutableStateOf(AppPage.HOME)
    }

    var resultKeyword by remember {
        mutableStateOf("")
    }

    var resultCategory by remember {
        mutableStateOf(SearchCategory.SONG)
    }

    var resultSource by remember {
        mutableStateOf("netease")
    }

    var resultTracks by remember {
        mutableStateOf<List<Track>>(emptyList())
    }

    var resultPlaylists by remember {
        mutableStateOf<List<NeteasePlaylist>>(emptyList())
    }

    var isSearching by remember {
        mutableStateOf(false)
    }

    var isLoadingMore by remember {
        mutableStateOf(false)
    }

    var resultPage by remember {
        mutableStateOf(1)
    }

    var searchGeneration by remember {
        mutableStateOf(0)
    }

    var hasMoreResults by remember {
        mutableStateOf(false)
    }

    var searchError by remember {
        mutableStateOf<String?>(null)
    }

    var showQueue by remember {
        mutableStateOf(false)
    }

    var showPlayerDetail by remember {
        mutableStateOf(false)
    }

    var pendingFavoriteTrack by remember {
        mutableStateOf<Track?>(null)
    }

    fun executeSearch(
        keyword: String,
        category: SearchCategory,
        source: String
    ) {
        searchGeneration += 1
        val generation = searchGeneration
        isSearching = true
        isLoadingMore = false
        searchError = null

        if (category == SearchCategory.NETEASE_PLAYLIST) {
            onRequestNeteasePlaylists(keyword, 1) { result ->
                if (generation == searchGeneration) {
                    isSearching = false
                    result.onSuccess { playlists ->
                        resultKeyword = keyword
                        resultCategory = category
                        resultSource = "netease"
                        resultTracks = emptyList()
                        resultPlaylists = playlists
                        resultPage = 1
                        hasMoreResults = playlists.size >= SEARCH_PAGE_SIZE
                        currentPage = AppPage.SEARCH_RESULTS
                    }
                    result.onFailure { error ->
                        searchError = error.message ?: "歌单搜索失败"
                    }
                }
            }
        } else {
            onRequestSearch(keyword, category, source, 1) { result ->
                if (generation == searchGeneration) {
                    isSearching = false
                    result.onSuccess { tracks ->
                        resultKeyword = keyword
                        resultCategory = category
                        resultSource = source
                        resultTracks = tracks
                        resultPlaylists = emptyList()
                        resultPage = 1
                        hasMoreResults = tracks.size >= SEARCH_PAGE_SIZE
                        currentPage = AppPage.SEARCH_RESULTS
                    }
                    result.onFailure { error ->
                        searchError = error.message ?: "搜索失败"
                    }
                }
            }
        }
    }

    fun loadNextSearchPage() {
        if (isSearching || isLoadingMore || !hasMoreResults) return

        val keyword = resultKeyword
        val category = resultCategory
        val source = resultSource
        val nextPage = resultPage + 1
        val generation = searchGeneration
        isLoadingMore = true

        fun searchChanged(): Boolean = generation != searchGeneration ||
            keyword != resultKeyword ||
            category != resultCategory ||
            source != resultSource

        if (category == SearchCategory.NETEASE_PLAYLIST) {
            onRequestNeteasePlaylists(keyword, nextPage) { result ->
                if (!searchChanged()) {
                    isLoadingMore = false
                    result.onSuccess { playlists ->
                        resultPlaylists = (resultPlaylists + playlists).distinctBy { it.id }
                        resultPage = nextPage
                        hasMoreResults = playlists.size >= SEARCH_PAGE_SIZE
                    }
                    result.onFailure { error ->
                        searchError = error.message ?: "加载下一页失败"
                    }
                }
            }
        } else onRequestSearch(keyword, category, source, nextPage) { result ->
            // A new search may have started while this page was loading.
            val searchChanged = generation != searchGeneration ||
                keyword != resultKeyword ||
                category != resultCategory ||
                source != resultSource
            if (!searchChanged) {
                isLoadingMore = false
                result.onSuccess { tracks ->
                    resultTracks = (resultTracks + tracks).distinctBy {
                        "${it.source}:${it.id}"
                    }
                    resultPage = nextPage
                    hasMoreResults = tracks.size >= SEARCH_PAGE_SIZE
                }

                result.onFailure { error ->
                    searchError = error.message ?: "加载下一页失败"
                }
            }
        }
    }

    LaunchedEffect(nowPlayingTrack?.source, nowPlayingTrack?.id) {
        if (nowPlayingTrack == null) showPlayerDetail = false
    }

    val showNavigationBar =
        currentPage == AppPage.HOME ||
                currentPage == AppPage.FAVORITE ||
                currentPage == AppPage.NETEASE_PLAYLIST

    // 顶层页面不再把系统返回键直接交给 Activity。详情页和搜索页会在各自
    // 的 Composable 中注册更靠后的 BackHandler，因此会优先返回上一级。
    BackHandler(enabled = showNavigationBar && !showPlayerDetail) {
        onRootBack()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,

        bottomBar = {
            if (!showPlayerDetail) Column(
                modifier = Modifier.padding(
                    bottom = if (showNavigationBar) 0.dp else 12.dp
                )
            ) {
                MiniPlayer(
                    track = nowPlayingTrack,
                    artworkUrl = artworkUrl,
                    isPlaying = isPlaying,
                    playMode = playMode,
                    playbackProgress = playbackProgress,
                    onPlayPause = onPlayPause,
                    onSwitchPlayMode = onSwitchPlayMode,
                    onOpenQueue = {
                        showQueue = true
                    },
                    onOpenDetails = {
                        if (nowPlayingTrack != null) showPlayerDetail = true
                    },
                    onSwipePrevious = onSkipPrevious,
                    onSwipeNext = onSkipNext
                )

                if (showNavigationBar) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = currentPage == AppPage.HOME,
                            onClick = {
                                currentPage = AppPage.HOME
                            },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = "主页"
                                )
                            },
                            label = {
                                Text("主页")
                            }
                        )

                        NavigationBarItem(
                            selected = currentPage == AppPage.FAVORITE,
                            onClick = {
                                currentPage = AppPage.FAVORITE
                            },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = "收藏"
                                )
                            },
                            label = {
                                Text("收藏")
                            }
                        )

                        NavigationBarItem(
                            selected = currentPage == AppPage.NETEASE_PLAYLIST,
                            onClick = {
                                currentPage = AppPage.NETEASE_PLAYLIST
                            },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.LibraryMusic,
                                    contentDescription = "网易云歌单"
                                )
                            },
                            label = {
                                AutoSizeSingleLineText(
                                    text = "网易云歌单",
                                    minFontSize = 9.sp,
                                    maxFontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            if (showPlayerDetail && nowPlayingTrack != null) {
                PlayerDetailScreen(
                    track = nowPlayingTrack,
                    artworkUrl = artworkUrl,
                    isPlaying = isPlaying,
                    playMode = playMode,
                    playbackProgress = playbackProgress,
                    playbackPositionMs = playbackPositionMs,
                    playbackDurationMs = playbackDurationMs,
                    showLyricTranslation = showLyricTranslation,
                    onShowLyricTranslationChange = onShowLyricTranslationChange,
                    onBack = { showPlayerDetail = false },
                    onPlayPause = onPlayPause,
                    onSwitchPlayMode = onSwitchPlayMode,
                    onSkipPrevious = onSkipPrevious,
                    onSkipNext = onSkipNext,
                    onOpenQueue = { showQueue = true },
                    onSeekTo = onSeekTo,
                    onRequestLyrics = onRequestLyrics,
                    onSwitchSongSource = onSwitchCurrentSource,
                    currentBitrate = if (nowPlayingTrack.requestedBitrate > 0) {
                        nowPlayingTrack.requestedBitrate
                    } else {
                        defaultBitrate
                    },
                    onChangeCurrentQuality = onChangeCurrentQuality,
                    onPlayNext = onPlayNext,
                    onAddToPlaylist = onAddToPlaylist,
                    onFavorite = { track -> pendingFavoriteTrack = track },
                    onSearchArtist = { artist, source ->
                        showPlayerDetail = false
                        executeSearch(artist, SearchCategory.SONG, source)
                    },
                    onSearchAlbum = { album, source ->
                        showPlayerDetail = false
                        executeSearch(album, SearchCategory.ALBUM, source)
                    }
                )
            } else when (currentPage) {
                AppPage.HOME -> {
                    HomeScreen(
                        defaultBitrate = defaultBitrate,
                        darkMode = darkMode,
                        onDefaultBitrateChange = onDefaultBitrateChange,
                        onDarkModeChange = onDarkModeChange,
                        onOpenSearch = {
                            searchError = null
                            currentPage = AppPage.SEARCH
                        },
                        onSongClick = onRecommendedSongClick
                    )
                }

                AppPage.FAVORITE -> {
                    FavoriteScreen(
                        playlists = localPlaylists,
                        onPlayPlaylist = onPlayResults,
                        onPlayNext = onPlayNext,
                        onAddToPlaylist = onAddToPlaylist,
                        onFavorite = { track ->
                            pendingFavoriteTrack = track
                        },
                        onSearchArtist = { artist, source ->
                            executeSearch(
                                artist,
                                SearchCategory.SONG,
                                source
                            )
                        },
                        onSearchAlbum = { album, source ->
                            executeSearch(
                                album,
                                SearchCategory.ALBUM,
                                source
                            )
                        },
                        onCreateFavorite = onCreateEmptyFavorite,
                        onDeleteFavorite = onDeleteFavorite,
                        onRemoveTrack = onRemoveLocalPlaylistTrack
                    )
                }

                AppPage.NETEASE_PLAYLIST -> {
                    NeteasePlaylistScreen(
                        onPlayPlaylist = onPlayResults,
                        onPlayNext = onPlayNext,
                        onAddToPlaylist = onAddToPlaylist,
                        onFavorite = { track ->
                            pendingFavoriteTrack = track
                        },
                        onImportPlaylist = onImportNeteasePlaylist,
                        onSearchArtist = { artist, source ->
                            executeSearch(
                                artist,
                                SearchCategory.SONG,
                                source
                            )
                        },
                        onSearchAlbum = { album, source ->
                            executeSearch(
                                album,
                                SearchCategory.ALBUM,
                                source
                            )
                        }
                    )
                }

                AppPage.SEARCH -> {
                    SearchScreen(
                        isSearching = isSearching,
                        errorMessage = searchError,
                        onBack = {
                            searchError = null
                            currentPage = AppPage.HOME
                        },
                        onSearch = ::executeSearch
                    )
                }

                AppPage.SEARCH_RESULTS -> {
                    SearchResultsScreen(
                        keyword = resultKeyword,
                        category = resultCategory,
                        source = resultSource,
                        tracks = resultTracks,
                        playlists = resultPlaylists,
                        isSearching = isSearching,
                        isLoadingMore = isLoadingMore,
                        hasMoreResults = hasMoreResults,
                        onBack = {
                            currentPage = AppPage.SEARCH
                        },
                        onSearch = ::executeSearch,
                        onLoadMore = ::loadNextSearchPage,
                        onImportPlaylist = onImportNeteasePlaylist,
                        onPlayPlaylist = onPlayResults,
                        onTrackClick = { index ->
                            onPlayResults(resultTracks, index)
                        },
                        onPlayNext = onPlayNext,
                        onAddToPlaylist = onAddToPlaylist,
                        onFavorite = { track ->
                            pendingFavoriteTrack = track
                        }
                    )
                }
            }
        }
    }

    if (showQueue) {
        QueueBottomSheet(
            tracks = queue,
            currentIndex = currentIndex,
            onDismiss = {
                showQueue = false
            },
            onTrackClick = { index ->
                onQueueTrackClick(index)
            },
            onRemoveTrack = { index ->
                onRemoveQueueTrack(index)
            },
            onClearQueue = {
                onClearQueue()
            }
        )
    }

    pendingFavoriteTrack?.let { track ->
        AddToFavoriteSheet(
            track = track,
            playlists = localPlaylists,
            onDismiss = {
                pendingFavoriteTrack = null
            },
            onCreatePlaylist = { name ->
                onCreateLocalPlaylist(name, track)
                pendingFavoriteTrack = null
            },
            onSelectPlaylist = { playlistId ->
                onAddToLocalPlaylist(playlistId, track)
                pendingFavoriteTrack = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddToFavoriteSheet(
    track: Track,
    playlists: List<LocalPlaylistStore.LocalPlaylist>,
    onDismiss: () -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onSelectPlaylist: (String) -> Unit
) {
    var showCreateDialog by remember(track.id) { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Text(
            text = "添加到收藏",
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        Text(
            text = track.name,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .padding(top = 8.dp, bottom = 24.dp)
        ) {
            item(key = "create-favorite") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCreateDialog = true }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AddCircle,
                        contentDescription = null,
                        modifier = Modifier.size(52.dp)
                    )
                    Text(
                        text = "新建收藏",
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 14.dp)
                    )
                }
            }

            items(
                items = playlists,
                key = { it.id }
            ) { favorite ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectPlaylist(favorite.id) }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FavoriteSheetCover(
                        track = favorite.coverTrack,
                        modifier = Modifier.size(52.dp)
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 14.dp)
                    ) {
                        Text(
                            favorite.name,
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                        Text(
                            "${favorite.tracks.size} 首歌曲",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("新建收藏") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("收藏名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = { onCreatePlaylist(name.trim()) }
                ) {
                    Text("创建并收藏")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun FavoriteSheetCover(
    track: Track?,
    modifier: Modifier = Modifier
) {
    val coverUrl = track?.picUrl.orEmpty()

    if (coverUrl.isNotBlank() && coverUrl != "null") {
        AsyncImage(
            model = coverUrl,
            contentDescription = track?.name ?: "收藏封面",
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(RoundedCornerShape(8.dp))
        )
    } else {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.MusicNote, contentDescription = null)
        }
    }
}

private const val SEARCH_PAGE_SIZE = 30
