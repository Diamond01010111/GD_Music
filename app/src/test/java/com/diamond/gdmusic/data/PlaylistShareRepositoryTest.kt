package com.diamond.gdmusic.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistShareRepositoryTest {
    @Test
    fun extractsShareCodeFromManualInput() {
        assertEquals("GDTZ6VBJGT", PlaylistShareRepository.extractShareId("gdtz6vbjgt"))
    }

    @Test
    fun extractsShareCodeFromAppLink() {
        assertEquals(
            "GDTZ6VBJGT",
            PlaylistShareRepository.extractShareId(
                "https://gd-music-share-api.gdmusic.workers.dev/import/GDTZ6VBJGT"
            )
        )
    }

    @Test
    fun rejectsInputWithoutShareCode() {
        assertNull(PlaylistShareRepository.extractShareId("not-a-share-code"))
    }
}
