package com.diamond.gdmusic.ui.screens

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.diamond.gdmusic.PlaybackMode
import com.diamond.gdmusic.Track
import com.diamond.gdmusic.ui.components.TrackMoreBottomSheet
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun PlayerDetailScreen(
    track: Track,
    artworkUrl: String,
    isPlaying: Boolean,
    playMode: PlaybackMode,
    playbackProgress: Float,
    playbackPositionMs: Long,
    playbackDurationMs: Long,
    showLyricTranslation: Boolean,
    onShowLyricTranslationChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSwitchPlayMode: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onOpenQueue: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onRequestLyrics: (Track, String?, (Result<Track>) -> Unit) -> Unit,
    onSwitchSongSource: (Track, String, (Result<Unit>) -> Unit) -> Unit,
    currentBitrate: Int,
    onChangeCurrentQuality: (Track, Int, (Result<Unit>) -> Unit) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onFavorite: (Track) -> Unit,
    onSearchArtist: (String, String) -> Unit,
    onSearchAlbum: (String, String) -> Unit
) {
    BackHandler(onBack = onBack)

    var rawLyrics by remember(track.source, track.id) { mutableStateOf("") }
    var rawTranslatedLyrics by remember(track.source, track.id) { mutableStateOf("") }
    var lyricSource by remember(track.source, track.id) { mutableStateOf<String?>(null) }
    var isLoadingLyrics by remember(track.source, track.id) { mutableStateOf(true) }
    var lyricError by remember(track.source, track.id) { mutableStateOf<String?>(null) }
    var lyricRequestSerial by remember(track.source, track.id) { mutableIntStateOf(0) }
    var sourceMessage by remember(track.source, track.id) { mutableStateOf<String?>(null) }
    var showMore by remember { mutableStateOf(false) }

    fun loadLyrics(preferredSource: String?) {
        lyricRequestSerial += 1
        val requestSerial = lyricRequestSerial
        isLoadingLyrics = true
        lyricError = null
        sourceMessage = null
        rawTranslatedLyrics = ""
        onRequestLyrics(track, preferredSource) { result ->
            if (requestSerial == lyricRequestSerial) {
                isLoadingLyrics = false
                result.onSuccess { resolved ->
                    rawLyrics = resolved.lyric.orEmpty()
                    rawTranslatedLyrics = resolved.translatedLyric
                        .orEmpty()
                        .takeUnless { it == "null" }
                        .orEmpty()
                    lyricSource = resolved.source
                }
                result.onFailure { error ->
                    rawLyrics = ""
                    rawTranslatedLyrics = ""
                    lyricError = error.message ?: "所有来源均未找到歌词"
                }
            }
        }
    }

    LaunchedEffect(track.source, track.id, track.lyricId) {
        loadLyrics(null)
    }

    val lyricLines = remember(rawLyrics) { parseLyrics(rawLyrics) }
    val translatedLines = remember(rawTranslatedLyrics) {
        parseLyrics(rawTranslatedLyrics)
    }
    val translations = remember(lyricLines, translatedLines) {
        alignTranslations(lyricLines, translatedLines)
    }
    val hasTranslation = translations.any { !it.isNullOrBlank() }
    val currentLyricIndex = remember(lyricLines, playbackPositionMs) {
        lyricLines.indexOfLast { line ->
            line.timeMs != null && line.timeMs <= playbackPositionMs
        }.coerceAtLeast(0)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回")
            }
            Text("歌曲详情", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showMore = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "更多")
            }
        }

        PlayerArtwork(
            artworkUrl = artworkUrl,
            songName = track.name,
            modifier = Modifier
                .size(148.dp)
                .align(Alignment.CenterHorizontally)
                .padding(top = 6.dp)
        )
        Text(
            text = track.name,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, start = 24.dp, end = 24.dp)
        )
        Text(
            text = track.artist.ifBlank { "未知歌手" },
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp, start = 24.dp, end = 24.dp)
        )

        LyricsPanel(
            lines = lyricLines,
            translations = translations,
            showTranslation = showLyricTranslation && hasTranslation,
            hasTranslation = hasTranslation,
            onToggleTranslation = {
                onShowLyricTranslationChange(!showLyricTranslation)
            },
            currentIndex = currentLyricIndex,
            isLoading = isLoadingLyrics,
            errorMessage = lyricError,
            modifier = Modifier.fillMaxWidth().weight(1f)
        )

        sourceMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
        }

        PlaybackControls(
            playMode = playMode,
            isPlaying = isPlaying,
            playbackProgress = playbackProgress,
            playbackPositionMs = playbackPositionMs,
            playbackDurationMs = playbackDurationMs,
            onSeekTo = onSeekTo,
            onSwitchPlayMode = onSwitchPlayMode,
            onSkipPrevious = onSkipPrevious,
            onPlayPause = onPlayPause,
            onSkipNext = onSkipNext,
            onOpenQueue = onOpenQueue
        )
    }

    if (showMore) {
        TrackMoreBottomSheet(
            track = track,
            onDismiss = { showMore = false },
            onPlayNext = onPlayNext,
            onAddToPlaylist = onAddToPlaylist,
            onFavorite = onFavorite,
            onSearchArtist = { artist -> onSearchArtist(artist, track.source) },
            onSearchAlbum = { album -> onSearchAlbum(album, track.source) },
            songSource = track.source,
            lyricSource = lyricSource,
            currentBitrate = currentBitrate,
            onSwitchSongSource = { source ->
                sourceMessage = null
                onSwitchSongSource(track, source) { result ->
                    result.onFailure { error ->
                        sourceMessage = error.message ?: "歌曲换源失败"
                    }
                }
            },
            onSwitchLyricSource = ::loadLyrics,
            onChangeQuality = { bitrate ->
                sourceMessage = null
                onChangeCurrentQuality(track, bitrate) { result ->
                    result.onFailure { error ->
                        sourceMessage = error.message ?: "音质切换失败"
                    }
                }
            }
        )
    }
}

@Composable
private fun LyricsPanel(
    lines: List<LyricLine>,
    translations: List<String?>,
    showTranslation: Boolean,
    hasTranslation: Boolean,
    onToggleTranslation: () -> Unit,
    currentIndex: Int,
    isLoading: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var manualScrollUntil by remember { mutableLongStateOf(0L) }
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    val verticalContentPadding = with(LocalDensity.current) {
        if (viewportHeightPx > 0) {
            (viewportHeightPx / 2f).toDp()
        } else {
            64.dp
        }
    }
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) {
                    manualScrollUntil = SystemClock.elapsedRealtime() + MANUAL_SCROLL_HOLD_MS
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(
        currentIndex,
        manualScrollUntil,
        lines.size,
        viewportHeightPx,
        showTranslation,
        translations
    ) {
        if (lines.isEmpty()) return@LaunchedEffect
        val remaining = manualScrollUntil - SystemClock.elapsedRealtime()
        if (remaining > 0L) delay(remaining)
        withFrameNanos { }
        listState.animateScrollToCenteredItem(currentIndex)
    }

    Box(modifier = modifier.padding(top = 8.dp), contentAlignment = Alignment.Center) {
        when {
            isLoading -> CircularProgressIndicator()
            errorMessage != null -> Text(
                errorMessage,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp)
            )
            lines.isEmpty() -> Text("暂无歌词")
            else -> LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { viewportHeightPx = it.height }
                    .nestedScroll(nestedScrollConnection),
                contentPadding = PaddingValues(vertical = verticalContentPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(lines) { index, line ->
                    val active = index == currentIndex && line.timeMs != null
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = line.text,
                            style = if (active) {
                                MaterialTheme.typography.titleMedium
                            } else {
                                MaterialTheme.typography.bodyLarge
                            },
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            color = if (active) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            textAlign = TextAlign.Center
                        )
                        translations.getOrNull(index)
                            ?.takeIf { showTranslation && it.isNotBlank() }
                            ?.let { translatedText ->
                                Text(
                                    text = translatedText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (active) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.78f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                            alpha = 0.72f
                                        )
                                    },
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                    }
                }
            }
        }

        TranslationToggleButton(
            enabled = hasTranslation,
            checked = showTranslation,
            onClick = onToggleTranslation,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
        )
    }
}

@Composable
private fun TranslationToggleButton(
    enabled: Boolean,
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
        checked -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.Translate,
                contentDescription = when {
                    !enabled -> "当前歌曲没有翻译歌词"
                    checked -> "关闭翻译歌词"
                    else -> "打开翻译歌词"
                },
                tint = iconColor
            )
            if (!enabled) {
                Box(
                    Modifier
                        .width(28.dp)
                        .height(2.dp)
                        .rotate(-45f)
                        .background(iconColor, RoundedCornerShape(1.dp))
                )
            }
        }
    }
}

private suspend fun androidx.compose.foundation.lazy.LazyListState.animateScrollToCenteredItem(
    index: Int
) {
    var itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    if (itemInfo == null) {
        scrollToItem(index)
        itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    }
    val item = itemInfo ?: return
    // Item offsets use LazyColumn's content coordinate system. With dynamic
    // before/after padding its visual center is between these two offsets,
    // rather than half of viewportSize.
    val viewportCenter =
        (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
    val itemCenter = item.offset + item.size / 2f
    animateScrollBy(itemCenter - viewportCenter)
}

@Composable
private fun PlaybackControls(
    playMode: PlaybackMode,
    isPlaying: Boolean,
    playbackProgress: Float,
    playbackPositionMs: Long,
    playbackDurationMs: Long,
    onSeekTo: (Long) -> Unit,
    onSwitchPlayMode: () -> Unit,
    onSkipPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onOpenQueue: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
    ) {
        PlaybackSeekBar(
            progress = playbackProgress,
            enabled = playbackDurationMs > 0L,
            onSeekFinished = { progress ->
                onSeekTo((playbackDurationMs * progress).toLong())
            }
        )
        Row(Modifier.fillMaxWidth()) {
            Text(formatDuration(playbackPositionMs), style = MaterialTheme.typography.labelMedium)
            Text(
                formatDuration(playbackDurationMs),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onSwitchPlayMode, modifier = Modifier.size(52.dp)) {
                Icon(
                    when (playMode) {
                        PlaybackMode.LIST_LOOP -> Icons.Default.Repeat
                        PlaybackMode.SINGLE_LOOP -> Icons.Default.RepeatOne
                        PlaybackMode.RANDOM -> Icons.Default.Shuffle
                    },
                    contentDescription = "切换播放模式"
                )
            }
            IconButton(onClick = onSkipPrevious, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "上一首", Modifier.size(32.dp))
            }
            IconButton(onClick = onPlayPause, modifier = Modifier.size(62.dp)) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    modifier = Modifier.size(42.dp)
                )
            }
            IconButton(onClick = onSkipNext, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Default.SkipNext, contentDescription = "下一首", Modifier.size(32.dp))
            }
            IconButton(onClick = onOpenQueue, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Default.QueueMusic, contentDescription = "播放列表")
            }
        }
    }
}

@Composable
private fun PlaybackSeekBar(
    progress: Float,
    enabled: Boolean,
    onSeekFinished: (Float) -> Unit
) {
    var widthPx by remember { mutableIntStateOf(1) }
    var displayedProgress by remember { mutableFloatStateOf(progress.coerceIn(0f, 1f)) }
    var dragging by remember { mutableStateOf(false) }
    val thumbRadiusPx = with(LocalDensity.current) { 6.dp.roundToPx() }

    LaunchedEffect(progress, dragging) {
        if (!dragging) displayedProgress = progress.coerceIn(0f, 1f)
    }

    fun updateFromX(x: Float) {
        displayedProgress = (x / widthPx.toFloat()).coerceIn(0f, 1f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .pointerInput(enabled, widthPx) {
                if (enabled) detectTapGestures { offset ->
                    updateFromX(offset.x)
                    onSeekFinished(displayedProgress)
                }
            }
            .pointerInput(enabled, widthPx) {
                if (enabled) detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        updateFromX(offset.x)
                    },
                    onHorizontalDrag = { change, _ ->
                        updateFromX(change.position.x)
                        change.consume()
                    },
                    onDragEnd = {
                        dragging = false
                        onSeekFinished(displayedProgress)
                    },
                    onDragCancel = { dragging = false }
                )
            }
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Box(
            Modifier
                .fillMaxWidth(displayedProgress)
                .height(4.dp)
                .align(Alignment.CenterStart)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Box(
            Modifier
                .offset {
                    IntOffset(
                        x = (widthPx * displayedProgress).roundToInt() - thumbRadiusPx,
                        y = 0
                    )
                }
                .size(12.dp)
                .align(Alignment.CenterStart)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
private fun PlayerArtwork(
    artworkUrl: String,
    songName: String,
    modifier: Modifier
) {
    if (artworkUrl.isNotBlank() && artworkUrl != "null") {
        AsyncImage(
            model = artworkUrl,
            contentDescription = songName,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(RoundedCornerShape(14.dp))
        )
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(54.dp))
        }
    }
}

private fun parseLyrics(rawLyrics: String): List<LyricLine> {
    val lines = mutableListOf<LyricLine>()
    rawLyrics.lineSequence().forEach { rawLine ->
        val text = rawLine.replace(LYRIC_TAG, "").trim()
        if (text.isEmpty()) return@forEach
        val timestamps = LYRIC_TIMESTAMP.findAll(rawLine).toList()
        if (timestamps.isEmpty()) {
            lines += LyricLine(null, text)
        } else {
            timestamps.forEach { match ->
                val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                val fraction = match.groupValues[3]
                val millis = when (fraction.length) {
                    1 -> fraction.toLongOrNull()?.times(100L) ?: 0L
                    2 -> fraction.toLongOrNull()?.times(10L) ?: 0L
                    else -> fraction.take(3).padEnd(3, '0').toLongOrNull() ?: 0L
                }
                lines += LyricLine((minutes * 60L + seconds) * 1_000L + millis, text)
            }
        }
    }
    return lines.sortedWith(
        compareBy<LyricLine> { it.timeMs == null }
            .thenBy { it.timeMs ?: Long.MAX_VALUE }
    )
}

private fun alignTranslations(
    originalLines: List<LyricLine>,
    translatedLines: List<LyricLine>
): List<String?> {
    if (originalLines.isEmpty() || translatedLines.isEmpty()) {
        return List(originalLines.size) { null }
    }

    val timedTranslations = translatedLines.filter { it.timeMs != null }
    return originalLines.mapIndexed { index, original ->
        val originalTime = original.timeMs
        val matched = if (originalTime != null && timedTranslations.isNotEmpty()) {
            timedTranslations
                .minByOrNull { translated ->
                    abs(translated.timeMs!! - originalTime)
                }
                ?.takeIf { translated ->
                    abs(translated.timeMs!! - originalTime) <=
                        TRANSLATION_TIMESTAMP_TOLERANCE_MS
                }
        } else {
            translatedLines.getOrNull(index)
        }

        matched?.text?.takeIf { translatedText ->
            translatedText.isNotBlank() && translatedText != original.text
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1_000L
    return String.format(
        Locale.getDefault(),
        "%d:%02d",
        totalSeconds / 60L,
        totalSeconds % 60L
    )
}

private data class LyricLine(val timeMs: Long?, val text: String)

private val LYRIC_TIMESTAMP = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")
private val LYRIC_TAG = Regex("\\[[^]]*]")
private const val MANUAL_SCROLL_HOLD_MS = 3_000L
private const val TRANSLATION_TIMESTAMP_TOLERANCE_MS = 750L
