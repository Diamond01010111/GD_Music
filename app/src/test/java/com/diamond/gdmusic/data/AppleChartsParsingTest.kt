package com.diamond.gdmusic.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AppleChartsParsingTest {
    private fun song(id: String, name: String = "Song") = JSONObject()
        .put("id", id).put("name", name).put("artistName", "Artist")
        .put("kind", "songs").put("artworkUrl100", "https://example.com/cover.jpg")

    @Test fun preservesRankingAndMarksMetadataForSourceMatching() {
        val feed = JSONObject().put("results", JSONArray().put(song("2")).put(song("1")))
        val songs = AppleCharts.parseFeed(feed)
        assertEquals(listOf("2", "1"), songs.map { it.id })
        assertTrue(songs.all { it.externalMetadata && it.source == "apple" && it.audioUrl.isNullOrBlank() })
    }

    @Test fun skipsInvalidRowsAndDuplicateSongs() {
        val feed = JSONObject().put("results", JSONArray().put(song("2"))
            .put(JSONObject().put("id", "missing_fields")).put(song("2")).put(song("3")))
        assertEquals(listOf("2", "3"), AppleCharts.parseFeed(feed).map { it.id })
    }

    @Test fun resultLimitIsFiftyEvenIfServerReturnsMore() {
        val entries = JSONArray()
        repeat(80) { entries.put(song(it.toString())) }
        assertEquals(50, AppleCharts.parseFeed(JSONObject().put("results", entries)).size)
    }

    @Test(expected = java.io.IOException::class)
    fun refusesToReplaceCacheWithAnEmptyChart() {
        AppleCharts.parseFeed(JSONObject().put("results", JSONArray()))
    }
}
