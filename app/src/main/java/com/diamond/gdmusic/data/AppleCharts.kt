package com.diamond.gdmusic.data

import android.content.Context
import androidx.core.os.ConfigurationCompat
import com.diamond.gdmusic.CacheGeneration
import com.diamond.gdmusic.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Apple's public RSS charts provide metadata, not audio URLs. */
object AppleCharts {
    const val PREFIX = "apple:"
    const val DAY_MS = 24L * 60 * 60 * 1000
    private const val RETRY_MS = 15L * 60 * 1000
    private fun preferences(context: Context) = context.applicationContext
        .getSharedPreferences("apple_charts", Context.MODE_PRIVATE)

    fun countries(context: Context): List<String> {
        val prefs = preferences(context)
        val saved = prefs.getString("countries", null)
        if (saved != null) {
            val countries = saved.split(',').filter { it in AppleChartRegions.supported() }
                .distinct().take(10)
            return countries
        }
        // System region is only a default, not GPS/IP geolocation; no permission needed.
        val country = ConfigurationCompat.getLocales(context.resources.configuration)[0]?.country
            ?.takeIf { it.isNotBlank() } ?: Locale.getDefault().country
        val countries = AppleChartRegions.defaults(country).filter { it in AppleChartRegions.supported() }
        prefs.edit().putString("countries", countries.joinToString(",")).apply()
        return countries
    }

    fun countryName(country: String): String = when (country) {
        "cn" -> "中国"; "jp" -> "日本"; "kr" -> "韩国"; "us" -> "美国"; "gb" -> "英国"; "xk" -> "科索沃"
        else -> Locale("", country.uppercase(Locale.ROOT)).getDisplayCountry(Locale.SIMPLIFIED_CHINESE)
            .ifBlank { country.uppercase(Locale.ROOT) }
    }

    private fun snapshot(context: Context, country: String): JSONObject? = runCatching {
        JSONObject(preferences(context).getString("chart_$country", "") ?: "")
    }.getOrNull()

    fun tracks(context: Context, country: String): List<Track> = runCatching {
        snapshot(context, country)?.getJSONObject("feed")?.let(::parseFeed).orEmpty()
    }.getOrDefault(emptyList())

    // Reuse the common playlist UI model; the namespace keeps Apple IDs out of Netease APIs.
    fun playlist(context: Context, country: String): NeteasePlaylist {
        val songs = tracks(context, country)
        return NeteasePlaylist(PREFIX + country, "${countryName(country)}热门歌曲",
            cover(context, country), songs.size, "")
    }

    fun playlists(context: Context): List<NeteasePlaylist> {
        val selected = countries(context)
        val recent = preferences(context).getString("browsed", "").orEmpty().split(',')
        return (recent.filter { it in selected } + selected).distinct().take(10).map { playlist(context, it) }
    }

    @Synchronized
    fun selectCountries(context: Context, selected: List<String>) {
        val clean = selected.filter { it in AppleChartRegions.supported() }.distinct().take(10)
        val prefs = preferences(context)
        val editor = prefs.edit().putString("countries", clean.joinToString(","))
            .putLong("selection_revision", selectionRevision(context) + 1)
        prefs.all.keys.filter { key ->
            (key.startsWith("chart_") && key.removePrefix("chart_") !in clean) ||
                (key.startsWith("attempt_") && key.removePrefix("attempt_") !in clean)
        }.forEach { editor.remove(it) }
        editor.apply()
    }

    fun selectionRevision(context: Context): Long = preferences(context).getLong("selection_revision", 0)

    fun recordBrowsing(context: Context, country: String) {
        val prefs = preferences(context)
        val recent = prefs.getString("browsed", "").orEmpty().split(',').filter { it.isNotBlank() }
        prefs.edit().putString("browsed", (listOf(country) + recent).distinct().take(10).joinToString(",")).apply()
    }

    private fun cover(context: Context, country: String): String {
        val prefs = preferences(context)
        val key = "color_$country"
        val color = if (prefs.contains(key)) prefs.getInt(key, 0) else {
            kotlin.random.Random.nextInt(8).also { prefs.edit().putInt(key, it).apply() }
        }
        return "apple-chart:$country:$color"
    }

    fun stale(context: Context, country: String, now: Long = System.currentTimeMillis()): Boolean {
        val savedAt = snapshot(context, country)?.optLong("savedAt") ?: 0L
        return savedAt == 0L || now < savedAt || now - savedAt >= DAY_MS || tracks(context, country).isEmpty()
    }

    fun shouldRefresh(context: Context, country: String): Boolean {
        val now = System.currentTimeMillis()
        val attempt = preferences(context).getLong("attempt_$country", 0L)
        return stale(context, country, now) && (attempt == 0L || now < attempt || now - attempt >= RETRY_MS)
    }

    @Synchronized
    fun markAttempt(context: Context, country: String) {
        if (country !in countries(context)) return
        preferences(context).edit().putLong("attempt_$country", System.currentTimeMillis()).apply()
    }

    @Synchronized
    fun save(context: Context, country: String, feed: JSONObject, revision: Long = selectionRevision(context)) {
        // Removed or reconfigured charts cannot be restored by an older request.
        if (revision != selectionRevision(context) || country !in countries(context)) return
        preferences(context).edit().putString("chart_$country", JSONObject()
            .put("savedAt", System.currentTimeMillis()).put("feed", feed).toString()).apply()
    }

    @Synchronized
    fun clear(context: Context) {
        val prefs = preferences(context)
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("chart_") || it.startsWith("attempt_") }
            .forEach { editor.remove(it) }
        editor.apply() // Keep the initial country selection.
    }

    internal fun parseFeed(feed: JSONObject): List<Track> {
        val array = feed.getJSONArray("results")
        val songs = (0 until minOf(array.length(), 50)).mapNotNull { index ->
            val song = array.optJSONObject(index) ?: return@mapNotNull null
            val id = song.optString("id").trim()
            val name = song.optString("name").trim()
            val artist = song.optString("artistName").trim()
            if (id.isBlank() || name.isBlank() || artist.isBlank() ||
                id == "null" || name == "null" || artist == "null" || song.optString("kind") != "songs") {
                return@mapNotNull null
            }
            Track(id, "apple", name, artist, "", "", "").apply {
                picUrl = song.optString("artworkUrl100").takeIf { it.startsWith("https://") }.orEmpty()
                externalMetadata = true
            }
        }.distinctBy { it.id }
        if (songs.isEmpty()) throw IOException("Apple Music 榜单暂无有效歌曲")
        return songs
    }
}

class AppleChartsRepository(context: Context) {
    private val context = context.applicationContext
    private val generation = CacheGeneration.current()
    private val countryLocks = ConcurrentHashMap<String, Mutex>()
    private val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()

    suspend fun refresh(force: Boolean = false): List<String> = coroutineScope {
        AppleCharts.countries(context).filter { force || AppleCharts.shouldRefresh(context, it) }
            .map { country -> async { load(country).exceptionOrNull()?.let { AppleCharts.countryName(country) } } }
            .awaitAll().filterNotNull()
    }

    suspend fun loadTracks(id: String): Result<List<Track>> {
        val country = id.removePrefix(AppleCharts.PREFIX)
        if (!id.startsWith(AppleCharts.PREFIX) || !country.matches(Regex("[a-z]{2}"))) {
            return Result.failure(IllegalArgumentException("无效的 Apple Music 榜单"))
        }
        val cached = AppleCharts.tracks(context, country)
        if (cached.isNotEmpty() && !AppleCharts.stale(context, country)) return Result.success(cached)
        val fresh = load(country)
        // Network errors leave the last successful chart usable offline.
        return if (fresh.isFailure && cached.isNotEmpty() && CacheGeneration.isCurrent(generation)) {
            Result.success(cached)
        } else fresh
    }

    private suspend fun load(country: String): Result<List<Track>> =
        countryLocks.getOrPut(country) { Mutex() }.withLock {
            fetch(country)
        }

    private suspend fun fetch(country: String): Result<List<Track>> = withContext(Dispatchers.IO) {
        try {
            val selectionRevision = AppleCharts.selectionRevision(context)
            if (!CacheGeneration.runIfCurrent(generation) { AppleCharts.markAttempt(context, country) }) {
                throw CancellationException("缓存已清理")
            }
            val request = Request.Builder()
                .url("https://rss.marketingtools.apple.com/api/v2/$country/music/most-played/50/songs.json")
                .build()
            val feed = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Apple Music HTTP ${response.code}")
                JSONObject(response.body?.string() ?: throw IOException("榜单响应为空")).getJSONObject("feed")
            }
            val tracks = AppleCharts.parseFeed(feed)
            if (!CacheGeneration.runIfCurrent(generation) { AppleCharts.save(context, country, feed, selectionRevision) }) {
                throw CancellationException("缓存已清理")
            }
            Result.success(tracks)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
}
