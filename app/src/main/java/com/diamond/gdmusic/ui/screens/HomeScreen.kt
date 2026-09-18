package com.diamond.gdmusic.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.diamond.gdmusic.AudioQuality
import com.diamond.gdmusic.LocalPlaylistStore
import com.diamond.gdmusic.RequestTracker
import com.diamond.gdmusic.Track
import com.diamond.gdmusic.data.NeteasePlaylist
import com.diamond.gdmusic.data.NeteasePlaylistRepository
import com.diamond.gdmusic.data.NeteaseToplistCache
import com.diamond.gdmusic.data.PlaybackHistoryStore
import com.diamond.gdmusic.data.RecentPlaylist
import com.diamond.gdmusic.ui.components.AutoSizeSingleLineText
import com.diamond.gdmusic.ui.components.TrackMoreBottomSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    localPlaylists: List<LocalPlaylistStore.LocalPlaylist>,
    defaultBitrate: Int,
    darkMode: Boolean,
    onDefaultBitrateChange: (Int) -> Unit,
    onDarkModeChange: (Boolean) -> Unit,
    onOpenSearch: () -> Unit,
    onPlayHistoryTrack: (List<Track>, Int) -> Unit,
    onPlayPlaylist: (NeteasePlaylist, List<Track>, Int) -> Unit,
    onPlayPlaylistTrack: (NeteasePlaylist, List<Track>, Int) -> Unit,
    onPlayLocalPlaylist: (LocalPlaylistStore.LocalPlaylist, List<Track>, Int) -> Unit,
    onPlayLocalPlaylistTrack: (LocalPlaylistStore.LocalPlaylist, List<Track>, Int) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onFavorite: (Track) -> Unit,
    onSearchArtist: (String, String) -> Unit,
    onSearchAlbum: (String, String) -> Unit,
    onRemoveLocalPlaylistTrack: (String, Track) -> Unit,
    onSyncPlaylist: (NeteasePlaylist, List<Track>, (Result<Unit>) -> Unit) -> Unit
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val historyStore = remember { PlaybackHistoryStore(context.applicationContext) }
    val playlistRepository = remember { NeteasePlaylistRepository() }
    var requestCount by remember { mutableIntStateOf(RequestTracker.countLastFiveMinutes()) }
    var recentTracks by remember { mutableStateOf(historyStore.recentTracks()) }
    var recentPlaylists by remember { mutableStateOf(historyStore.recentPlaylists()) }
    var topLists by remember { mutableStateOf(NeteaseToplistCache.readToplists(context)) }
    var recentlyBrowsedToplists by remember {
        mutableStateOf(NeteaseToplistCache.recentlyBrowsed(context))
    }
    var topListError by remember { mutableStateOf<String?>(null) }
    var isLoadingTopLists by remember { mutableStateOf(topLists.isEmpty()) }
    var showAllRecommendations by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var selectedPlaylist by remember { mutableStateOf<NeteasePlaylist?>(null) }
    var selectedPlaylistIsRecommendation by remember { mutableStateOf(false) }
    var selectedLocalPlaylist by remember { mutableStateOf<LocalPlaylistStore.LocalPlaylist?>(null) }

    fun refreshToplists() {
        isLoadingTopLists = true
        topListError = null
        playlistRepository.loadToplists { result ->
            isLoadingTopLists = false
            result.onSuccess {
                topLists = it
                NeteaseToplistCache.saveToplists(context, it)
            }
                .onFailure { error -> topListError = error.message ?: "加载音乐推荐失败" }
        }
    }

    LaunchedEffect(Unit) {
        if (topLists.isEmpty() || NeteaseToplistCache.shouldRefresh(context)) {
            refreshToplists()
        }
        while (true) {
            requestCount = RequestTracker.countLastFiveMinutes()
            delay(1_000L)
        }
    }

    BackHandler(
        enabled = showHistory || selectedPlaylist != null ||
            selectedLocalPlaylist != null || showAllRecommendations
    ) {
        when {
            selectedPlaylist != null -> selectedPlaylist = null
            selectedLocalPlaylist != null -> selectedLocalPlaylist = null
            showHistory -> showHistory = false
            showAllRecommendations -> showAllRecommendations = false
        }
    }

    if (showHistory) {
        PlaybackHistoryDetail(
            tracks = recentTracks,
            onBack = { showHistory = false },
            onPlayTrack = onPlayHistoryTrack,
            onPlayNext = onPlayNext,
            onAddToPlaylist = onAddToPlaylist,
            onFavorite = onFavorite,
            onSearchArtist = onSearchArtist,
            onSearchAlbum = onSearchAlbum
        )
        return
    }

    selectedPlaylist?.let { playlist ->
        HomePlaylistDetail(
            playlist = playlist,
            repository = playlistRepository,
            onBack = { selectedPlaylist = null },
            onPlayPlaylist = { tracks, index -> onPlayPlaylist(playlist, tracks, index) },
            sourceLabel = if (selectedPlaylistIsRecommendation) "音乐推荐" else "网易云",
            onPlayTrack = { tracks, index -> onPlayPlaylistTrack(playlist, tracks, index) },
            onPlayNext = onPlayNext,
            onAddToPlaylist = onAddToPlaylist,
            onFavorite = onFavorite,
            onSearchArtist = onSearchArtist,
            onSearchAlbum = onSearchAlbum,
            onSync = { tracks, callback ->
                val importPlaylist = if (selectedPlaylistIsRecommendation) {
                    playlist.copy(name = "${playlist.name} ${currentDateLabel()}")
                } else {
                    playlist
                }
                onSyncPlaylist(importPlaylist, tracks, callback)
            }
        )
        return
    }

    selectedLocalPlaylist?.let { playlist ->
        HomeLocalPlaylistDetail(
            playlist = playlist,
            onBack = { selectedLocalPlaylist = null },
            onPlayPlaylist = { tracks, index -> onPlayLocalPlaylist(playlist, tracks, index) },
            onPlayTrack = { tracks, index -> onPlayLocalPlaylistTrack(playlist, tracks, index) },
            onPlayNext = onPlayNext,
            onAddToPlaylist = onAddToPlaylist,
            onFavorite = onFavorite,
            onSearchArtist = onSearchArtist,
            onSearchAlbum = onSearchAlbum,
            onRemoveTrack = { onRemoveLocalPlaylistTrack(playlist.id, it) }
        )
        return
    }

    if (showAllRecommendations) {
        RecommendationListScreen(
            playlists = (recentlyBrowsedToplists + topLists).distinctBy { it.id },
            recentlyBrowsedIds = recentlyBrowsedToplists.mapTo(mutableSetOf()) { it.id },
            isLoading = isLoadingTopLists,
            errorMessage = topListError,
            onBack = { showAllRecommendations = false },
            onRefresh = ::refreshToplists,
            onOpenPlaylist = { playlist ->
                NeteaseToplistCache.recordBrowsing(context, playlist)
                recentlyBrowsedToplists = NeteaseToplistCache.recentlyBrowsed(context)
                selectedPlaylistIsRecommendation = true
                selectedPlaylist = playlist
            }
        )
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(requestCount, defaultBitrate, darkMode, onDefaultBitrateChange, onDarkModeChange)
        }
    ) {
        HomeContent(
            recentPlaylists = recentPlaylists,
            localPlaylists = localPlaylists,
            topLists = topLists,
            recentlyBrowsedToplists = recentlyBrowsedToplists,
            isLoadingTopLists = isLoadingTopLists,
            topListError = topListError,
            onOpenDrawer = { scope.launch { drawerState.open() } },
            onOpenSearch = onOpenSearch,
            onOpenHistory = {
                recentTracks = historyStore.recentTracks()
                showHistory = true
            },
            onRefreshToplists = ::refreshToplists,
            onOpenAllRecommendations = { showAllRecommendations = true },
            onOpenPlaylist = { playlist, isRecommendation ->
                selectedPlaylistIsRecommendation = isRecommendation
                if (isRecommendation) {
                    NeteaseToplistCache.recordBrowsing(context, playlist)
                    recentlyBrowsedToplists = NeteaseToplistCache.recentlyBrowsed(context)
                }
                selectedPlaylist = playlist
            },
            onOpenLocalPlaylist = { playlist -> selectedLocalPlaylist = playlist }
        )
    }

}

@Composable
private fun HomeContent(
    recentPlaylists: List<RecentPlaylist>,
    localPlaylists: List<LocalPlaylistStore.LocalPlaylist>,
    topLists: List<NeteasePlaylist>,
    recentlyBrowsedToplists: List<NeteasePlaylist>,
    isLoadingTopLists: Boolean,
    topListError: String?,
    onOpenDrawer: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenHistory: () -> Unit,
    onRefreshToplists: () -> Unit,
    onOpenAllRecommendations: () -> Unit,
    onOpenPlaylist: (NeteasePlaylist, Boolean) -> Unit,
    onOpenLocalPlaylist: (LocalPlaylistStore.LocalPlaylist) -> Unit
) {
    val visibleToplists = remember(recentlyBrowsedToplists, topLists) {
        (recentlyBrowsedToplists + topLists).distinctBy { it.id }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FilledIconButton(onClick = onOpenDrawer, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Default.Menu, contentDescription = "打开侧边栏")
                }
                Card(
                    onClick = onOpenSearch,
                    modifier = Modifier.weight(1f).padding(start = 10.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp)) {
                        Icon(Icons.Default.Search, contentDescription = null)
                        AutoSizeSingleLineText(
                            text = "搜索歌曲、歌手、专辑或网易云歌单",
                            style = MaterialTheme.typography.bodyLarge,
                            minFontSize = 11.sp,
                            maxFontSize = 16.sp,
                            modifier = Modifier.weight(1f).padding(start = 12.dp)
                        )
                    }
                }
            }
        }
        item { SectionTitle("最近播放") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    HomePlaylistCard(
                        name = "播放历史",
                        coverUrl = "",
                        subtitle = "最近 50 首",
                        fallbackIcon = Icons.Default.History,
                        onClick = onOpenHistory
                    )
                }
                items(recentPlaylists, key = { it.id }) { playlist ->
                    HomePlaylistCard(
                        name = playlist.name,
                        coverUrl = playlist.coverUrl,
                        subtitle = "${if (playlist.type == RecentPlaylist.Type.LOCAL) "收藏" else "网易云"} · ${playlist.trackCount} 首"
                    ) {
                        if (playlist.type == RecentPlaylist.Type.LOCAL) {
                            localPlaylists.firstOrNull { it.id == playlist.id }
                                ?.let(onOpenLocalPlaylist)
                        } else {
                            onOpenPlaylist(
                                NeteasePlaylist(
                                    playlist.id,
                                    playlist.name,
                                    playlist.coverUrl,
                                    playlist.trackCount,
                                    ""
                                ),
                                false
                            )
                        }
                    }
                }
            }
        }
        item {
            RecommendationHeader(
                onRefresh = onRefreshToplists,
                onOpenAll = onOpenAllRecommendations
            )
        }
        item {
            when {
                isLoadingTopLists && visibleToplists.isEmpty() -> Row(
                    Modifier.fillMaxWidth().padding(vertical = 28.dp),
                    horizontalArrangement = Arrangement.Center
                ) { CircularProgressIndicator() }
                topListError != null && visibleToplists.isEmpty() -> Text(
                    topListError,
                    color = MaterialTheme.colorScheme.error
                )
                else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(visibleToplists, key = { it.id }) { playlist ->
                        HomePlaylistCard(
                            name = playlist.name,
                            coverUrl = playlist.coverUrl,
                            subtitle = when {
                                recentlyBrowsedToplists.any { it.id == playlist.id } -> "最近浏览"
                                playlist.trackCount > 0 -> "${playlist.trackCount} 首歌曲"
                                else -> "网易云榜单"
                            },
                            onClick = { onOpenPlaylist(playlist, true) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun RecommendationHeader(
    onRefresh: () -> Unit,
    onOpenAll: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("音乐推荐", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onRefresh) {
            Icon(Icons.Default.Refresh, contentDescription = "刷新音乐推荐")
        }
        TextButton(onClick = onOpenAll) {
            Text("查看全部")
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun HomePlaylistCard(
    name: String,
    coverUrl: String,
    subtitle: String,
    fallbackIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.LibraryMusic,
    onClick: () -> Unit
) {
    Card(onClick = onClick, modifier = Modifier.width(132.dp)) {
        Column {
            if (coverUrl.isNotBlank()) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(fallbackIcon, contentDescription = null, modifier = Modifier.size(36.dp))
                }
            }
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp, end = 8.dp)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp, top = 2.dp, end = 8.dp, bottom = 8.dp)
            )
        }
    }
}

@Composable
private fun HomePlaylistRow(
    name: String,
    coverUrl: String,
    subtitle: String,
    fallbackIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.LibraryMusic,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(onClick = onClick, modifier = modifier) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (coverUrl.isNotBlank()) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(72.dp).clip(MaterialTheme.shapes.medium)
                )
            } else {
                Card(Modifier.size(72.dp)) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(fallbackIcon, contentDescription = null)
                    }
                }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = "打开 $name")
        }
    }
}

@Composable
private fun HomePlaylistCover(coverUrl: String, name: String, modifier: Modifier) {
    Card(modifier) {
        if (coverUrl.isNotBlank() && coverUrl != "null") {
            AsyncImage(
                model = coverUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LibraryMusic, contentDescription = "默认歌单封面")
            }
        }
    }
}

@Composable
private fun HomeTrackCard(track: Track, onClick: () -> Unit, onMore: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(track.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(track.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            IconButton(onClick = onMore) {
                Icon(Icons.Default.MoreVert, contentDescription = "更多")
            }
        }
    }
}

@Composable
private fun PlaybackHistoryDetail(
    tracks: List<Track>,
    onBack: () -> Unit,
    onPlayTrack: (List<Track>, Int) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onFavorite: (Track) -> Unit,
    onSearchArtist: (String, String) -> Unit,
    onSearchAlbum: (String, String) -> Unit
) {
    var moreTrack by remember { mutableStateOf<Track?>(null) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        DetailHeader("播放历史", onBack)
        Text("最近 ${tracks.size} 首", style = MaterialTheme.typography.bodyMedium)
        if (tracks.isEmpty()) {
            Text("播放歌曲后会显示在这里。", modifier = Modifier.padding(24.dp))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(
                    tracks,
                    key = { index, track -> "${track.source}:${track.id}:$index" }
                ) { index, track ->
                    HomeTrackCard(
                        track,
                        onClick = { onPlayTrack(tracks, index) },
                        onMore = { moreTrack = track }
                    )
                }
            }
        }
    }
    moreTrack?.let { track ->
        TrackMoreBottomSheet(track, { moreTrack = null }, onPlayNext, onAddToPlaylist, onFavorite,
            onSearchArtist = { onSearchArtist(it, track.source) },
            onSearchAlbum = { onSearchAlbum(it, track.source) })
    }
}

@Composable
private fun HomePlaylistDetail(
    playlist: NeteasePlaylist,
    repository: NeteasePlaylistRepository,
    onBack: () -> Unit,
    sourceLabel: String,
    onPlayPlaylist: (List<Track>, Int) -> Unit,
    onPlayTrack: (List<Track>, Int) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onFavorite: (Track) -> Unit,
    onSearchArtist: (String, String) -> Unit,
    onSearchAlbum: (String, String) -> Unit,
    onSync: (List<Track>, (Result<Unit>) -> Unit) -> Unit
) {
    var tracks by remember(playlist.id) { mutableStateOf<List<Track>>(emptyList()) }
    var loading by remember(playlist.id) { mutableStateOf(true) }
    var error by remember(playlist.id) { mutableStateOf<String?>(null) }
    var moreTrack by remember(playlist.id) { mutableStateOf<Track?>(null) }
    var showPlaylistMore by remember(playlist.id) { mutableStateOf(false) }
    LaunchedEffect(playlist.id) {
        repository.loadPlaylistTracks(playlist.id) { result ->
            loading = false
            result.onSuccess { tracks = it }.onFailure { failure ->
                error = failure.message ?: "加载歌单失败"
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回主页")
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showPlaylistMore = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "歌单更多")
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HomePlaylistCover(playlist.coverUrl, playlist.name, Modifier.size(96.dp))
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(
                            playlist.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "$sourceLabel · ${tracks.size.coerceAtLeast(playlist.trackCount)} 首歌曲",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Button(
                            enabled = tracks.isNotEmpty() && !loading,
                            onClick = { onPlayPlaylist(tracks, 0) },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Text("播放全部")
                        }
                    }
                }
            }
            when {
                loading -> item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 36.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                error != null -> item {
                    Text(error ?: "加载歌单失败", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(20.dp))
                }
                tracks.isEmpty() -> item { Text("歌单暂无歌曲", modifier = Modifier.padding(20.dp)) }
                else -> itemsIndexed(
                    tracks,
                    key = { index, track -> "${track.source}:${track.id}:$index" }
                ) { index, track ->
                    HomeTrackCard(
                        track,
                        onClick = { onPlayTrack(tracks, index) },
                        onMore = { moreTrack = track }
                    )
                }
            }
        }
    }
    moreTrack?.let { track ->
        TrackMoreBottomSheet(track, { moreTrack = null }, onPlayNext, onAddToPlaylist, onFavorite,
            onSearchArtist = { onSearchArtist(it, track.source) }, onSearchAlbum = { onSearchAlbum(it, track.source) })
    }
    if (showPlaylistMore) {
        HomePlaylistMoreSheet(
            playlist = playlist,
            onDismiss = { showPlaylistMore = false },
            onImport = { callback ->
                when {
                    loading -> callback(Result.failure(IllegalStateException("歌单仍在加载中")))
                    error != null -> callback(Result.failure(IllegalStateException(error)))
                    tracks.isEmpty() -> callback(Result.failure(IllegalStateException("歌单中没有可收藏的歌曲")))
                    else -> onSync(tracks, callback)
                }
            }
        )
    }
}

@Composable
private fun HomeLocalPlaylistDetail(
    playlist: LocalPlaylistStore.LocalPlaylist,
    onBack: () -> Unit,
    onPlayPlaylist: (List<Track>, Int) -> Unit,
    onPlayTrack: (List<Track>, Int) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onFavorite: (Track) -> Unit,
    onSearchArtist: (String, String) -> Unit,
    onSearchAlbum: (String, String) -> Unit,
    onRemoveTrack: (Track) -> Unit
) {
    var moreTrack by remember(playlist.id) { mutableStateOf<Track?>(null) }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回主页")
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    HomePlaylistCover(playlist.coverTrack?.picUrl.orEmpty(), playlist.name, Modifier.size(96.dp))
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(
                            playlist.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "收藏 · ${playlist.tracks.size} 首歌曲",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Button(
                            enabled = playlist.tracks.isNotEmpty(),
                            onClick = { onPlayPlaylist(playlist.tracks, 0) },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Text("播放全部")
                        }
                    }
                }
            }
            if (playlist.tracks.isEmpty()) {
                item { Text("收藏中还没有歌曲", modifier = Modifier.padding(20.dp)) }
            } else {
                itemsIndexed(
                    playlist.tracks,
                    key = { index, track -> "${track.source}:${track.id}:$index" }
                ) { index, track ->
                    HomeTrackCard(
                        track,
                        onClick = { onPlayTrack(playlist.tracks, index) },
                        onMore = { moreTrack = track }
                    )
                }
            }
        }
    }
    moreTrack?.let { track ->
        TrackMoreBottomSheet(track, { moreTrack = null }, onPlayNext, onAddToPlaylist, onFavorite,
            onSearchArtist = { onSearchArtist(it, track.source) }, onSearchAlbum = { onSearchAlbum(it, track.source) },
            onRemove = onRemoveTrack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomePlaylistMoreSheet(
    playlist: NeteasePlaylist,
    onDismiss: () -> Unit,
    onImport: ((Result<Unit>) -> Unit) -> Unit
) {
    var importing by remember(playlist.id) { mutableStateOf(false) }
    var message by remember(playlist.id) { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            ListItem(
                headlineContent = {
                    Text(if (importing) "正在添加…" else "将歌单添加到收藏")
                },
                supportingContent = message?.let { value -> { Text(value) } },
                leadingContent = {
                    if (importing) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Default.PlaylistAdd, contentDescription = null)
                    }
                },
                modifier = Modifier.fillMaxWidth().clickable(enabled = !importing) {
                    importing = true
                    message = null
                    onImport { result ->
                        importing = false
                        result.onSuccess {
                            message = "歌单已添加到收藏"
                        }.onFailure { failure ->
                            message = failure.message ?: "添加失败"
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun RecommendationListScreen(
    playlists: List<NeteasePlaylist>,
    recentlyBrowsedIds: Set<String>,
    isLoading: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenPlaylist: (NeteasePlaylist) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回首页")
            }
            Text(
                "音乐推荐",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRefresh, enabled = !isLoading) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新音乐推荐")
            }
        }
        when {
            isLoading && playlists.isEmpty() -> Row(
                Modifier.fillMaxWidth().padding(vertical = 36.dp),
                horizontalArrangement = Arrangement.Center
            ) { CircularProgressIndicator() }
            errorMessage != null && playlists.isEmpty() -> Text(
                errorMessage,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(20.dp)
            )
            playlists.isEmpty() -> Text("暂无音乐推荐", modifier = Modifier.padding(20.dp))
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(playlists, key = { it.id }) { playlist ->
                    HomePlaylistRow(
                        name = playlist.name,
                        coverUrl = playlist.coverUrl,
                        subtitle = if (playlist.id in recentlyBrowsedIds) "最近浏览" else "${playlist.trackCount} 首歌曲",
                        onClick = { onOpenPlaylist(playlist) }
                    )
                }
            }
        }
    }
}

private fun currentDateLabel(): String =
    SimpleDateFormat("M/d/yyyy", Locale.US).format(Date())

@Composable
private fun DetailHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AppDrawer(
    requestCount: Int,
    defaultBitrate: Int,
    darkMode: Boolean,
    onDefaultBitrateChange: (Int) -> Unit,
    onDarkModeChange: (Boolean) -> Unit
) {
    var showQualityChoices by remember { mutableStateOf(false) }
    ModalDrawerSheet(modifier = Modifier.fillMaxWidth(0.82f).fillMaxHeight()) {
        Text("GD Music", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(20.dp))
        ListItem(headlineContent = { Text("最近 5 分钟 GD 音乐台请求") }, supportingContent = { Text("$requestCount / 50") })
        HorizontalDivider()
        Text("设置", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 6.dp))
        NavigationDrawerItem(
            label = { Text("默认音质：${AudioQuality.fromBitrate(defaultBitrate).label}") },
            selected = false,
            icon = { Icon(Icons.Default.LibraryMusic, contentDescription = null) },
            onClick = { showQualityChoices = !showQualityChoices },
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        if (showQualityChoices) {
            AudioQuality.entries.forEach { quality ->
                NavigationDrawerItem(
                    label = { Text(quality.label) },
                    selected = quality.bitrate == defaultBitrate,
                    onClick = { onDefaultBitrateChange(quality.bitrate); showQualityChoices = false },
                    modifier = Modifier.padding(start = 32.dp, end = 12.dp)
                )
            }
        }
        ListItem(
            headlineContent = { Text("深色模式") },
            trailingContent = { Switch(checked = darkMode, onCheckedChange = onDarkModeChange) },
            modifier = Modifier.fillMaxWidth().clickable { onDarkModeChange(!darkMode) }
        )
        Spacer(Modifier.weight(1f))
    }
}
