package com.diamond.gdmusic.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.diamond.gdmusic.*
import com.diamond.gdmusic.model.supportedMusicSources
import kotlinx.coroutines.*
import kotlin.coroutines.resume

@Composable
fun AutoSourcesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val api = remember { GdMusicApi() }
    var selected by remember {
        AutoSourcePreferences.initialize(context)
        mutableStateOf(AutoSourcePreferences.selected().toSet())
    }
    var keyword by remember { mutableStateOf("七里香") }
    var running by remember { mutableStateOf(false) }
    val statuses = remember { mutableStateMapOf<String, String>() }
    val choices = remember { supportedMusicSources.filter { it.value != "apple" } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自动换源") },
        text = {
            Column {
                Text("仅自动尝试勾选的音源；全部取消可关闭自动换源。直接播放和手动换源不受影响。")
                OutlinedTextField(value = keyword, onValueChange = { keyword = it; statuses.clear() },
                    label = { Text("验证搜索词") }, singleLine = true, enabled = !running,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                Text("验证按当前默认音质检查搜索结果中最多 3 首歌曲的播放地址，会计入 API 次数。失败仅表示本次样本未获得地址。",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
                TextButton(enabled = !running && selected.isNotEmpty() && keyword.isNotBlank(), onClick = {
                    val targets = choices.filter { it.value in selected }
                    val query = keyword.trim()
                    val bitrate = PlaybackPreferences.defaultBitrate(context)
                    statuses.clear()
                    running = true
                    scope.launch {
                        try {
                            for (source in targets) {
                                if (RequestTracker.countLastFiveMinutes() >= 50) {
                                    targets.filter { it.value !in statuses }.forEach { statuses[it.value] = "额度不足，未验证" }
                                    break
                                }
                                statuses[source.value] = "验证中…"
                                statuses[source.value] = try {
                                    withTimeout(30_000) { verifySource(api, source.value, query, bitrate) }
                                } catch (_: TimeoutCancellationException) { "验证超时" }
                            }
                        } finally { running = false }
                    }
                }) { Text(if (running) "正在验证…" else "验证已选音源") }
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(choices, key = { it.value }) { source ->
                        val checked = source.value in selected
                        fun toggle() {
                            selected = if (checked) selected - source.value else selected + source.value
                            statuses.remove(source.value)
                        }
                        ListItem(headlineContent = { Text(source.label) },
                            supportingContent = { Text(statuses[source.value] ?: "尚未验证") },
                            trailingContent = { Checkbox(checked, onCheckedChange = { toggle() }, enabled = !running) },
                            modifier = Modifier.clickable(enabled = !running) { toggle() })
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = !running, onClick = {
            AutoSourcePreferences.save(choices.filter { it.value in selected }.map { it.value })
            onDismiss()
        }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private suspend fun verifySource(api: GdMusicApi, source: String, keyword: String, bitrate: Int): String {
    val search = suspendCancellableCoroutine<Result<List<Track>>> { continuation ->
        api.searchTracks(keyword, source, 3, 1, object : GdMusicApi.SearchCallback {
            override fun onSuccess(tracks: MutableList<Track>) {
                if (continuation.isActive) continuation.resume(Result.success(tracks))
            }
            override fun onError(error: Exception) {
                if (continuation.isActive) continuation.resume(Result.failure(error))
            }
        })
    }
    if (search.isFailure) return if (search.exceptionOrNull() is GdMusicApi.RateLimitException)
        "额度不足，未完成验证" else "搜索失败，未完成验证"
    val tracks = search.getOrThrow().distinctBy { it.id }.take(3)
    if (tracks.isEmpty()) return "搜索无结果，请更换搜索词"
    for (track in tracks) {
        currentCoroutineContext().ensureActive()
        // The selected provider owns this validation, even if upstream returns another source field.
        track.source = source
        val result = suspendCancellableCoroutine<Result<Track>> { continuation ->
            api.getAudioUrl(track, bitrate, object : GdMusicApi.TrackCallback {
                override fun onSuccess(resolved: Track) {
                    if (continuation.isActive) continuation.resume(Result.success(resolved))
                }
                override fun onError(error: Exception) {
                    if (continuation.isActive) continuation.resume(Result.failure(error))
                }
            })
        }
        if (result.isSuccess) return "可获取播放地址（不保证全部歌曲可播）"
        if (result.exceptionOrNull() is GdMusicApi.RateLimitException) return "额度不足，未完成验证"
    }
    return "本次样本未获取到播放地址"
}
