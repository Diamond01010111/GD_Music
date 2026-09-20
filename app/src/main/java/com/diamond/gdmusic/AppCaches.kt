package com.diamond.gdmusic

import android.content.Context
import android.widget.Toast
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.disk.DiskCache
import com.diamond.gdmusic.data.NeteasePlaylistCache
import com.diamond.gdmusic.data.NeteaseToplistCache
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okio.Path.Companion.toOkioPath
import java.io.File
import java.util.UUID

/** Process-owned operation: rotating the screen cannot interrupt cache cleanup. */
object AppCaches {
    data class State(val revision: Long = 0, val clearing: Boolean = false)
    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val listeners = linkedSetOf<Runnable>()
    private var imagesInitialized = false

    @JvmStatic fun addListener(listener: Runnable) { listeners.add(listener) }
    @JvmStatic fun removeListener(listener: Runnable) { listeners.remove(listener) }

    @OptIn(DelicateCoilApi::class)
    fun initializeImages(context: Context) {
        if (imagesInitialized) return
        imagesInitialized = true
        val app = context.applicationContext
        val name = app.getSharedPreferences("image_cache_location", Context.MODE_PRIVATE)
            .getString("directory", null) ?: return
        SingletonImageLoader.setUnsafe(newImageLoader(app, name))
    }

    private fun newImageLoader(context: Context, directory: String): ImageLoader =
        ImageLoader.Builder(context).diskCache {
            DiskCache.Builder()
                .directory(File(context.cacheDir, directory).toOkioPath())
                .build()
        }.build()

    @OptIn(DelicateCoilApi::class)
    fun clear(context: Context) {
        if (mutableState.value.clearing) return
        val app = context.applicationContext
        mutableState.value = State(mutableState.value.revision, true)
        scope.launch {
            var failure: Throwable? = null
            try {
                CacheGeneration.invalidate {
                    NeteasePlaylistCache.clear(app)
                    NeteaseToplistCache.clearCachedToplists(app)
                    com.diamond.gdmusic.data.AppleCharts.clear(app)
                    listeners.toList().forEach { it.run() }
                }
                val oldLoader = SingletonImageLoader.get(app)
                val oldDisk = oldLoader.diskCache
                // Retire the old loader and disk instance. Late image requests cannot
                // write into the replacement cache, which has a different directory.
                oldLoader.shutdown()
                val directory = "gd_covers_" + UUID.randomUUID().toString()
                app.getSharedPreferences("image_cache_location", Context.MODE_PRIVATE)
                    .edit().putString("directory", directory).apply()
                SingletonImageLoader.setUnsafe(newImageLoader(app, directory))
                withContext(Dispatchers.IO) {
                    try {
                        oldDisk?.clear()
                    } finally {
                        oldDisk?.shutdown()
                    }
                    val oldDirectory = oldDisk?.directory?.toFile()
                    if (oldDirectory != null && oldDirectory.exists() && !oldDirectory.deleteRecursively()) {
                        error("部分图片缓存文件未能删除")
                    }
                    // Retry leftovers from an interrupted previous cleanup, without
                    // touching any other application data or the active image cache.
                    app.cacheDir.listFiles()?.filter {
                        it.name.startsWith("gd_covers_") && it.name != directory
                    }?.forEach {
                        if (!it.deleteRecursively()) error("部分图片缓存文件未能删除")
                    }
                }
            } catch (error: Exception) {
                failure = error
            } finally {
                mutableState.value = State(CacheGeneration.current(), false)
                Toast.makeText(app,
                    if (failure == null) "缓存已清理，页面将按需重新加载" else "部分缓存清理失败，请重试",
                    Toast.LENGTH_LONG).show()
            }
        }
    }
}
