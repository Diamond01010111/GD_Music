package com.diamond.gdmusic

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.diamond.gdmusic.data.AppleCharts
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AppleChartsTest {
    private fun feed(): JSONObject = JSONObject().put("results", JSONArray()
        .put(JSONObject().put("id", "123").put("name", "Song").put("artistName", "Artist")
            .put("kind", "songs").put("artworkUrl100", "https://example.com/cover.jpg")))

    @Test fun metadataCanBePlayedThroughExistingSourceResolver() {
        val track = AppleCharts.parseFeed(feed()).single()
        assertEquals("apple", track.source)
        assertEquals("Artist", track.artist)
        assertTrue(track.externalMetadata)
        assertTrue(track.audioUrl.isNullOrBlank())
    }

    @Test fun cacheExpiresAfterADayAndClearPreservesCountries() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val key = "apple_chart_test_${UUID.randomUUID()}"
        val prefs = base.getSharedPreferences(key, Context.MODE_PRIVATE)
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = prefs
        }
        try {
            prefs.edit().putString("countries", "cn,jp,kr,us,gb,ca").commit()
            AppleCharts.save(context, "ca", feed())
            AppleCharts.markAttempt(context, "ca")
            assertFalse(AppleCharts.stale(context, "ca"))
            assertTrue(AppleCharts.stale(context, "ca", System.currentTimeMillis() + AppleCharts.DAY_MS + 1))
            assertEquals(1, AppleCharts.playlist(context, "ca").trackCount)
            AppleCharts.clear(context)
            assertEquals(listOf("cn", "jp", "kr", "us", "gb", "ca"), AppleCharts.countries(context))
            assertTrue(AppleCharts.tracks(context, "ca").isEmpty())
            assertTrue(AppleCharts.shouldRefresh(context, "ca"))
        } finally {
            prefs.edit().clear().commit()
        }
    }

    @Test fun selectionCapsCacheAndRejectsRemovedOrStaleRequests() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = base.getSharedPreferences("apple_cache_limit_${UUID.randomUUID()}", Context.MODE_PRIVATE)
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = prefs
        }
        try {
            val countries = com.diamond.gdmusic.data.AppleChartRegions.supported().take(12)
            AppleCharts.selectCountries(context, countries)
            countries.forEach { AppleCharts.save(context, it, feed()) }
            assertEquals(10, prefs.all.keys.count { it.startsWith("chart_") })
            val oldRevision = AppleCharts.selectionRevision(context)
            AppleCharts.selectCountries(context, listOf("ca", "us"))
            countries.forEach { AppleCharts.save(context, it, feed(), oldRevision) }
            assertTrue(prefs.all.keys.none { it.startsWith("chart_") })
            AppleCharts.save(context, "ca", feed())
            AppleCharts.save(context, "us", feed())
            AppleCharts.recordBrowsing(context, "us")
            assertEquals("apple:us", AppleCharts.playlists(context).first().id)
            val cover = AppleCharts.playlist(context, "us").coverUrl
            AppleCharts.clear(context)
            assertEquals(cover, AppleCharts.playlist(context, "us").coverUrl)
            assertEquals("apple:us", AppleCharts.playlists(context).first().id)
            AppleCharts.selectCountries(context, emptyList())
            assertTrue(AppleCharts.countries(context).isEmpty())
        } finally { prefs.edit().clear().commit() }
    }

    @Test(expected = java.io.IOException::class)
    fun rejectsEmptyOrMalformedSongData() {
        AppleCharts.parseFeed(JSONObject().put("results", JSONArray().put(JSONObject().put("id", "123"))))
    }
}
