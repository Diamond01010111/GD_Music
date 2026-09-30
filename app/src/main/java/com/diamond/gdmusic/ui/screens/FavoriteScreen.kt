package com.diamond.gdmusic.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.diamond.gdmusic.LocalPlaylistStore
import com.diamond.gdmusic.Track
import com.diamond.gdmusic.data.PlaylistShareResult
import com.diamond.gdmusic.data.SharedPlaylist
import com.diamond.gdmusic.ui.components.TrackMoreBottomSheet

@Composable
fun FavoriteScreen(
    playlists: List<LocalPlaylistStore.LocalPlaylist>,
    pendingImportCode: String?,
    onImportCodeConsumed: () -> Unit,
    onSharePlaylist: (LocalPlaylistStore.LocalPlaylist, (Result<PlaylistShareResult>) -> Unit) -> Unit,
    onLoadSharedPlaylist: (String, (Result<SharedPlaylist>) -> Unit) -> Unit,
    onImportSharedPlaylist: (String, List<Track>) -> Boolean,
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
    var showAddMenu by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var showImportCode by remember { mutableStateOf(false) }
    var importLoading by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    var sharedPlaylist by remember { mutableStateOf<SharedPlaylist?>(null) }
    val selected = playlists.firstOrNull { it.id == selectedId }

    fun loadSharedPlaylist(value: String) {
        importLoading = true
        importError = null
        onLoadSharedPlaylist(value) { result ->
            importLoading = false
            result.onSuccess { sharedPlaylist = it }
                .onFailure { importError = it.message ?: "导入歌单失败" }
        }
    }

    LaunchedEffect(pendingImportCode) {
        pendingImportCode?.let {
            onImportCodeConsumed()
            selectedId = null
            loadSharedPlaylist(it)
        }
    }

    BackHandler(enabled = selected != null) { selectedId = null }

    if (selected != null) {
        FavoriteDetail(
            favorite = selected,
            onBack = { selectedId = null },
            onShare = onSharePlaylist,
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
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Text(
                "我的收藏",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 12.dp)
            )
            if (playlists.isEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Text("还没有收藏，点击右下角按钮创建或导入。", Modifier.padding(20.dp))
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    items(playlists.size, key = { playlists[it].id }) { index ->
                        val favorite = playlists[index]
                        Card(
                            onClick = { selectedId = favorite.id },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                FavoriteCover(favorite.coverTrack, Modifier.size(72.dp))
                                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                    Text(favorite.name, style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${favorite.tracks.size} 首歌曲", style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        Box(Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
            FloatingActionButton(onClick = { showAddMenu = true }) {
                Icon(Icons.Default.Add, contentDescription = "添加收藏")
            }
            DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                DropdownMenuItem(
                    text = { Text("新建收藏") },
                    leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                    onClick = { showAddMenu = false; showCreate = true }
                )
                DropdownMenuItem(
                    text = { Text("通过分享码导入") },
                    leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                    onClick = { showAddMenu = false; showImportCode = true }
                )
            }
        }
    }

    if (showCreate) {
        NameFavoriteDialog("新建收藏", "创建", onDismiss = { showCreate = false }) {
            onCreateFavorite(it); showCreate = false
        }
    }
    if (showImportCode) {
        ImportCodeDialog(
            onDismiss = { showImportCode = false },
            onImport = { code -> showImportCode = false; loadSharedPlaylist(code) }
        )
    }
    if (importLoading) LoadingDialog("正在读取分享歌单…")
    importError?.let { message ->
        AlertDialog(
            onDismissRequest = { importError = null },
            title = { Text("无法导入") }, text = { Text(message) },
            confirmButton = { TextButton(onClick = { importError = null }) { Text("确定") } }
        )
    }
    sharedPlaylist?.let { shared ->
        SharedPlaylistImportSheet(
            shared = shared,
            onDismiss = { sharedPlaylist = null },
            onImport = { tracks ->
                if (onImportSharedPlaylist(shared.name, tracks)) sharedPlaylist = null
            }
        )
    }
}

@Composable
private fun FavoriteDetail(
    favorite: LocalPlaylistStore.LocalPlaylist,
    onBack: () -> Unit,
    onShare: (LocalPlaylistStore.LocalPlaylist, (Result<PlaylistShareResult>) -> Unit) -> Unit,
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
    var sharing by remember { mutableStateOf(false) }
    var shareError by remember { mutableStateOf<String?>(null) }
    var shareResult by remember { mutableStateOf<PlaylistShareResult?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回我的收藏")
            }
            Spacer(Modifier.weight(1f))
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("分享歌单") },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        enabled = favorite.tracks.isNotEmpty() && !sharing,
                        onClick = {
                            showMenu = false; sharing = true; shareError = null
                            onShare(favorite) { result ->
                                sharing = false
                                result.onSuccess { shareResult = it }
                                    .onFailure { shareError = it.message ?: "分享歌单失败" }
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        enabled = favorite.name != LocalPlaylistStore.LIKED_PLAYLIST_NAME,
                        onClick = { showMenu = false; renameError = false; showRename = true }
                    )
                    DropdownMenuItem(
                        text = { Text("删除歌单") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        enabled = favorite.name != LocalPlaylistStore.LIKED_PLAYLIST_NAME,
                        onClick = { showMenu = false; confirmDelete = true }
                    )
                }
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item(key = "favorite-header") {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    FavoriteCover(favorite.coverTrack, Modifier.size(112.dp))
                    Column(Modifier.weight(1f).padding(start = 16.dp)) {
                        Text(favorite.name, style = MaterialTheme.typography.headlineSmall)
                        Text("${favorite.tracks.size} 首歌曲", style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp))
                        Button(
                            enabled = favorite.tracks.isNotEmpty(),
                            onClick = { onPlayAll(favorite, favorite.tracks, 0) },
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null); Text("播放全部")
                        }
                    }
                }
            }
            if (favorite.tracks.isEmpty()) {
                item(key = "favorite-empty") { Text("收藏中还没有歌曲", Modifier.padding(20.dp)) }
            } else {
                itemsIndexed(favorite.tracks,
                    key = { index, track -> "${track.source}-${track.id}-$index" }) { index, track ->
                    Card(
                        onClick = { onPlayTrack(favorite, favorite.tracks, index) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(track.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                Text(track.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1)
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
            track, { moreTrack = null }, onPlayNext, onAddToPlaylist, onFavorite,
            onSearchArtist = { onSearchArtist(it, track.source) },
            onSearchAlbum = { onSearchAlbum(it, track.source) }, onRemove = onRemoveTrack
        )
    }
    if (showRename) {
        NameFavoriteDialog("重命名歌单", "保存", favorite.name,
            if (renameError) "无法重命名，请使用其他名称" else null,
            { showRename = false }) {
            if (onRename(it)) showRename = false else renameError = true
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false }, title = { Text("删除收藏？") },
            text = { Text("确定删除“${favorite.name}”？其中的歌曲也会从该收藏移除。") },
            confirmButton = { TextButton(onClick = onDelete) { Text("删除") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
        )
    }
    if (sharing) LoadingDialog("正在生成分享链接…")
    shareError?.let { message ->
        AlertDialog(
            onDismissRequest = { shareError = null }, title = { Text("分享失败") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { shareError = null }) { Text("确定") } }
        )
    }
    shareResult?.let { result ->
        ShareResultDialog(favorite.name, result, onDismiss = { shareResult = null })
    }
}

@Composable
private fun ShareResultDialog(playlistName: String, result: PlaylistShareResult, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("分享链接已生成") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("分享码（24 小时内有效）")
                CopyRow(result.shareId) { copyText(context, "分享码", result.shareId) }
                Text("分享链接")
                CopyRow(result.shareUrl) { copyText(context, "分享链接", result.shareUrl) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val message = "GD Music 歌单：$playlistName\n分享码：${result.shareId}\n${result.shareUrl}\n链接将在 24 小时后失效。"
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, message)
                }, "分享歌单"))
            }) { Icon(Icons.Default.Share, contentDescription = null); Text("系统分享") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}

@Composable
private fun CopyRow(value: String, onCopy: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.small, tonalElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(value, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = onCopy) { Icon(Icons.Default.ContentCopy, contentDescription = "复制") }
        }
    }
}

@Composable
private fun ImportCodeDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("导入分享歌单") },
        text = {
            OutlinedTextField(
                value = code, onValueChange = { code = it.uppercase() }, singleLine = true,
                label = { Text("10 位分享码") }, modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(enabled = code.isNotBlank(), onClick = { onImport(code.trim()) }) { Text("读取歌单") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedPlaylistImportSheet(
    shared: SharedPlaylist,
    onDismiss: () -> Unit,
    onImport: (List<Track>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedIndices by remember(shared.shareId) { mutableStateOf(shared.tracks.indices.toSet()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                FavoriteCover(shared.tracks.firstOrNull(), Modifier.size(88.dp))
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(shared.name, style = MaterialTheme.typography.titleLarge,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${shared.tracks.size} 首歌曲 · 已选择 ${selectedIndices.size} 首",
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { selectedIndices = shared.tracks.indices.toSet() }) { Text("全选") }
                TextButton(onClick = {
                    selectedIndices = shared.tracks.indices.filterNot { it in selectedIndices }.toSet()
                }) { Text("反选") }
            }
            HorizontalDivider()
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
                itemsIndexed(shared.tracks,
                    key = { index, track -> "${track.source}:${track.id}:$index" }) { index, track ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            selectedIndices = if (index in selectedIndices) selectedIndices - index
                            else selectedIndices + index
                        }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(track.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(track.artist, style = MaterialTheme.typography.bodySmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Checkbox(
                            checked = index in selectedIndices,
                            onCheckedChange = { checked ->
                                selectedIndices = if (checked) selectedIndices + index else selectedIndices - index
                            }
                        )
                    }
                }
            }
            Surface(tonalElevation = 3.dp) {
                Button(
                    enabled = selectedIndices.isNotEmpty(),
                    onClick = {
                        onImport(shared.tracks.filterIndexed { index, _ -> index in selectedIndices })
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(top = 12.dp, bottom = 8.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Text("导入所选歌曲")
                }
            }
        }
    }
}

@Composable
private fun LoadingDialog(message: String) {
    AlertDialog(
        onDismissRequest = {}, title = { Text(message) },
        text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } },
        confirmButton = {}
    )
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
        onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it }, label = { Text("收藏名称") },
                isError = error != null, supportingText = { if (error != null) Text(error) },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name.trim()) }) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun FavoriteCover(track: Track?, modifier: Modifier = Modifier) {
    Card(modifier.aspectRatio(1f)) {
        val coverUrl = track?.picUrl.orEmpty()
        if (coverUrl.isNotBlank() && coverUrl != "null") {
            AsyncImage(
                model = coverUrl, contentDescription = track?.name,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.MusicNote, contentDescription = "默认收藏封面")
            }
        }
    }
}

private fun copyText(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
}
