package com.diamond.gdmusic

import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Explicit opt-in. Uses an isolated silent player, never touches the app's queue. */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class QilixiangDeviceTest {
    private fun describe(error: Throwable): String {
        val root = generateSequence(error) { it.cause }.last()
        return "${root.javaClass.simpleName}: ${root.message.orEmpty()}"
            .replace(Regex("https?://[^\\s\"]+"), "[URL]")
    }

    @Test fun locateFailureOnDevice() {
        assumeTrue("Pass -e gdLive 1", InstrumentationRegistry.getArguments().getString("gdLive") == "1")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        RequestTracker.initialize(context)
        val reference = Track("diagnostic", "apple", "七里香", "周杰伦", "", "", "")
        val api = object : GdMusicApi() {
            override fun searchTracks(keyword: String, source: String, count: Int, page: Int, callback: SearchCallback) {
                Log.i("GD_DIAG", "$source SEARCH keyword=$keyword")
                super.searchTracks(keyword, source, count, page, object : SearchCallback {
                    override fun onSuccess(tracks: MutableList<Track>) {
                        val matches = GdMusicApi.matchingArtistCandidates(reference, tracks)
                        Log.i("GD_DIAG", "$source SEARCH_OK count=${tracks.size} matches=${matches.size}")
                        tracks.forEach { Log.i("GD_DIAG", "$source MATCH ${it.name} / ${it.artist} accepted=${it in matches}") }
                        callback.onSuccess(tracks)
                    }
                    override fun onError(error: Exception) {
                        Log.e("GD_DIAG", "$source SEARCH_FAILED ${describe(error)}")
                        callback.onError(error)
                    }
                })
            }
            override fun getAudioUrl(track: Track, br: Int, callback: TrackCallback) {
                Log.i("GD_DIAG", "${track.source} URL_REQUEST id=${track.id} br=$br")
                super.getAudioUrl(track, br, object : TrackCallback {
                    override fun onSuccess(result: Track) {
                        Log.i("GD_DIAG", "${track.source} URL_OK")
                        callback.onSuccess(result)
                    }
                    override fun onError(error: Exception) {
                        Log.e("GD_DIAG", "${track.source} URL_FAILED ${describe(error)}")
                        callback.onError(error)
                    }
                })
            }
        }
        var playable = 0
        for (source in listOf("netease", "joox", "kuwo", "bilibili")) {
            if (RequestTracker.countLastFiveMinutes() >= 50) {
                Log.e("GD_DIAG", "RATE_LIMIT: stop diagnostic")
                break
            }
            val result = CompletableFuture<Track>()
            val active = AtomicBoolean(true)
            api.resolveTrackFromSource(reference, source, 320, object : GdMusicApi.TrackCallback {
                override fun isActive() = active.get()
                override fun onSuccess(track: Track) { result.complete(track) }
                override fun onError(error: Exception) { result.completeExceptionally(error) }
            })
            val track = try {
                result.get(35, TimeUnit.SECONDS)
            } catch (error: Exception) {
                Log.e("GD_DIAG", "$source RESOLVE_FAILED ${describe(error)}")
                if (error is java.util.concurrent.TimeoutException) break
                continue
            } finally { active.set(false) }
            val ready = CountDownLatch(1)
            val failure = AtomicReference<PlaybackException>()
            lateinit var player: ExoPlayer
            instrumentation.runOnMainSync {
                player = ExoPlayer.Builder(context).build()
                player.volume = 0f
                player.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        Log.i("GD_DIAG", "$source PLAYER_STATE=$state")
                        if (state == Player.STATE_READY) ready.countDown()
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        failure.set(error)
                        Log.e("GD_DIAG", "$source PLAYER_FAILED ${error.errorCodeName} cause=${describe(error)}")
                        ready.countDown()
                    }
                })
                player.setMediaItem(MediaItem.fromUri(track.audioUrl))
                player.prepare()
            }
            try {
                val completed = ready.await(20, TimeUnit.SECONDS)
                if (completed && failure.get() == null) {
                    playable++
                    Log.i("GD_DIAG", "$source PLAYER_READY")
                } else if (!completed) Log.e("GD_DIAG", "$source PLAYER_TIMEOUT")
            } finally { instrumentation.runOnMainSync { player.release() } }
        }
        assertTrue("No source reached PLAYER_READY. Filter Logcat by GD_DIAG.", playable > 0)
    }
}
