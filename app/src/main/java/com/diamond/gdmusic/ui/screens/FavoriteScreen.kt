package com.diamond.gdmusic.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.diamond.gdmusic.LocalPlaylistStore
import com.diamond.gdmusic.Track
import com.diamond.gdmusic.ui.components.TrackMoreBottomSheet

@Composable
fun FavoriteScreen(
    playlists: List<LocalPlaylistStore.LocalPlaylist>,
    onPlayAll: (LocalPlaylistStore.LocalPlaylist, List<Track>, Int) -> Unit,
    onPlayTrack: (LocalPlaylistStore.LocalPlaylist, List<Track>, Int) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onFavorite: (Track) -> Unit,
    onSearchArtist: (String, String) -> Unit,
    onSearchAlbum: (String, String) -> Unit,
    onCreateFavorite: (String) -> Unit,
    onDeleteFavorite: (String) -> Unit,
    onRenameFavorite: (String, String) -> Boolean,
    onRemoveTrack: (String, Track) -> Unit
) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    val selected = playlists.firstOrNull { it.id == selectedId }

    BackHandler(enabled = selected != null) {
        selectedId = null
    }

    if (selected != null) {
        FavoriteDetail(
            favorite = selected,
            onBack = { selectedId = null },
            onPlayAll = onPlayAll,
            onPlayTrack = onPlayTrack,
            onPlayNext = onPlayNext,
            onAddToPlaylist = onAddToPlaylist,
            onFavorite = onFavorite,
            onSearchArtist = onSearchArtist,
            onSearchAlbum = onSearchAlbum,
            onRename = { onRenameFavorite(selected.id, it) },
            onDelete = {
                onDeleteFavorite(selected.id)
                selectedId = null
            },
            onRemoveTrack = { onRemoveTrack(selected.id, it) }
        )
        return
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                "我的收藏",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 12.dp)
            )

            if (playlists.isEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Text("还没有收藏，点击右下角按钮创建。", Modifier.padding(20.dp))
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(playlists.size, key = { playlists[it].id }) { index ->
                        val favorite = playlists[index]
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { selectedId = favorite.id }
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FavoriteCover(favorite.coverTrack, Modifier.size(72.dp))
                                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                    Text(
                                        favorite.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1
                                    )
                                    Text(
                                        "${favorite.tracks.size} 首歌曲",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showCreate = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "新建收藏")
        }
    }

    if (showCreate) {
        NameFavoriteDialog(
            title = "新建收藏",
            confirmText = "创建",
            onDismiss = { showCreate = false },
            onConfirm = {
                onCreateFavorite(it)
                showCreate = false
            }
        )
    }
}

@Composable
private fun FavoriteDetail(
    favorite: LocalPlaylistStore.LocalPlaylist,
    onBack: () -> Unit,
    onPlayAll: (LocalPlaylistStore.LocalPlaylist, List<Track>, Int) -> Unit,
    onPlayTrack: (LocalPlaylistStore.LocalPlaylist, List<Track>, Int) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onFavorite: (Track) -> Unit,
    onSearchArtist: (String, String) -> Unit,
    onSearchAlbum: (String, String) -> Unit,
    onDelete: () -> Unit,
    onRename: (String) -> Boolean,
    onRemoveTrack: (Track) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var renameError by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var moreTrack by remember { mutableStateOf<Track?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回我的收藏")
            }
            Spacer(Modifier.weight(1f))
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("重命名") },
                        enabled = favorite.name != LocalPlaylistStore.LIKED_PLAYLIST_NAME,
                        onClick = { showMenu = false; renameError = false; showRename = true })
                    DropdownMenuItem(text = { Text("删除歌单") },
                        onClick = { showMenu = false; confirmDelete = true })
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item(key = "favorite-header") {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FavoriteCover(favorite.coverTrack, Modifier.size(112.dp))
                    Column(Modifier.weight(1f).padding(start = 16.dp)) {
                        Text(favorite.name, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "${favorite.tracks.size} 首歌曲",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Button(
                            enabled = favorite.tracks.isNotEmpty(),
                            onClick = { onPlayAll(favorite, favorite.tracks, 0) },
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Text("播放全部")
                        }
                    }
                }
            }
            if (favorite.tracks.isEmpty()) {
                item(key = "favorite-empty") {
                    Text("收藏中还没有歌曲", Modifier.padding(20.dp))
                }
            } else {
                itemsIndexed(
                    favorite.tracks,
                    key = { index, track -> "${track.source}-${track.id}-$index" }
                ) { index, track ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onPlayTrack(favorite, favorite.tracks, index) }
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    track.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1
                                )
                                Text(
                                    track.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1
                                )
                            }
                            IconButton(onClick = { moreTrack = track }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "更多")
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
            onSearchArtist = { artist ->
                onSearchArtist(artist, track.source)
            },
            onSearchAlbum = { album ->
                onSearchAlbum(album, track.source)
            },
            onRemove = onRemoveTrack
        )
    }

    if (showRename) {
        NameFavoriteDialog(title = "重命名歌单", confirmText = "保存",
            initialName = favorite.name,
            error = if (renameError) "无法重命名，请使用其他名称" else null,
            onDismiss = { showRename = false },
            onConfirm = { if (onRename(it)) showRename = false else renameError = true })
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除收藏？") },
            text = { Text("确定删除“${favorite.name}”吗？其中的歌曲也会从该收藏中移除。") },
            confirmButton = {
                TextButton(onClick = onDelete) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun NameFavoriteDialog(
    title: String,
    confirmText: String,
    initialName: String = "",
    error: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("收藏名称") },
                isError = error != null,
                supportingText = { if (error != null) Text(error) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name.trim()) }
            ) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun FavoriteCover(
    track: Track?,
    modifier: Modifier = Modifier
) {
    Card(modifier.aspectRatio(1f)) {
        val coverUrl = track?.picUrl.orEmpty()
        if (coverUrl.isNotBlank() && coverUrl != "null") {
            AsyncImage(
                model = coverUrl,
                contentDescription = track?.name ?: "收藏封面",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.LibraryMusic,
                    contentDescription = "默认收藏封面",
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}
