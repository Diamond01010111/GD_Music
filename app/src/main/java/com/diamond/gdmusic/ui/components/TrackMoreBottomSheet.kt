package com.diamond.gdmusic.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.diamond.gdmusic.Track
import com.diamond.gdmusic.AudioQuality
import com.diamond.gdmusic.model.supportedMusicSources

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackMoreBottomSheet(
    track: Track,
    onDismiss: () -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onFavorite: (Track) -> Unit,
    onSearchArtist: (String) -> Unit,
    onSearchAlbum: (String) -> Unit,
    onRemove: ((Track) -> Unit)? = null,
    songSource: String? = null,
    lyricSource: String? = null,
    onSwitchSongSource: ((String) -> Unit)? = null,
    onSwitchLyricSource: ((String) -> Unit)? = null,
    currentBitrate: Int? = null,
    onChangeQuality: ((Int) -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false
    )
    var choosingArtist by remember(track.id) { mutableStateOf(false) }
    var choosingSource by remember(track.id) { mutableStateOf<SourceTarget?>(null) }
    var choosingQuality by remember(track.id) { mutableStateOf(false) }
    val artists = remember(track.artist) { splitArtists(track.artist) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
        ) {
            Column(
                modifier = Modifier.padding(
                    start = 20.dp,
                    end = 20.dp,
                    bottom = 12.dp
                )
            ) {
                Text(
                    text = track.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1
                )
                Text(
                    text = track.artist.ifBlank { "未知歌手" },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
                if (track.album.isNotBlank()) {
                    Text(
                        text = track.album,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }
            }

            HorizontalDivider()

            if (choosingQuality) {
                Text(
                    text = "更改当前歌曲音质",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(20.dp)
                )
                Text(
                    text = "仅对当前歌曲生效，不修改默认音质",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(AudioQuality.entries, key = { it.bitrate }) { quality ->
                        ListItem(
                            headlineContent = { Text(quality.label) },
                            trailingContent = if (quality.bitrate == currentBitrate) {
                                { Icon(Icons.Default.Check, contentDescription = "当前音质") }
                            } else null,
                            modifier = Modifier.fillMaxWidth().clickable {
                                onDismiss()
                                onChangeQuality?.invoke(quality.bitrate)
                            }
                        )
                    }
                }
            } else if (choosingSource != null) {
                Text(
                    text = if (choosingSource == SourceTarget.SONG) {
                        "选择歌曲播放源"
                    } else {
                        "选择歌词来源"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(20.dp)
                )
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(
                        supportedMusicSources.filter { it.recommended },
                        key = { it.value }
                    ) { source ->
                        ListItem(
                            headlineContent = { Text(source.label) },
                            supportingContent = { Text(source.value) },
                            leadingContent = {
                                Icon(Icons.Default.SwapHoriz, contentDescription = null)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val target = choosingSource
                                    onDismiss()
                                    if (target == SourceTarget.SONG) {
                                        onSwitchSongSource?.invoke(source.value)
                                    } else {
                                        onSwitchLyricSource?.invoke(source.value)
                                    }
                                }
                        )
                    }
                }
            } else if (choosingArtist) {
                Text(
                    text = "选择要搜索的歌手",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(20.dp)
                )
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(artists, key = { it }) { artist ->
                        ListItem(
                            headlineContent = { Text(artist) },
                            leadingContent = {
                                Icon(Icons.Default.PersonSearch, contentDescription = null)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDismiss()
                                    onSearchArtist(artist)
                                }
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    if (currentBitrate != null && onChangeQuality != null) {
                        item {
                            SheetAction(
                                "当前音质：${AudioQuality.fromBitrate(currentBitrate).label}",
                                Icons.Default.HighQuality
                            ) {
                                choosingQuality = true
                            }
                        }
                    }
                    if (songSource != null && onSwitchSongSource != null) {
                        item {
                            SheetAction(
                                "歌曲源：${sourceLabel(songSource)}",
                                Icons.Default.MusicNote
                            ) {
                                choosingSource = SourceTarget.SONG
                            }
                        }
                    }
                    if (onSwitchLyricSource != null) {
                        item {
                            SheetAction(
                                "歌词源：${sourceLabel(lyricSource)}",
                                Icons.Default.Lyrics
                            ) {
                                choosingSource = SourceTarget.LYRIC
                            }
                        }
                    }
                    item {
                        SheetAction("添加到下一首播放", Icons.Default.QueuePlayNext) {
                            onDismiss()
                            onPlayNext(track)
                        }
                    }
                    item {
                        SheetAction("加入播放列表", Icons.Default.PlaylistAdd) {
                            onDismiss()
                            onAddToPlaylist(track)
                        }
                    }
                    item {
                        SheetAction("添加到收藏", Icons.Default.FavoriteBorder) {
                            onDismiss()
                            onFavorite(track)
                        }
                    }
                    item {
                        SheetAction(
                            "搜索歌手：${track.artist.ifBlank { "未知歌手" }}",
                            Icons.Default.PersonSearch,
                            enabled = artists.isNotEmpty()
                        ) {
                            if (artists.size > 1) {
                                choosingArtist = true
                            } else {
                                onDismiss()
                                artists.firstOrNull()?.let(onSearchArtist)
                            }
                        }
                    }
                    item {
                        SheetAction(
                            "搜索专辑：${track.album.ifBlank { "未知专辑" }}",
                            Icons.Default.Album,
                            enabled = track.album.isNotBlank()
                        ) {
                            onDismiss()
                            onSearchAlbum(track.album)
                        }
                    }
                    if (onRemove != null) {
                        item {
                            SheetAction(
                                "移出当前收藏",
                                Icons.Default.DeleteOutline,
                                tint = MaterialTheme.colorScheme.error
                            ) {
                                onDismiss()
                                onRemove(track)
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class SourceTarget {
    SONG,
    LYRIC
}

private fun sourceLabel(source: String?): String = if (source == "apple") "自动匹配（苹果榜单）" else supportedMusicSources
    .firstOrNull { it.value == source }
    ?.label
    ?: source?.ifBlank { "自动匹配" }
    ?: "自动匹配"

@Composable
private fun SheetAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean = true,
    tint: androidx.compose.ui.graphics.Color = LocalContentColor.current,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = tint)
        },
        colors = ListItemDefaults.colors(
            headlineColor = if (enabled) {
                LocalContentColor.current
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
    )
}

private fun splitArtists(raw: String): List<String> {
    if (raw.isBlank()) {
        return emptyList()
    }

    return raw
        .split(
            Regex(
                """\s*(?:、|,|，|/|&|\bfeat\.?\b|\bft\.?\b)\s*""",
                RegexOption.IGNORE_CASE
            )
        )
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
}
