package com.diamond.gdmusic;

import org.junit.Assume;
import org.junit.Test;
import java.util.*;
import java.util.concurrent.*;
import okhttp3.*;
import static org.junit.Assert.*;

/** Opt-in: real GD requests. Never runs automatically in the ordinary unit test suite. */
public class QilixiangLiveTest {
    private final GdMusicApi api = new GdMusicApi();
    private final OkHttpClient probe = new OkHttpClient.Builder()
            .callTimeout(15, TimeUnit.SECONDS).build();

    @Test public void diagnoseSearchMatchingUrlAndAudioAccess() throws Exception {
        Assume.assumeTrue("Enable explicitly with GD_MUSIC_LIVE_TEST=1",
                "1".equals(System.getenv("GD_MUSIC_LIVE_TEST")));
        Track reference = new Track("diagnostic", "apple", "七里香", "周杰伦", "", "", "");
        List<String> report = new ArrayList<>();
        int accessible = 0;
        int startedAt = RequestTracker.countLastFiveMinutes();
        for (String source : Arrays.asList("netease", "joox", "kuwo", "bilibili")) {
            CompletableFuture<List<Track>> search = new CompletableFuture<>();
            long start = System.nanoTime();
            String keyword = "joox".equals(source) ? ChineseText.traditional(reference.name) : reference.name;
            log(source, "SEARCH", "keyword=" + keyword);
            api.searchTracks(keyword, source, 15, 1, new GdMusicApi.SearchCallback() {
                public void onSuccess(List<Track> tracks) { search.complete(tracks); }
                public void onError(Exception error) { search.completeExceptionally(error); }
            });
            List<Track> results;
            try { results = search.get(20, TimeUnit.SECONDS); }
            catch (Exception error) {
                report.add(source + ": SEARCH_FAILED");
                log(source, "SEARCH_FAILED", safe(error));
                if (isTimeoutOrLimit(error)) break;
                continue;
            }
            log(source, "SEARCH_OK", "count=" + results.size() + " ms=" + (System.nanoTime() - start) / 1_000_000);
            List<Track> matches = GdMusicApi.matchingArtistCandidates(reference, results);
            for (Track track : results) {
                log(source, "MATCH", "title=" + track.name + " artist=" + track.artist
                        + " accepted=" + matches.contains(track));
            }
            if (matches.isEmpty()) {
                report.add(source + (results.isEmpty() ? ": SEARCH_EMPTY" : ": MATCH_REJECTED_ALL"));
                continue;
            }
            for (Track candidate : matches.subList(0, Math.min(3, matches.size()))) {
                CompletableFuture<Track> address = new CompletableFuture<>();
                api.getAudioUrl(candidate, 320, new GdMusicApi.TrackCallback() {
                    public void onSuccess(Track track) { address.complete(track); }
                    public void onError(Exception error) { address.completeExceptionally(error); }
                });
                Track resolved;
                try { resolved = address.get(20, TimeUnit.SECONDS); }
                catch (Exception error) {
                    report.add(source + ": URL_FAILED");
                    log(source, "URL_FAILED", safe(error));
                    if (isTimeoutOrLimit(error)) {
                        fail("Stopped to avoid overlapping requests: " + report);
                    }
                    continue;
                }
                // Do not print signed URLs or query parameters. Read only a small prefix.
                log(source, "URL_OK", "host=" + HttpUrl.get(resolved.audioUrl).host());
                try (Response response = probe.newCall(new Request.Builder().url(resolved.audioUrl)
                        .header("Range", "bytes=0-4095").build()).execute()) {
                    String type = response.header("Content-Type", "unknown");
                    byte[] bytes = response.peekBody(4096).bytes();
                    boolean readable = response.isSuccessful() && bytes.length > 0
                            && !type.toLowerCase(Locale.ROOT).contains("text/html")
                            && !type.toLowerCase(Locale.ROOT).contains("json");
                    log(source, "AUDIO_HTTP", "status=" + response.code() + " type=" + type
                            + " bytes=" + bytes.length + " accessible=" + readable);
                    report.add(source + (readable ? ": AUDIO_ACCESSIBLE_NOT_DECODED" : ": AUDIO_HTTP_FAILED"));
                    if (readable) { accessible++; break; }
                } catch (Exception error) {
                    log(source, "AUDIO_HTTP_FAILED", safe(error));
                    report.add(source + ": AUDIO_HTTP_FAILED");
                }
            }
        }
        System.out.println("GD_DIAG SUMMARY " + report + " GD requests="
                + (RequestTracker.countLastFiveMinutes() - startedAt));
        assertTrue("No accessible stream: " + report, accessible > 0);
        System.out.println("HTTP access succeeded; use the device test to verify Media3 preparation.");
    }
    private static void log(String source, String stage, String detail) {
        System.out.println("GD_DIAG [" + source + "] " + stage + " " + detail);
    }
    private static boolean isTimeoutOrLimit(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof TimeoutException || t instanceof GdMusicApi.RateLimitException) return true;
        }
        return false;
    }
    private static String safe(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null) root = root.getCause();
        return root.getClass().getSimpleName() + ": " + String.valueOf(root.getMessage())
                .replaceAll("https?://[^\\s\\\"]+", "[URL]");
    }
}
