package com.diamond.gdmusic.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.diamond.gdmusic.Track
import com.diamond.gdmusic.data.NeteaseAlbum
import com.diamond.gdmusic.data.NeteaseArtist
import com.diamond.gdmusic.data.NeteaseArtistRepository
import com.diamond.gdmusic.ui.components.TrackMoreBottomSheet

@Composable
fun ArtistDetailScreen(
    artistName: String,
    onBack: () -> Unit,
    onPlayAll: (List<Track>, Int) -> Unit,
    onPlayTrack: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onFavorite: (Track) -> Unit,
    onSearchAlbum: (String) -> Unit
) {
    val repository = remember { NeteaseArtistRepository() }
    var artist by remember(artistName) { mutableStateOf<NeteaseArtist?>(null) }
    var albums by remember(artistName) { mutableStateOf<List<NeteaseAlbum>>(emptyList()) }
    var selectedAlbum by remember(artistName) { mutableStateOf<NeteaseAlbum?>(null) }
    var tracks by remember(artistName) { mutableStateOf<List<Track>>(emptyList()) }
    var moreTrack by remember { mutableStateOf<Track?>(null) }
    var loading by remember(artistName) { mutableStateOf(true) }
    var loadingAlbums by remember(artistName) { mutableStateOf(false) }
    var loadingTracks by remember(artistName) { mutableStateOf(false) }
    var error by remember(artistName) { mutableStateOf<String?>(null) }
    var moreAlbums by remember(artistName) { mutableStateOf(false) }
    var offset by remember(artistName) { mutableIntStateOf(0) }
    var generation by remember(artistName) { mutableIntStateOf(0) }

    fun loadAlbums(id: String) {
        if (loadingAlbums || (offset > 0 && !moreAlbums)) return
        error = null
        loadingAlbums = true
        val request = generation
        repository.loadAlbums(id, offset) { result ->
            if (request == generation) {
                loadingAlbums = false
                result.onSuccess { page ->
                    val merged = (albums + page).distinctBy { it.id }
                    moreAlbums = page.isNotEmpty() && merged.size > albums.size
                    albums = merged
                    offset += page.size
                }.onFailure { error = it.message ?: "加载专辑失败" }
            }
        }
    }

    fun loadArtist() {
        generation++
        val request = generation
        loading = true
        error = null
        repository.findArtist(artistName) { result ->
            if (request == generation) {
                loading = false
                result.onSuccess {
                    artist = it
                    loadAlbums(it.id)
                }.onFailure { error = it.message ?: "加载艺人失败" }
            }
        }
    }

    LaunchedEffect(artistName) { loadArtist() }
    fun loadAlbumTracks(album: NeteaseAlbum) {
        tracks = emptyList()
        loadingTracks = true
        error = null
        val request = generation
        repository.loadAlbumTracks(album.id) { result ->
            if (request == generation && selectedAlbum?.id == album.id) {
                loadingTracks = false
                result.onSuccess { tracks = it }
                    .onFailure { error = it.message ?: "加载专辑歌曲失败" }
            }
        }
    }
    LaunchedEffect(selectedAlbum?.id) {
        selectedAlbum?.let(::loadAlbumTracks)
    }
    BackHandler {
        if (selectedAlbum != null) {
            selectedAlbum = null
            error = null
        } else onBack()
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                if (selectedAlbum != null) selectedAlbum = null else onBack()
            }) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") }
            Text(selectedAlbum?.name ?: artist?.name ?: artistName,
                style = MaterialTheme.typography.titleLarge)
        }
        if (loading || loadingTracks) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let {
            Text(it, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = {
                if (artist == null) loadArtist()
                else if (selectedAlbum != null) selectedAlbum?.let(::loadAlbumTracks)
                else artist?.id?.let(::loadAlbums)
            }) { Text("重试") }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (selectedAlbum == null) {
                artist?.let { info ->
                    item {
                        Column {
                            if (info.coverUrl.isNotBlank()) {
                                AsyncImage(info.coverUrl, info.name, contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(180.dp).clip(RoundedCornerShape(12.dp)))
                            }
                            Text(info.name, style = MaterialTheme.typography.headlineMedium)
                            Text("别名：${info.aliases.ifEmpty { listOf("暂无") }.joinToString("、")}")
                            Text("艺人类型：${info.coverType}")
                            Text(info.description.ifBlank { "暂无简介" })
                            Text("专辑", style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(top = 16.dp))
                        }
                    }
                }
                items(albums, key = { it.id }) { album ->
                    ListItem(
                        headlineContent = { Text(album.name) },
                        supportingContent = { Text("${album.songCount} 首歌曲") },
                        leadingContent = {
                            if (album.coverUrl.isNotBlank()) AsyncImage(album.coverUrl, album.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)))
                        },
                        modifier = Modifier.clickable { selectedAlbum = album }
                    )
                }
                if (loadingAlbums) item { CircularProgressIndicator() }
                if (moreAlbums && !loadingAlbums) item {
                    TextButton(onClick = { artist?.id?.let(::loadAlbums) }) { Text("加载更多专辑") }
                }
            } else {
                if (tracks.isNotEmpty()) item {
                    Button(onClick = { onPlayAll(tracks, 0) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Text("播放全部")
                    }
                }
                items(tracks, key = { it.id }) { track ->
                    ListItem(
                        headlineContent = { Text(track.name) },
                        supportingContent = { Text(track.artist) },
                        trailingContent = {
                            IconButton(onClick = { moreTrack = track }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "更多")
                            }
                        },
                        modifier = Modifier.clickable { onPlayTrack(track) }
                    )
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
            onSearchArtist = { /* Already on the selected artist's detail page. */ },
            onSearchAlbum = onSearchAlbum
        )
    }
}
