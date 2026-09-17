package com.diamond.gdmusic.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    localPlaylists: List<LocalPlaylistStore.LocalPlaylist>,
    defaultBitrate: Int,
    darkMode: Boolean,
    onDefaultBitrateChange: (Int) -> Unit,
    onDarkModeChange: (Boolean) -> Unit,
    onOpenSearch: () -> Unit,
    onPlayHistoryTrack: (Track) -> Unit,
    onPlayPlaylistTrack: (NeteasePlaylist, Track) -> Unit,
    onPlayLocalPlaylistTrack: (LocalPlaylistStore.LocalPlaylist, Track) -> Unit,
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
            onPlayTrack = onPlayHistoryTrack
        )
        return
    }

    selectedPlaylist?.let { playlist ->
        HomePlaylistDetail(
            playlist = playlist,
            repository = playlistRepository,
            onBack = { selectedPlaylist = null },
            onPlayTrack = { track -> onPlayPlaylistTrack(playlist, track) },
            onSync = { tracks, callback -> onSyncPlaylist(playlist, tracks, callback) }
        )
        return
    }

    selectedLocalPlaylist?.let { playlist ->
        HomeLocalPlaylistDetail(
            playlist = playlist,
            onBack = { selectedLocalPlaylist = null },
            onPlayTrack = { track -> onPlayLocalPlaylistTrack(playlist, track) }
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
                item { HistoryPlaylistCard(onOpenHistory) }
                items(recentPlaylists, key = { it.id }) { playlist ->
                    PlaylistCard(playlist.name, playlist.coverUrl, "${playlist.trackCount} 首歌曲") {
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
                        PlaylistCard(
                            playlist.name,
                            playlist.coverUrl,
                            when {
                                recentlyBrowsedToplists.any { it.id == playlist.id } -> "最近浏览"
                                playlist.trackCount > 0 -> "${playlist.trackCount} 首歌曲"
                                else -> "网易云榜单"
                            }
                        ) { onOpenPlaylist(playlist, true) }
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
private fun ExpandedPlaylistRow(
    playlist: NeteasePlaylist,
    isRecentlyBrowsed: Boolean,
    onClick: () -> Unit
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (playlist.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = playlist.coverUrl,
                    contentDescription = playlist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(58.dp).clip(MaterialTheme.shapes.medium)
                )
            } else {
                Icon(Icons.Default.LibraryMusic, contentDescription = null, modifier = Modifier.size(58.dp))
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (isRecentlyBrowsed) "最近浏览" else "${playlist.trackCount} 首歌曲",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Icon(Icons.Default.PlayArrow, contentDescription = "打开 ${playlist.name}")
        }
    }
}

@Composable
private fun HistoryPlaylistCard(onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.width(132.dp)) {
        Column(Modifier.height(176.dp).padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(42.dp))
            Column {
                Text("播放历史", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text("最近 50 首", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun PlaylistCard(name: String, coverUrl: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.width(132.dp)) {
        Column(Modifier.padding(8.dp)) {
            if (coverUrl.isNotBlank()) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(116.dp).clip(MaterialTheme.shapes.medium)
                )
            } else {
                Row(
                    Modifier.size(116.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) { Icon(Icons.Default.LibraryMusic, contentDescription = null) }
            }
            Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
    }
}

@Composable
private fun PlaybackHistoryDetail(
    tracks: List<Track>,
    onBack: () -> Unit,
    onPlayTrack: (Track) -> Unit
) {
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
                ) { _, track ->
                    ListItem(
                        headlineContent = { Text(track.name, maxLines = 1) },
                        supportingContent = { Text(track.artist, maxLines = 1) },
                        trailingContent = { Icon(Icons.Default.PlayArrow, contentDescription = "播放 ${track.name}") },
                        modifier = Modifier.clickable { onPlayTrack(track) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomePlaylistDetail(
    playlist: NeteasePlaylist,
    repository: NeteasePlaylistRepository,
    onBack: () -> Unit,
    onPlayTrack: (Track) -> Unit,
    onSync: (List<Track>, (Result<Unit>) -> Unit) -> Unit
) {
    var tracks by remember(playlist.id) { mutableStateOf<List<Track>>(emptyList()) }
    var loading by remember(playlist.id) { mutableStateOf(true) }
    var error by remember(playlist.id) { mutableStateOf<String?>(null) }
    var syncing by remember(playlist.id) { mutableStateOf(false) }
    var syncMessage by remember(playlist.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(playlist.id) {
        repository.loadPlaylistTracks(playlist.id) { result ->
            loading = false
            result.onSuccess { tracks = it }.onFailure { failure ->
                error = failure.message ?: "加载歌单失败"
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        DetailHeader(playlist.name, onBack)
        Text(
            if (playlist.trackCount > 0) "${playlist.trackCount} 首歌曲" else "网易云歌单",
            style = MaterialTheme.typography.bodyMedium
        )
        when {
            loading -> Row(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
            error != null -> Text(error ?: "加载歌单失败", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
            tracks.isEmpty() -> Text("歌单暂无歌曲", modifier = Modifier.padding(24.dp))
            else -> {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            syncing = true
                            syncMessage = null
                            onSync(tracks) { result ->
                                syncing = false
                                syncMessage = if (result.isSuccess) {
                                    "已同步到我的收藏"
                                } else {
                                    result.exceptionOrNull()?.message ?: "同步歌单失败"
                                }
                            }
                        },
                        enabled = !syncing
                    ) { Text(if (syncing) "同步中…" else "同步歌单") }
                    syncMessage?.let { message ->
                        Text(
                            message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (message == "已同步到我的收藏") {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(
                        tracks,
                        key = { index, track -> "${track.source}:${track.id}:$index" }
                    ) { _, track ->
                        ListItem(
                            headlineContent = { Text(track.name, maxLines = 1) },
                            supportingContent = { Text(track.artist, maxLines = 1) },
                            trailingContent = { IconButton(onClick = { onPlayTrack(track) }) { Icon(Icons.Default.PlayArrow, contentDescription = "播放 ${track.name}") } },
                            modifier = Modifier.clickable { onPlayTrack(track) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeLocalPlaylistDetail(
    playlist: LocalPlaylistStore.LocalPlaylist,
    onBack: () -> Unit,
    onPlayTrack: (Track) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        DetailHeader(playlist.name, onBack)
        Text("${playlist.tracks.size} 首歌曲", style = MaterialTheme.typography.bodyMedium)
        if (playlist.tracks.isEmpty()) {
            Text("收藏中还没有歌曲", modifier = Modifier.padding(24.dp))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(
                    playlist.tracks,
                    key = { index, track -> "${track.source}:${track.id}:$index" }
                ) { _, track ->
                    ListItem(
                        headlineContent = { Text(track.name, maxLines = 1) },
                        supportingContent = { Text(track.artist, maxLines = 1) },
                        trailingContent = {
                            IconButton(onClick = { onPlayTrack(track) }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "播放 ${track.name}")
                            }
                        },
                        modifier = Modifier.clickable { onPlayTrack(track) }
                    )
                }
            }
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
                    ExpandedPlaylistRow(
                        playlist = playlist,
                        isRecentlyBrowsed = playlist.id in recentlyBrowsedIds,
                        onClick = { onOpenPlaylist(playlist) }
                    )
                }
            }
        }
    }
}

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
