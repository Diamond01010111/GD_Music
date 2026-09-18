package com.diamond.gdmusic.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.diamond.gdmusic.PlaybackMode
import com.diamond.gdmusic.Track
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun MiniPlayer(
    track: Track?,
    artworkUrl: String,
    isPlaying: Boolean,
    playMode: PlaybackMode,

    // 0f 至 1f
    playbackProgress: Float,

    onPlayPause: () -> Unit,
    onSwitchPlayMode: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenDetails: () -> Unit,
    onSwipePrevious: () -> Unit,
    onSwipeNext: () -> Unit
) {
    val animationScope = rememberCoroutineScope()
    val latestTrackId by rememberUpdatedState(track?.id)
    var contentOffset by remember { mutableFloatStateOf(0f) }
    var contentWidth by remember { mutableIntStateOf(1) }
    var isSwipeAnimating by remember { mutableStateOf(false) }
    var pendingTrackId by remember { mutableStateOf<String?>(null) }
    var exitDirection by remember { mutableIntStateOf(0) }

    suspend fun animateContentTo(target: Float) {
        animate(
            initialValue = contentOffset,
            targetValue = target,
            animationSpec = tween(durationMillis = SWIPE_ANIMATION_DURATION_MS)
        ) { value, _ ->
            contentOffset = value
        }
    }

    LaunchedEffect(track?.id) {
        val previousId = pendingTrackId ?: return@LaunchedEffect
        if (track?.id == previousId) return@LaunchedEffect

        // 旧歌曲完全滑出后才切歌；新封面与文字从另一侧滑入。
        contentOffset = -exitDirection * contentWidth.toFloat()
        pendingTrackId = null
        animateContentTo(0f)
        isSwipeAnimating = false
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 12.dp,
                vertical = 6.dp
            )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 10.dp,
                        end = 10.dp,
                        top = 10.dp,
                        bottom = 8.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clipToBounds()
                        .onSizeChanged { contentWidth = it.width.coerceAtLeast(1) }
                        .clickable(
                            enabled = track != null && !isSwipeAnimating,
                            onClick = onOpenDetails
                        )
                        .pointerInput(track?.id, isSwipeAnimating) {
                            val swipeThreshold = 48.dp.toPx()
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    if (!isSwipeAnimating) contentOffset = 0f
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    if (!isSwipeAnimating) {
                                        contentOffset = (contentOffset + dragAmount)
                                            .coerceIn(
                                                -contentWidth.toFloat(),
                                                contentWidth.toFloat()
                                            )
                                    }
                                },
                                onDragCancel = {
                                    if (!isSwipeAnimating) {
                                        animationScope.launch { animateContentTo(0f) }
                                    }
                                },
                                onDragEnd = {
                                    if (
                                        track != null &&
                                        !isSwipeAnimating &&
                                        abs(contentOffset) >= swipeThreshold
                                    ) {
                                        val direction = if (contentOffset < 0f) -1 else 1
                                        animationScope.launch {
                                            isSwipeAnimating = true
                                            animateContentTo(
                                                direction * contentWidth.toFloat()
                                            )

                                            val previousId = latestTrackId
                                            pendingTrackId = previousId
                                            exitDirection = direction
                                            if (direction < 0) onSwipeNext() else onSwipePrevious()

                                            // 队列只有一首或无法切换时，将旧内容滑回原位。
                                            delay(SWIPE_TRACK_CHANGE_TIMEOUT_MS)
                                            if (
                                                pendingTrackId == previousId &&
                                                latestTrackId == previousId
                                            ) {
                                                pendingTrackId = null
                                                animateContentTo(0f)
                                                isSwipeAnimating = false
                                            }
                                        }
                                    } else if (!isSwipeAnimating) {
                                        animationScope.launch { animateContentTo(0f) }
                                    }
                                }
                            )
                        }
                ) {
                    Row(
                        modifier = Modifier.graphicsLayer {
                            translationX = contentOffset
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AlbumArtwork(
                            artworkUrl = artworkUrl,
                            songName = track?.name
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp)
                                .basicMarquee(
                                    iterations = Int.MAX_VALUE,
                                    initialDelayMillis = MARQUEE_INITIAL_DELAY_MS,
                                    repeatDelayMillis = MARQUEE_REPEAT_DELAY_MS
                                )
                        ) {
                            Text(
                                text = track?.name ?: "暂未播放",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                softWrap = false
                            )

                            Text(
                                text = track?.artist ?: "请选择一首歌曲",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                // 三种模式分别使用不同图标
                IconButton(
                    onClick = onSwitchPlayMode,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = when (playMode) {
                            PlaybackMode.LIST_LOOP ->
                                Icons.Default.Repeat

                            PlaybackMode.SINGLE_LOOP ->
                                Icons.Default.RepeatOne

                            PlaybackMode.RANDOM ->
                                Icons.Default.Shuffle
                        },
                        contentDescription = when (playMode) {
                            PlaybackMode.LIST_LOOP ->
                                "列表循环"

                            PlaybackMode.SINGLE_LOOP ->
                                "单曲循环"

                            PlaybackMode.RANDOM ->
                                "随机播放"
                        }
                    )
                }

                IconButton(
                    enabled = track != null,
                    onClick = onPlayPause,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) {
                            Icons.Default.Pause
                        } else {
                            Icons.Default.PlayArrow
                        },
                        contentDescription = if (isPlaying) {
                            "暂停"
                        } else {
                            "播放"
                        }
                    )
                }

                IconButton(
                    onClick = onOpenQueue,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = "当前播放列表"
                    )
                }
            }

            // 沿迷你播放器底部边缘显示进度
            LinearProgressIndicator(
                progress = {
                    playbackProgress.coerceIn(0f, 1f)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor =
                    MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

private const val SWIPE_ANIMATION_DURATION_MS = 180
private const val SWIPE_TRACK_CHANGE_TIMEOUT_MS = 700L
private const val MARQUEE_INITIAL_DELAY_MS = 1_200
private const val MARQUEE_REPEAT_DELAY_MS = 900

@Composable
private fun AlbumArtwork(
    artworkUrl: String,
    songName: String?
) {
    if (
        artworkUrl.isNotBlank() &&
        artworkUrl != "null"
    ) {
        AsyncImage(
            model = artworkUrl,
            contentDescription = songName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null
            )
        }
    }
}
