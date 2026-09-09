package com.diamond.gdmusic;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class GdMusicApi {

    private static final String[] PLAYABLE_SOURCES = {
            "netease",
            "joox",
            "bilibili"
    };

    private final OkHttpClient client = new OkHttpClient();

    public interface SearchCallback {
        void onSuccess(List<Track> tracks);
        void onError(Exception e);
    }

    public interface TrackCallback {
        void onSuccess(Track track);
        void onError(Exception e);
    }

    public void resolveExternalTrack(
            Track reference,
            int br,
            TrackCallback callback
    ) {
        List<String> sources = orderedSources(reference, null);
        tryResolveAudioSource(reference, br, sources, 0, callback);
    }

    public void resolveTrackFromSource(
            Track reference,
            String source,
            int br,
            TrackCallback callback
    ) {
        if (!validReference(reference) || source == null || source.trim().isEmpty()) {
            callback.onError(new IllegalArgumentException("歌曲或目标音源无效"));
            return;
        }
        List<String> sources = new ArrayList<>();
        sources.add(source);
        tryResolveAudioSource(reference, br, sources, 0, callback);
    }

    public void resolveLyrics(
            Track reference,
            String preferredSource,
            TrackCallback callback
    ) {
        if (!validReference(reference)) {
            callback.onError(new IllegalArgumentException("歌曲缺少歌曲名或歌手"));
            return;
        }
        tryResolveLyricSource(
                reference,
                orderedSources(reference, preferredSource),
                0,
                callback
        );
    }

    private void tryResolveAudioSource(
            Track reference,
            int br,
            List<String> sources,
            int sourceIndex,
            TrackCallback callback
    ) {
        if (!validReference(reference)) {
            callback.onError(new IllegalArgumentException("外部歌曲缺少歌曲名或歌手"));
            return;
        }
        if (sourceIndex >= sources.size()) {
            callback.onError(new Exception(
                    "没有找到歌手匹配且可播放的音源："
                            + reference.name + " - " + reference.artist
            ));
            return;
        }

        String source = sources.get(sourceIndex);
        searchTracks(reference.name, source, 15, 1, new SearchCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                List<Track> matches = matchingArtistCandidates(reference, tracks);
                tryPlayableCandidate(
                        reference,
                        br,
                        sources,
                        sourceIndex,
                        matches,
                        0,
                        callback
                );
            }

            @Override
            public void onError(Exception e) {
                tryResolveAudioSource(reference, br, sources, sourceIndex + 1, callback);
            }
        });
    }

    private void tryPlayableCandidate(
            Track reference,
            int br,
            List<String> sources,
            int sourceIndex,
            List<Track> candidates,
            int candidateIndex,
            TrackCallback callback
    ) {
        if (candidateIndex >= candidates.size()) {
            tryResolveAudioSource(reference, br, sources, sourceIndex + 1, callback);
            return;
        }

        Track candidate = candidates.get(candidateIndex);
        getAudioUrl(candidate, br, new TrackCallback() {
            @Override
            public void onSuccess(Track resolved) {
                if (!present(resolved.audioUrl)) {
                    tryPlayableCandidate(
                            reference,
                            br,
                            sources,
                            sourceIndex,
                            candidates,
                            candidateIndex + 1,
                            callback
                    );
                    return;
                }
                if (!present(resolved.picUrl) && present(reference.picUrl)) {
                    resolved.picUrl = reference.picUrl;
                }
                resolved.externalMetadata = false;
                resolved.requestedBitrate = reference.requestedBitrate;
                callback.onSuccess(resolved);
            }

            @Override
            public void onError(Exception e) {
                tryPlayableCandidate(
                        reference,
                        br,
                        sources,
                        sourceIndex,
                        candidates,
                        candidateIndex + 1,
                        callback
                );
            }
        });
    }

    private void tryResolveLyricSource(
            Track reference,
            List<String> sources,
            int sourceIndex,
            TrackCallback callback
    ) {
        if (sourceIndex >= sources.size()) {
            callback.onError(new Exception("所有可用来源均未找到歌词"));
            return;
        }

        String source = sources.get(sourceIndex);
        if (source.equals(reference.source) && present(reference.lyricId)) {
            Track direct = copyTrack(reference);
            getLyric(direct, new TrackCallback() {
                @Override
                public void onSuccess(Track resolved) {
                    if (present(resolved.lyric)) {
                        callback.onSuccess(resolved);
                    } else {
                        tryResolveLyricSource(reference, sources, sourceIndex + 1, callback);
                    }
                }

                @Override
                public void onError(Exception e) {
                    tryResolveLyricSource(reference, sources, sourceIndex + 1, callback);
                }
            });
            return;
        }

        searchTracks(reference.name, source, 15, 1, new SearchCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                tryLyricCandidate(
                        reference,
                        sources,
                        sourceIndex,
                        matchingArtistCandidates(reference, tracks),
                        0,
                        callback
                );
            }

            @Override
            public void onError(Exception e) {
                tryResolveLyricSource(reference, sources, sourceIndex + 1, callback);
            }
        });
    }

    private void tryLyricCandidate(
            Track reference,
            List<String> sources,
            int sourceIndex,
            List<Track> candidates,
            int candidateIndex,
            TrackCallback callback
    ) {
        if (candidateIndex >= candidates.size()) {
            tryResolveLyricSource(reference, sources, sourceIndex + 1, callback);
            return;
        }

        Track candidate = candidates.get(candidateIndex);
        if (!present(candidate.lyricId)) {
            tryLyricCandidate(
                    reference,
                    sources,
                    sourceIndex,
                    candidates,
                    candidateIndex + 1,
                    callback
            );
            return;
        }
        getLyric(candidate, new TrackCallback() {
            @Override
            public void onSuccess(Track resolved) {
                if (present(resolved.lyric)) {
                    callback.onSuccess(resolved);
                } else {
                    tryLyricCandidate(
                            reference,
                            sources,
                            sourceIndex,
                            candidates,
                            candidateIndex + 1,
                            callback
                    );
                }
            }

            @Override
            public void onError(Exception e) {
                tryLyricCandidate(
                        reference,
                        sources,
                        sourceIndex,
                        candidates,
                        candidateIndex + 1,
                        callback
                );
            }
        });
    }

    private List<String> orderedSources(Track reference, String preferredSource) {
        List<String> sources = new ArrayList<>();
        addSource(sources, preferredSource);
        addSource(sources, reference == null ? null : reference.source);
        for (String source : PLAYABLE_SOURCES) {
            addSource(sources, source);
        }
        return sources;
    }

    private void addSource(List<String> sources, String source) {
        if (source != null && !source.trim().isEmpty() && !sources.contains(source)) {
            sources.add(source);
        }
    }

    private boolean validReference(Track reference) {
        return reference != null
                && present(reference.name)
                && present(reference.artist);
    }

    private List<Track> matchingArtistCandidates(Track reference, List<Track> tracks) {
        List<Track> exactTitleMatches = new ArrayList<>();
        List<Track> otherMatches = new ArrayList<>();
        String referenceTitle = normalizeText(reference.name);
        if (tracks == null) {
            return exactTitleMatches;
        }
        for (Track candidate : tracks) {
            if (!hasMatchingArtist(reference.artist, candidate.artist)) {
                continue;
            }
            if (referenceTitle.equals(normalizeText(candidate.name))) {
                exactTitleMatches.add(candidate);
            } else {
                otherMatches.add(candidate);
            }
        }
        exactTitleMatches.addAll(otherMatches);
        return exactTitleMatches;
    }

    static boolean hasMatchingArtist(String first, String second) {
        Set<String> firstArtists = normalizedArtists(first);
        Set<String> secondArtists = normalizedArtists(second);
        for (String artist : firstArtists) {
            if (secondArtists.contains(artist)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> normalizedArtists(String raw) {
        Set<String> artists = new LinkedHashSet<>();
        if (raw == null || raw.trim().isEmpty()) {
            return artists;
        }
        String[] parts = raw.split(
                "(?i)\\s*(?:、|,|，|/|&|\\bfeat\\.?\\b|\\bft\\.?\\b)\\s*"
        );
        for (String part : parts) {
            String normalized = normalizeText(part);
            if (!normalized.isEmpty()) {
                artists.add(normalized);
            }
        }
        return artists;
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private Track copyTrack(Track source) {
        Track copy = new Track(
                source.id,
                source.source,
                source.name,
                source.artist,
                source.album,
                source.picId,
                source.lyricId
        );
        copy.audioUrl = source.audioUrl;
        copy.audioUrlCachedAt = source.audioUrlCachedAt;
        copy.picUrl = source.picUrl;
        copy.externalMetadata = source.externalMetadata;
        copy.requestedBitrate = source.requestedBitrate;
        return copy;
    }

    private boolean present(String value) {
        return value != null && !value.isEmpty() && !"null".equals(value);
    }

    public void searchTracks(String keywordRaw, SearchCallback callback) {
        searchTracks(keywordRaw, 5, 1, callback);
    }

    public void searchTracks(String keywordRaw, int count, int page, SearchCallback callback) {
        searchTracks(keywordRaw, "netease", count, page, callback);
    }

    public void searchTracks(String keywordRaw, String source, int count, int page, SearchCallback callback) {
        try {
            String keyword = URLEncoder.encode(keywordRaw, "UTF-8");

            String url = "https://music-api.gdstudio.xyz/api.php"
                    + "?types=search"
                    + "&source=" + source
                    + "&name=" + keyword
                    + "&count=" + count
                    + "&pages=" + page;

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .get()
                    .build();

            RequestTracker.record();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError(e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String trimmed = requireSuccessfulBody(response, "搜索");

                        if (!trimmed.startsWith("[")) {
                            throw invalidResponse("搜索", "顶层数据不是 JSON 数组", trimmed);
                        }

                        JSONArray arr = new JSONArray(trimmed);
                        List<Track> tracks = new ArrayList<>();

                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject item = arr.optJSONObject(i);
                            if (item == null) {
                                continue;
                            }

                            String id = normalizedValue(item.opt("id"));
                            String name = normalizedValue(item.opt("name"));
                            String artist = cleanArtist(normalizedValue(item.opt("artist")));
                            if (!present(id) || !present(name) || !present(artist)) {
                                continue;
                            }

                            Track track = new Track(
                                    id,
                                    validOptionalString(item, "source", source),
                                    name,
                                    artist,
                                    validOptionalString(item, "album", ""),
                                    validOptionalString(item, "pic_id", ""),
                                    validOptionalString(item, "lyric_id", "")
                            );

                            tracks.add(track);
                        }

                        if (arr.length() > 0 && tracks.isEmpty()) {
                            throw invalidResponse(
                                    "搜索",
                                    "歌曲条目缺少 id、name 或 artist",
                                    trimmed
                            );
                        }

                        callback.onSuccess(tracks);

                    } catch (Exception e) {
                        callback.onError(e);
                    }
                }
            });

        } catch (Exception e) {
            callback.onError(e);
        }
    }

    private String cleanArtist(String artistRaw) {
        if (artistRaw == null) {
            return "";
        }

        return artistRaw
                .replace("[", "")
                .replace("]", "")
                .replace("\"", "");
    }

    public void getAudioUrl(Track track, TrackCallback callback) {
        getAudioUrl(track, 320, callback);
    }

    public void getAudioUrl(Track track, int br, TrackCallback callback) {
        try {
            String idEncoded = URLEncoder.encode(track.id, "UTF-8");

            String url = "https://music-api.gdstudio.xyz/api.php"
                    + "?types=url"
                    + "&source=" + track.source
                    + "&id=" + idEncoded
                    + "&br=" + br;

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .get()
                    .build();

            RequestTracker.record();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError(e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String body = requireSuccessfulBody(response, "播放地址");
                        JSONObject obj = parseObject(body, "播放地址");
                        track.audioUrl = requireHttpUrl(obj, "url", "播放地址", body);
                        track.audioUrlCachedAt = System.currentTimeMillis();

                        callback.onSuccess(track);

                    } catch (Exception e) {
                        callback.onError(e);
                    }
                }
            });

        } catch (Exception e) {
            callback.onError(e);
        }
    }

    public void getPicUrl(Track track, TrackCallback callback) {
        if (track.picId == null || track.picId.isEmpty() || track.picId.equals("null")) {
            callback.onSuccess(track);
            return;
        }

        try {
            String picIdEncoded = URLEncoder.encode(track.picId, "UTF-8");

            String url = "https://music-api.gdstudio.xyz/api.php"
                    + "?types=pic"
                    + "&source=" + track.source
                    + "&id=" + picIdEncoded
                    + "&size=500";

            Request request = new Request.Builder().url(url).get().build();

            RequestTracker.record();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError(e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String body = requireSuccessfulBody(response, "封面");
                        JSONObject obj = parseObject(body, "封面");
                        track.picUrl = requireHttpUrl(obj, "url", "封面", body);

                        callback.onSuccess(track);

                    } catch (Exception e) {
                        callback.onError(e);
                    }
                }
            });

        } catch (Exception e) {
            callback.onError(e);
        }
    }

    public void getLyric(Track track, TrackCallback callback) {
        if (track.lyricId == null || track.lyricId.isEmpty() || track.lyricId.equals("null")) {
            callback.onSuccess(track);
            return;
        }

        try {
            String lyricIdEncoded = URLEncoder.encode(track.lyricId, "UTF-8");

            String url = "https://music-api.gdstudio.xyz/api.php"
                    + "?types=lyric"
                    + "&source=" + track.source
                    + "&id=" + lyricIdEncoded;

            Request request = new Request.Builder().url(url).get().build();

            RequestTracker.record();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError(e);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String body = requireSuccessfulBody(response, "歌词");
                        JSONObject obj = parseObject(body, "歌词");

                        track.lyric = validOptionalString(obj, "lyric", "");
                        track.translatedLyric = validOptionalString(obj, "tlyric", "");
                        if (!present(track.lyric)) {
                            throw invalidResponse("歌词", "缺少有效的 lyric 字段", body);
                        }

                        callback.onSuccess(track);

                    } catch (Exception e) {
                        callback.onError(e);
                    }
                }
            });

        } catch (Exception e) {
            callback.onError(e);
        }
    }

    private String requireSuccessfulBody(Response response, String endpoint) throws Exception {
        String body = response.body() == null ? "" : response.body().string();
        String trimmed = body.trim();
        if (!response.isSuccessful()) {
            throw invalidResponse(
                    endpoint,
                    "HTTP " + response.code(),
                    trimmed
            );
        }
        if (trimmed.isEmpty()) {
            throw invalidResponse(endpoint, "返回内容为空", trimmed);
        }
        return trimmed;
    }

    private JSONObject parseObject(String body, String endpoint) throws Exception {
        String trimmed = body.trim();

        if (trimmed.startsWith("[")) {
            JSONArray arr = new JSONArray(trimmed);
            if (arr.length() == 0) {
                throw invalidResponse(endpoint, "返回数组为空", trimmed);
            }
            JSONObject first = arr.optJSONObject(0);
            if (first == null) {
                throw invalidResponse(endpoint, "数组首项不是 JSON 对象", trimmed);
            }
            return first;
        }
        if (!trimmed.startsWith("{")) {
            throw invalidResponse(endpoint, "顶层数据不是 JSON 对象", trimmed);
        }
        return new JSONObject(trimmed);
    }

    private String requireHttpUrl(
            JSONObject object,
            String key,
            String endpoint,
            String rawBody
    ) throws Exception {
        String value = validOptionalString(object, key, "").trim();
        if (!present(value)) {
            throw invalidResponse(endpoint, "缺少有效的 " + key + " 字段", rawBody);
        }
        URI uri = new URI(value);
        String scheme = uri.getScheme();
        if (uri.getHost() == null
                || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw invalidResponse(endpoint, key + " 不是有效的 HTTP(S) 地址", rawBody);
        }
        return value;
    }

    private String validOptionalString(JSONObject object, String key, String fallback) {
        Object value = object.opt(key);
        if (value == null || value == JSONObject.NULL) {
            return fallback;
        }
        if (value instanceof String || value instanceof Number) {
            String normalized = String.valueOf(value).trim();
            return present(normalized) ? normalized : fallback;
        }
        return fallback;
    }

    private String normalizedValue(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return "";
        }
        return String.valueOf(value).trim();
    }

    private Exception invalidResponse(String endpoint, String reason, String body) {
        String preview = body == null ? "" : body.trim();
        if (preview.length() > 300) {
            preview = preview.substring(0, 300);
        }
        return new Exception(
                endpoint + "接口返回格式无效：" + reason
                        + (preview.isEmpty() ? "" : "\n\n返回内容：\n" + preview)
        );
    }
}
