package com.diamond.gdmusic.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.diamond.gdmusic.Track
import com.diamond.gdmusic.data.NeteasePlaylist
import com.diamond.gdmusic.data.NeteasePlaylistRepository
import com.diamond.gdmusic.model.SearchCategory
import com.diamond.gdmusic.ui.components.SearchControls
import com.diamond.gdmusic.ui.components.TrackMoreBottomSheet
import kotlinx.coroutines.flow.distinctUntilChanged
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun SearchResultsScreen(
    keyword: String,
    category: SearchCategory,
    source: String,
    tracks: List<Track>,
    playlists: List<NeteasePlaylist>,
    isSearching: Boolean,
    isLoadingMore: Boolean,
    hasMoreResults: Boolean,
    onBack: () -> Unit,
    onSearch: (String, SearchCategory, String) -> Unit,
    onLoadMore: () -> Unit,
    onImportPlaylist: (NeteasePlaylist, List<Track>, (Result<Unit>) -> Unit) -> Unit,
    onPlayPlaylist: (List<Track>, Int) -> Unit,
    onPlaylistTrackClick: (List<Track>, Int) -> Unit,
    onTrackClick: (Int) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onFavorite: (Track) -> Unit,
    onPlayNext: (Track) -> Unit
) {
    var query by remember { mutableStateOf(keyword) }
    var selectedCategory by remember { mutableStateOf(category) }
    var selectedSource by remember { mutableStateOf(source) }
    var moreTrack by remember { mutableStateOf<Track?>(null) }
    var morePlaylist by remember { mutableStateOf<NeteasePlaylist?>(null) }
    var selectedPlaylist by remember { mutableStateOf<NeteasePlaylist?>(null) }
    var selectedPlaylistTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isLoadingPlaylist by remember { mutableStateOf(false) }
    var playlistError by remember { mutableStateOf<String?>(null) }
    var playlistRequestVersion by remember { mutableIntStateOf(0) }
    val neteaseRepository = remember { NeteasePlaylistRepository() }
    val listState = rememberLazyListState()

    fun closePlaylist() {
        playlistRequestVersion++
        selectedPlaylist = null
        selectedPlaylistTracks = emptyList()
        playlistError = null
        morePlaylist = null
    }

    BackHandler {
        if (selectedPlaylist != null) closePlaylist() else onBack()
    }

    LaunchedEffect(keyword, category, source) {
        closePlaylist()
        query = keyword
        selectedCategory = category
        selectedSource = source
        if (tracks.isNotEmpty() || playlists.isNotEmpty()) listState.scrollToItem(0)
    }

    LaunchedEffect(
        listState,
        tracks.size,
        playlists.size,
        hasMoreResults,
        isLoadingMore
    ) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
        }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                if (
                    lastVisibleIndex != null &&
                    resultCount(category, tracks, playlists) > 0 &&
                    lastVisibleIndex >= resultCount(category, tracks, playlists) - 1 -
                    LOAD_MORE_THRESHOLD &&
                    hasMoreResults &&
                    !isLoadingMore &&
                    !isSearching
                ) {
                    onLoadMore()
                }
            }
    }

    fun search(
        searchKeyword: String = query,
        searchCategory: SearchCategory = selectedCategory,
        searchSource: String = selectedSource
    ) {
        val clean = searchKeyword.trim()
        if (clean.isNotEmpty() && !isSearching) {
            closePlaylist()
            query = clean
            selectedCategory = searchCategory
            selectedSource = searchSource
            onSearch(clean, searchCategory, searchSource)
        }
    }

    fun loadPlaylist(playlist: NeteasePlaylist) {
        val version = ++playlistRequestVersion
        isLoadingPlaylist = true
        playlistError = null
        selectedPlaylistTracks = emptyList()
        neteaseRepository.loadPlaylistTracks(playlist.id) { result ->
            if (version != playlistRequestVersion) return@loadPlaylistTracks
            isLoadingPlaylist = false
            result.onSuccess { selectedPlaylistTracks = it }
            result.onFailure { error ->
                playlistError = error.message ?: "加载歌单歌曲失败"
            }
        }
    }

    LaunchedEffect(selectedPlaylist?.id) {
        selectedPlaylist?.let(::loadPlaylist)
    }

    selectedPlaylist?.let { playlist ->
        SearchedPlaylistDetail(
            playlist = playlist,
            tracks = selectedPlaylistTracks,
            isLoading = isLoadingPlaylist,
            errorMessage = playlistError,
            onBack = ::closePlaylist,
            onRetry = { loadPlaylist(playlist) },
            onMore = { morePlaylist = playlist },
            onPlayPlaylist = onPlayPlaylist,
            onTrackClick = onPlaylistTrackClick,
            onPlayNext = onPlayNext,
            onAddToPlaylist = onAddToPlaylist,
            onFavorite = onFavorite,
            onSearchArtist = { artist, trackSource ->
                search(artist, SearchCategory.SONG, trackSource)
            },
            onSearchAlbum = { album, trackSource ->
                search(album, SearchCategory.ALBUM, trackSource)
            }
        )
        morePlaylist?.let {
            PlaylistSearchMoreSheet(
                playlist = playlist,
                tracks = selectedPlaylistTracks,
                onDismiss = { morePlaylist = null },
                onImport = onImportPlaylist
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回搜索")
            }
            Text(
                "搜索结果",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 4.dp, top = 10.dp)
            )
        }

        SearchControls(
            keyword = query,
            category = selectedCategory,
            sourceValue = selectedSource,
            isSearching = isSearching,
            onKeywordChange = { query = it },
            onCategoryChange = { newCategory ->
                val nextSource = if (
                    newCategory == SearchCategory.NETEASE_PLAYLIST
                ) {
                    "netease"
                } else {
                    selectedSource
                }
                search(query, newCategory, nextSource)
            },
            onSourceChange = { newSource ->
                val nextCategory = if (
                    selectedCategory == SearchCategory.NETEASE_PLAYLIST &&
                    newSource != "netease"
                ) {
                    SearchCategory.SONG
                } else {
                    selectedCategory
                }
                search(query, nextCategory, newSource)
            },
            onSubmit = { search() }
        )

        if (isSearching) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }

        val showingPlaylists = category == SearchCategory.NETEASE_PLAYLIST
        val emptyResults = if (showingPlaylists) playlists.isEmpty() else tracks.isEmpty()
        if (emptyResults && !isSearching) {
            Text(
                "没有找到搜索结果",
                modifier = Modifier.padding(20.dp)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (showingPlaylists) {
                    items(
                        count = playlists.size,
                        key = { index -> "netease-playlist-${playlists[index].id}" }
                    ) { index ->
                        val playlist = playlists[index]
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { selectedPlaylist = playlist }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                if (playlist.coverUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = playlist.coverUrl,
                                        contentDescription = playlist.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(64.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                }
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(
                                        playlist.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 2
                                    )
                                    Text(
                                        "${playlist.trackCount} 首歌曲",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                } else items(
                    count = tracks.size,
                    key = { index ->
                        "${tracks[index].source}-${tracks[index].id}-$index"
                    }
                ) { index ->
                    val track = tracks[index]
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onTrackClick(index) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    track.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1
                                )
                                Text(
                                    track.artist,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1
                                )
                                if (track.album.isNotBlank()) {
                                    Text(
                                        track.album,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1
                                    )
                                }
                            }
                            IconButton(onClick = { moreTrack = track }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "更多")
                            }
                        }
                    }
                }

                item(key = "search-pagination-footer") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        when {
                            isLoadingMore -> CircularProgressIndicator()
                            !emptyResults && !hasMoreResults -> Text(
                                "没有更多结果",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }

    moreTrack?.let { track ->
        TrackMoreBottomSheet(
            track = track,
            onDismiss = { moreTrack = null },
            onPlayNext = onPlayNext,
            onAddToPlaylist = onAddToPlaylist,
            onFavorite = onFavorite,
            onSearchArtist = { artist ->
                search(artist, SearchCategory.SONG, selectedSource)
            },
            onSearchAlbum = { album ->
                search(album, SearchCategory.ALBUM, selectedSource)
            }
        )
    }

}

private const val LOAD_MORE_THRESHOLD = 5

private fun resultCount(
    category: SearchCategory,
    tracks: List<Track>,
    playlists: List<NeteasePlaylist>
): Int = if (category == SearchCategory.NETEASE_PLAYLIST) playlists.size else tracks.size

@Composable
private fun SearchedPlaylistDetail(
    playlist: NeteasePlaylist,
    tracks: List<Track>,
    isLoading: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onMore: () -> Unit,
    onPlayPlaylist: (List<Track>, Int) -> Unit,
    onTrackClick: (List<Track>, Int) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onFavorite: (Track) -> Unit,
    onSearchArtist: (String, String) -> Unit,
    onSearchAlbum: (String, String) -> Unit
) {
    var moreTrack by remember { mutableStateOf<Track?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回歌单搜索")
            }
            Spacer(Modifier.weight(1f))
            IconButton(
                enabled = !isLoading && errorMessage == null,
                onClick = onMore
            ) {
                Icon(Icons.Default.MoreVert, contentDescription = "歌单更多")
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item(key = "playlist-header") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    if (playlist.coverUrl.isNotBlank()) {
                        AsyncImage(
                            model = playlist.coverUrl,
                            contentDescription = playlist.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(12.dp))
                        )
                    }
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(
                            playlist.name,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${if (isLoading) playlist.trackCount else tracks.size} 首歌曲",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Button(
                            enabled = tracks.isNotEmpty() && !isLoading,
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
                isLoading -> item(key = "playlist-loading") {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 36.dp),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) { CircularProgressIndicator() }
                }
                errorMessage != null -> item(key = "playlist-error") {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(errorMessage, color = MaterialTheme.colorScheme.error)
                            Button(onClick = onRetry, modifier = Modifier.padding(top = 10.dp)) {
                                Text("重试")
                            }
                        }
                    }
                }
                tracks.isEmpty() -> item(key = "playlist-empty") {
                    Text("歌单中没有可显示的歌曲", Modifier.padding(20.dp))
                }
                else -> itemsIndexed(
                    items = tracks,
                    key = { index, track -> "${track.source}-${track.id}-$index" }
                ) { index, track ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onTrackClick(tracks, index) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .padding(start = 14.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    track.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    track.artist.ifBlank { "未知歌手" },
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { moreTrack = track }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "歌曲更多")
                            }
                        }
                    }
                }
            }
        }
    }

    moreTrack?.let { track ->
        TrackMoreBottomSheet(
            track = track,
            onDismiss = { moreTrack = null },
            onPlayNext = onPlayNext,
            onAddToPlaylist = onAddToPlaylist,
            onFavorite = onFavorite,
            onSearchArtist = { onSearchArtist(it, track.source) },
            onSearchAlbum = { onSearchAlbum(it, track.source) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistSearchMoreSheet(
    playlist: NeteasePlaylist,
    tracks: List<Track>,
    onDismiss: () -> Unit,
    onImport: (NeteasePlaylist, List<Track>, (Result<Unit>) -> Unit) -> Unit
) {
    var importing by remember(playlist.id) { mutableStateOf(false) }
    var message by remember(playlist.id) { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
            Text(
                playlist.name,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            ListItem(
                headlineContent = {
                    Text(if (importing) "正在导入收藏…" else "加入收藏")
                },
                supportingContent = message?.let { value -> { Text(value) } },
                leadingContent = {
                    if (importing) CircularProgressIndicator(Modifier.size(24.dp))
                    else Icon(Icons.Default.PlaylistAdd, contentDescription = null)
                },
                modifier = Modifier.fillMaxWidth().clickable(enabled = !importing) {
                    importing = true
                    message = null
                    onImport(playlist, tracks) { result ->
                        importing = false
                        result.onSuccess {
                            message = "已加入收藏，共 ${tracks.size} 首歌曲"
                        }
                        result.onFailure { error ->
                            message = error.message ?: "导入失败"
                        }
                    }
                }
            )
        }
    }
}
