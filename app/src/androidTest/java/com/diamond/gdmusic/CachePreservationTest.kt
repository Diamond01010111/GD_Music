package com.diamond.gdmusic

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.diamond.gdmusic.data.NeteasePlaylist
import com.diamond.gdmusic.data.NeteasePlaylistCache
import com.diamond.gdmusic.data.NeteaseToplistCache
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CachePreservationTest {
    @Test fun clearingCachedListsPreservesIdentityAndBrowsingOrder() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "cache_test_${UUID.randomUUID()}_"
        val opened = mutableListOf<SharedPreferences>()
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                base.getSharedPreferences(prefix + name, mode).also { opened.add(it) }
        }
        try {
            val playlist = NeteasePlaylist("123", "保留浏览记录", "", 12, "456")
            NeteasePlaylistCache.saveUserId(context, "456")
            NeteasePlaylistCache.save(context, "456", listOf(playlist))
            NeteasePlaylistCache.markRefreshAttempt(context)
            NeteaseToplistCache.saveToplists(context, listOf(playlist))
            NeteaseToplistCache.recordBrowsing(context, playlist)
            assertFalse(NeteasePlaylistCache.shouldRefresh(context))
            assertFalse(NeteaseToplistCache.shouldRefresh(context))

            NeteasePlaylistCache.clear(context)
            NeteaseToplistCache.clearCachedToplists(context)

            assertEquals("456", NeteasePlaylistCache.savedUserId(context))
            assertTrue(NeteasePlaylistCache.read(context).isEmpty())
            assertTrue(NeteasePlaylistCache.shouldRefresh(context))
            assertTrue(NeteaseToplistCache.readToplists(context).isEmpty())
            assertTrue(NeteaseToplistCache.shouldRefresh(context))
            assertEquals(listOf(playlist), NeteaseToplistCache.recentlyBrowsed(context))
        } finally {
            opened.forEach { it.edit().clear().commit() }
        }
    }
}
