package com.diamond.gdmusic;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.ResolvingDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.session.CommandButton;
import androidx.media3.session.LibraryResult;
import androidx.media3.session.MediaLibraryService;
import androidx.media3.session.MediaSession;
import androidx.media3.session.SessionCommand;
import androidx.media3.session.SessionCommands;
import androidx.media3.session.SessionError;
import androidx.media3.session.SessionResult;

import com.diamond.gdmusic.data.NeteasePlaylist;
import com.diamond.gdmusic.data.NeteasePlaylistCache;
import com.diamond.gdmusic.data.NeteasePlaylistRepository;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Shared playback and media-library endpoint used by the phone UI and Android Auto.
 *
 * <p>Android Auto renders the media tree supplied here; it does not render the
 * application's Compose activities. The phone UI connects through a
 * MediaController while Android Auto renders the driving-safe tree here.</p>
 */
@UnstableApi
public final class AutoPlaybackService extends MediaLibraryService {

    private static final String SESSION_LOG_TAG = "GDMediaSession";
    private static final String ROOT_ID = "root";
    private static final String FAVORITES_ID = "favorites";
    private static final String NETEASE_ID = "netease_playlists";
    private static final String NETEASE_CREATED_ID = "netease_created";
    private static final String NETEASE_SUBSCRIBED_ID = "netease_subscribed";
    private static final String NETEASE_PLAYLIST_PREFIX = "netease_playlist:";
    private static final String NETEASE_TRACK_PREFIX = "netease_track:";
    private static final String PLAYLIST_PREFIX = "playlist:";
    private static final String TRACK_PREFIX = "track:";
    private static final String AUTO_SEARCH_TRACK_PREFIX = "auto_search_track:";
    private static final int AUTO_SEARCH_RESULT_COUNT = 30;
    private static final long AUDIO_URL_MAX_AGE_MS = 30L * 60L * 1000L;
    private static final long SOURCE_RESOLVE_TIMEOUT_SECONDS = 60L;
    private static final SessionCommand ADD_TO_LIKED_COMMAND = new SessionCommand(
            "com.diamond.gdapplication.command.ADD_TO_LIKED",
            Bundle.EMPTY
    );

    private ExoPlayer player;
    private MediaLibrarySession mediaLibrarySession;
    private LocalPlaylistStore playlistStore;
    private GdMusicApi musicApi;
    private NeteasePlaylistRepository neteaseRepository;
    private final Map<String, NeteasePlaylist> neteasePlaylists = new LinkedHashMap<>();
    private final Map<String, List<Track>> neteaseTracks = new LinkedHashMap<>();
    private final Map<String, Track> playbackTracks = new ConcurrentHashMap<>();
    private final Map<String, List<MediaItem>> autoSearchResults = new ConcurrentHashMap<>();
    private final Set<String> pendingArtworkItems = ConcurrentHashMap.newKeySet();
    private final Set<String> sourceRecoveryItems = ConcurrentHashMap.newKeySet();
    private final Set<String> failedPlaybackItems = ConcurrentHashMap.newKeySet();
    private Handler playbackHandler;
    private volatile boolean destroyed;

    @Override
    public void onCreate() {
        super.onCreate();
        playlistStore = new LocalPlaylistStore(getApplicationContext());
        musicApi = new GdMusicApi();
        neteaseRepository = new NeteasePlaylistRepository();
        String neteaseUserId = NeteasePlaylistCache.savedUserId(this);
        rememberNeteasePlaylists(NeteasePlaylistCache.read(this, neteaseUserId));
        ResolvingDataSource.Factory resolvingDataSourceFactory =
                new ResolvingDataSource.Factory(
                        new DefaultDataSource.Factory(this),
                        this::resolvePlaybackDataSpec
                );
        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(
                        new DefaultMediaSourceFactory(resolvingDataSourceFactory)
                )
                .build();
        playbackHandler = new Handler(player.getApplicationLooper());
        player.addListener(new Player.Listener() {
            @Override
            public void onMediaItemTransition(
                    @Nullable MediaItem mediaItem,
                    int reason
            ) {
                requestMissingArtwork(mediaItem);
                updateFavoriteButton(TrackMediaItem.toTrack(mediaItem));
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState != Player.STATE_READY) {
                    return;
                }
                MediaItem currentItem = player.getCurrentMediaItem();
                if (currentItem != null) {
                    sourceRecoveryItems.remove(currentItem.mediaId);
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                recoverOrSkipFailedTrack(error);
            }
        });
        player.setAudioAttributes(
                new AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                true
        );
        player.setHandleAudioBecomingNoisy(true);
        player.setRepeatMode(Player.REPEAT_MODE_ALL);

        Intent sessionActivityIntent = new Intent(this, ComposeMainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent sessionActivity = PendingIntent.getActivity(
                this,
                0,
                sessionActivityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        mediaLibrarySession = new MediaLibrarySession.Builder(
                this,
                player,
                new AutoLibraryCallback()
        )
                .setId("shared_playback")
                .setSessionActivity(sessionActivity)
                .setMediaButtonPreferences(favoriteButtonPreferences(null))
                .setCustomLayout(favoriteButtonPreferences(null))
                .build();
    }

    @Nullable
    @Override
    public MediaLibrarySession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return mediaLibrarySession;
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        pendingArtworkItems.clear();
        sourceRecoveryItems.clear();
        failedPlaybackItems.clear();
        autoSearchResults.clear();
        playbackHandler.removeCallbacksAndMessages(null);
        mediaLibrarySession.release();
        player.release();
        super.onDestroy();
    }

    private void requestMissingArtwork(@Nullable MediaItem mediaItem) {
        Track track = TrackMediaItem.toTrack(mediaItem);
        if (mediaItem == null
                || track == null
                || isPresent(track.picUrl)
                || !isPresent(track.picId)
                || !pendingArtworkItems.add(mediaItem.mediaId)) {
            return;
        }

        String mediaId = mediaItem.mediaId;
        musicApi.getPicUrl(track, new GdMusicApi.TrackCallback() {
            @Override
            public void onSuccess(Track resolvedTrack) {
                pendingArtworkItems.remove(mediaId);
                if (destroyed || !isPresent(resolvedTrack.picUrl)) {
                    return;
                }
                playbackHandler.post(() -> updateMediaItemArtwork(mediaId, resolvedTrack.picUrl));
            }

            @Override
            public void onError(Exception error) {
                pendingArtworkItems.remove(mediaId);
            }
        });
    }

    private void updateMediaItemArtwork(String mediaId, String artworkUrl) {
        if (destroyed) {
            return;
        }
        for (int index = 0; index < player.getMediaItemCount(); index++) {
            MediaItem item = player.getMediaItemAt(index);
            if (!mediaId.equals(item.mediaId)) {
                continue;
            }
            Track track = TrackMediaItem.toTrack(item);
            if (track == null || isPresent(track.picUrl)) {
                return;
            }
            track.picUrl = artworkUrl;
            playbackTracks.put(mediaId, track);
            player.replaceMediaItem(index, TrackMediaItem.create(mediaId, track));
            return;
        }
    }

    private final class AutoLibraryCallback implements MediaLibrarySession.Callback {

        @Override
        public MediaSession.ConnectionResult onConnect(
                MediaSession session,
                MediaSession.ControllerInfo controller
        ) {
            Log.i(
                    SESSION_LOG_TAG,
                    "Controller connected: package="
                            + controller.getPackageName()
                            + ", uid="
                            + controller.getUid()
            );
            SessionCommands baseCommands = controller.isTrusted()
                    ? MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                    : MediaSession.ConnectionResult.DEFAULT_UNTRUSTED_SESSION_AND_LIBRARY_COMMANDS;
            SessionCommands availableCommands = baseCommands.buildUpon()
                    .add(ADD_TO_LIKED_COMMAND)
                    .build();
            List<CommandButton> buttons = favoriteButtonPreferences(
                    TrackMediaItem.toTrack(player.getCurrentMediaItem())
            );
            return new MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                    .setAvailableSessionCommands(availableCommands)
                    .setMediaButtonPreferences(buttons)
                    .setCustomLayout(buttons)
                    .build();
        }

        @Override
        public ListenableFuture<SessionResult> onCustomCommand(
                MediaSession session,
                MediaSession.ControllerInfo controller,
                SessionCommand customCommand,
                Bundle args
        ) {
            if (!ADD_TO_LIKED_COMMAND.equals(customCommand)) {
                return MediaLibrarySession.Callback.super.onCustomCommand(
                        session,
                        controller,
                        customCommand,
                        args
                );
            }

            Track current = TrackMediaItem.toTrack(player.getCurrentMediaItem());
            if (current == null) {
                return Futures.immediateFuture(
                        new SessionResult(SessionError.ERROR_BAD_VALUE)
                );
            }
            playlistStore = new LocalPlaylistStore(getApplicationContext());
            playlistStore.addToLiked(current);
            updateFavoriteButton(current);
            return Futures.immediateFuture(
                    new SessionResult(SessionResult.RESULT_SUCCESS)
            );
        }

        @Override
        public void onDisconnected(
                MediaSession session,
                MediaSession.ControllerInfo controller
        ) {
            Log.i(
                    SESSION_LOG_TAG,
                    "Controller disconnected: package="
                            + controller.getPackageName()
                            + ", uid="
                            + controller.getUid()
            );
            MediaLibrarySession.Callback.super.onDisconnected(session, controller);
        }

        @Override
        public ListenableFuture<LibraryResult<MediaItem>> onGetLibraryRoot(
                MediaLibrarySession session,
                MediaSession.ControllerInfo browser,
                @Nullable LibraryParams params
        ) {
            return Futures.immediateFuture(
                    LibraryResult.ofItem(
                            browsableItem(ROOT_ID, getString(R.string.app_name)),
                            params
                    )
            );
        }

        @Override
        public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> onGetChildren(
                MediaLibrarySession session,
                MediaSession.ControllerInfo browser,
                String parentId,
                int page,
                int pageSize,
                @Nullable LibraryParams params
        ) {
            if (NETEASE_CREATED_ID.equals(parentId)
                    || NETEASE_SUBSCRIBED_ID.equals(parentId)) {
                return loadNeteasePlaylistChildren(parentId, page, pageSize, params);
            }
            if (parentId.startsWith(NETEASE_PLAYLIST_PREFIX)) {
                return loadNeteaseTrackChildren(parentId, page, pageSize, params);
            }

            List<MediaItem> children = childrenFor(parentId);
            return immediatePagedResult(children, page, pageSize, params);
        }

        @Override
        public ListenableFuture<LibraryResult<Void>> onSearch(
                MediaLibrarySession session,
                MediaSession.ControllerInfo browser,
                String query,
                @Nullable LibraryParams params
        ) {
            String normalizedQuery = query.trim();
            if (normalizedQuery.isEmpty()) {
                return Futures.immediateFuture(
                        LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                );
            }

            requestAutoSearch(session, browser, normalizedQuery, params);
            return Futures.immediateFuture(LibraryResult.ofVoid());
        }

        @Override
        public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> onGetSearchResult(
                MediaLibrarySession session,
                MediaSession.ControllerInfo browser,
                String query,
                int page,
                int pageSize,
                @Nullable LibraryParams params
        ) {
            String normalizedQuery = query.trim();
            if (normalizedQuery.isEmpty()) {
                return Futures.immediateFuture(
                        LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                );
            }

            List<MediaItem> cached = autoSearchResults.get(normalizedQuery);
            if (cached != null) {
                return immediatePagedResult(cached, page, pageSize, params);
            }

            SettableFuture<LibraryResult<ImmutableList<MediaItem>>> future =
                    SettableFuture.create();
            musicApi.searchTracks(
                    normalizedQuery,
                    "netease",
                    AUTO_SEARCH_RESULT_COUNT,
                    1,
                    new GdMusicApi.SearchCallback() {
                        @Override
                        public void onSuccess(List<Track> tracks) {
                            List<MediaItem> results =
                                    autoSearchItems(normalizedQuery, tracks);
                            autoSearchResults.put(normalizedQuery, results);
                            future.set(pagedResult(results, page, pageSize, params));
                        }

                        @Override
                        public void onError(Exception error) {
                            future.set(pagedResult(
                                    Collections.emptyList(),
                                    page,
                                    pageSize,
                                    params
                            ));
                        }
                    }
            );
            return future;
        }

        @Override
        public ListenableFuture<LibraryResult<MediaItem>> onGetItem(
                MediaLibrarySession session,
                MediaSession.ControllerInfo browser,
                String mediaId
        ) {
            MediaItem item = itemFor(mediaId);
            if (item == null) {
                return Futures.immediateFuture(
                        LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                );
            }
            return Futures.immediateFuture(LibraryResult.ofItem(item, null));
        }

        @Override
        public ListenableFuture<List<MediaItem>> onAddMediaItems(
                MediaSession mediaSession,
                MediaSession.ControllerInfo controller,
                List<MediaItem> mediaItems
        ) {
            return Futures.immediateFuture(preparePlayableItems(mediaItems));
        }

        @Override
        public ListenableFuture<MediaSession.MediaItemsWithStartPosition> onSetMediaItems(
                MediaSession mediaSession,
                MediaSession.ControllerInfo controller,
                List<MediaItem> mediaItems,
                int startIndex,
                long startPositionMs
        ) {
            // A new queue (including a user reselecting a track in Android Auto) deserves a
            // fresh attempt. Failed IDs only apply to the queue that produced them.
            sourceRecoveryItems.clear();
            failedPlaybackItems.clear();
            QueueExpansion expansion = expandLibraryQueue(mediaItems, startIndex);
            return Futures.immediateFuture(
                    new MediaSession.MediaItemsWithStartPosition(
                            preparePlayableItems(expansion.items),
                            expansion.startIndex,
                            startPositionMs
                    )
            );
        }
    }

    private List<CommandButton> favoriteButtonPreferences(@Nullable Track track) {
        boolean liked = track != null && playlistStore.isLiked(track);
        CommandButton button = new CommandButton.Builder(
                liked ? CommandButton.ICON_HEART_FILLED : CommandButton.ICON_HEART_UNFILLED
        )
                .setSessionCommand(ADD_TO_LIKED_COMMAND)
                .setDisplayName(liked ? "已加入我喜欢的" : "加入我喜欢的")
                .setSlots(
                        CommandButton.SLOT_FORWARD_SECONDARY,
                        CommandButton.SLOT_OVERFLOW
                )
                .build();
        return ImmutableList.of(button);
    }

    private void updateFavoriteButton(@Nullable Track track) {
        if (mediaLibrarySession == null || destroyed) {
            return;
        }
        List<CommandButton> buttons = favoriteButtonPreferences(track);
        mediaLibrarySession.setMediaButtonPreferences(buttons);
        mediaLibrarySession.setCustomLayout(buttons);
    }

    private void requestAutoSearch(
            MediaLibrarySession session,
            MediaSession.ControllerInfo browser,
            String query,
            @Nullable LibraryParams params
    ) {
        musicApi.searchTracks(
                query,
                "netease",
                AUTO_SEARCH_RESULT_COUNT,
                1,
                new GdMusicApi.SearchCallback() {
                    @Override
                    public void onSuccess(List<Track> tracks) {
                        List<MediaItem> results = autoSearchItems(query, tracks);
                        autoSearchResults.put(query, results);
                        if (!destroyed) {
                            playbackHandler.post(() -> {
                                if (!destroyed) {
                                    session.notifySearchResultChanged(
                                            browser,
                                            query,
                                            results.size(),
                                            params
                                    );
                                }
                            });
                        }
                    }

                    @Override
                    public void onError(Exception error) {
                        autoSearchResults.put(query, Collections.emptyList());
                        if (!destroyed) {
                            playbackHandler.post(() -> {
                                if (!destroyed) {
                                    session.notifySearchResultChanged(
                                            browser,
                                            query,
                                            0,
                                            params
                                    );
                                }
                            });
                        }
                    }
                }
        );
    }

    private List<MediaItem> autoSearchItems(String query, List<Track> tracks) {
        List<MediaItem> items = new ArrayList<>();
        String queryId = Integer.toHexString(query.hashCode());
        for (int index = 0; index < tracks.size(); index++) {
            items.add(trackItem(
                    AUTO_SEARCH_TRACK_PREFIX + queryId + ":" + index,
                    tracks.get(index)
            ));
        }
        return items;
    }

    private List<MediaItem> childrenFor(String parentId) {
        List<MediaItem> items = new ArrayList<>();

        if (ROOT_ID.equals(parentId)) {
            items.add(browsableItem(FAVORITES_ID, getString(R.string.auto_favorites)));
            items.add(browsableItem(NETEASE_ID, getString(R.string.auto_netease_playlists)));
            return items;
        }

        if (FAVORITES_ID.equals(parentId)) {
            // The phone UI may edit favorites while this long-lived service is active.
            // Re-open the store before publishing the Android Auto library snapshot.
            playlistStore = new LocalPlaylistStore(getApplicationContext());
            for (LocalPlaylistStore.LocalPlaylist playlist : playlistStore.getPlaylists()) {
                MediaMetadata.Builder metadata = new MediaMetadata.Builder()
                        .setTitle(playlist.name)
                        .setSubtitle(playlist.tracks.size() + " 首歌曲")
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST);

                Track cover = playlist.getCoverTrack();
                if (cover != null && isPresent(cover.picUrl)) {
                    metadata.setArtworkUri(Uri.parse(cover.picUrl));
                }

                items.add(new MediaItem.Builder()
                        .setMediaId(PLAYLIST_PREFIX + playlist.id)
                        .setMediaMetadata(metadata.build())
                        .build());
            }
            return items;
        }

        if (NETEASE_ID.equals(parentId)) {
            if (NeteasePlaylistCache.savedUserId(this).isEmpty()) {
                items.add(messageItem(
                        "netease_user_hint",
                        getString(R.string.auto_netease_user_hint)
                ));
            } else {
                items.add(browsableItem(
                        NETEASE_CREATED_ID,
                        getString(R.string.auto_netease_created)
                ));
                items.add(browsableItem(
                        NETEASE_SUBSCRIBED_ID,
                        getString(R.string.auto_netease_subscribed)
                ));
            }
            return items;
        }

        if (parentId.startsWith(PLAYLIST_PREFIX)) {
            String playlistId = parentId.substring(PLAYLIST_PREFIX.length());
            LocalPlaylistStore.LocalPlaylist playlist = findPlaylist(playlistId);
            if (playlist == null) {
                return items;
            }
            for (int index = 0; index < playlist.tracks.size(); index++) {
                items.add(trackItem(
                        localTrackId(playlistId, index),
                        playlist.tracks.get(index)
                ));
            }
        }

        return items;
    }

    @Nullable
    private MediaItem itemFor(String mediaId) {
        if (ROOT_ID.equals(mediaId)) {
            return browsableItem(ROOT_ID, getString(R.string.app_name));
        }
        if (FAVORITES_ID.equals(mediaId)) {
            return browsableItem(FAVORITES_ID, getString(R.string.auto_favorites));
        }
        if (NETEASE_ID.equals(mediaId)) {
            return browsableItem(NETEASE_ID, getString(R.string.auto_netease_playlists));
        }
        if (NETEASE_CREATED_ID.equals(mediaId)) {
            return browsableItem(NETEASE_CREATED_ID, getString(R.string.auto_netease_created));
        }
        if (NETEASE_SUBSCRIBED_ID.equals(mediaId)) {
            return browsableItem(
                    NETEASE_SUBSCRIBED_ID,
                    getString(R.string.auto_netease_subscribed)
            );
        }
        if (mediaId.startsWith(NETEASE_PLAYLIST_PREFIX)) {
            NeteasePlaylist playlist = neteasePlaylists.get(
                    mediaId.substring(NETEASE_PLAYLIST_PREFIX.length())
            );
            return playlist == null ? null : neteasePlaylistItem(playlist);
        }
        if (mediaId.startsWith(PLAYLIST_PREFIX)) {
            LocalPlaylistStore.LocalPlaylist playlist = findPlaylist(
                    mediaId.substring(PLAYLIST_PREFIX.length())
            );
            return playlist == null ? null : browsableItem(mediaId, playlist.name);
        }
        TrackLocation location = findTrack(mediaId);
        return location == null
                ? null
                : trackItem(location.mediaId, location.track);
    }

    private MediaItem browsableItem(String mediaId, String title) {
        return new MediaItem.Builder()
                .setMediaId(mediaId)
                .setMediaMetadata(new MediaMetadata.Builder()
                        .setTitle(title)
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
                        .build())
                .build();
    }

    private ListenableFuture<LibraryResult<ImmutableList<MediaItem>>>
    loadNeteasePlaylistChildren(
            String sectionId,
            int page,
            int pageSize,
            @Nullable LibraryParams params
    ) {
        String userId = NeteasePlaylistCache.savedUserId(this);
        if (userId.isEmpty()) {
            return immediatePagedResult(
                    Collections.singletonList(messageItem(
                            "netease_user_hint",
                            getString(R.string.auto_netease_user_hint)
                    )),
                    page,
                    pageSize,
                    params
            );
        }

        // 手机端手动刷新后，车机下次打开板块即可读取最新缓存。
        rememberNeteasePlaylists(NeteasePlaylistCache.read(this, userId));

        if (!neteasePlaylists.isEmpty() && !NeteasePlaylistCache.shouldRefresh(this)) {
            return immediatePagedResult(
                    neteasePlaylistItems(sectionId, userId),
                    page,
                    pageSize,
                    params
            );
        }

        SettableFuture<LibraryResult<ImmutableList<MediaItem>>> future =
                SettableFuture.create();
        NeteasePlaylistCache.markRefreshAttempt(this);
        neteaseRepository.loadPublicPlaylistsForJava(
                userId,
                new NeteasePlaylistRepository.PlaylistsCallback() {
                    @Override
                    public void onSuccess(List<NeteasePlaylist> playlists) {
                        rememberNeteasePlaylists(playlists);
                        NeteasePlaylistCache.save(
                                AutoPlaybackService.this,
                                userId,
                                playlists
                        );
                        future.set(pagedResult(
                                neteasePlaylistItems(sectionId, userId),
                                page,
                                pageSize,
                                params
                        ));
                    }

                    @Override
                    public void onError(Throwable error) {
                        if (!neteasePlaylists.isEmpty()) {
                            future.set(pagedResult(
                                    neteasePlaylistItems(sectionId, userId),
                                    page,
                                    pageSize,
                                    params
                            ));
                            return;
                        }
                        future.set(pagedResult(
                                Collections.singletonList(messageItem(
                                        "netease_load_error",
                                        getString(R.string.auto_netease_load_error)
                                )),
                                page,
                                pageSize,
                                params
                        ));
                    }
                }
        );
        return future;
    }

    private ListenableFuture<LibraryResult<ImmutableList<MediaItem>>>
    loadNeteaseTrackChildren(
            String parentId,
            int page,
            int pageSize,
            @Nullable LibraryParams params
    ) {
        String playlistId = parentId.substring(NETEASE_PLAYLIST_PREFIX.length());
        List<Track> cachedTracks = neteaseTracks.get(playlistId);
        if (cachedTracks != null) {
            return immediatePagedResult(
                    neteaseTrackItems(playlistId, cachedTracks),
                    page,
                    pageSize,
                    params
            );
        }

        SettableFuture<LibraryResult<ImmutableList<MediaItem>>> future =
                SettableFuture.create();
        neteaseRepository.loadPlaylistTracksForJava(
                playlistId,
                new NeteasePlaylistRepository.TracksCallback() {
                    @Override
                    public void onSuccess(List<? extends Track> tracks) {
                        List<Track> trackList = new ArrayList<>(tracks);
                        neteaseTracks.put(playlistId, trackList);
                        future.set(pagedResult(
                                neteaseTrackItems(playlistId, trackList),
                                page,
                                pageSize,
                                params
                        ));
                    }

                    @Override
                    public void onError(Throwable error) {
                        future.set(pagedResult(
                                Collections.singletonList(messageItem(
                                        "netease_tracks_error:" + playlistId,
                                        getString(R.string.auto_netease_tracks_error)
                                )),
                                page,
                                pageSize,
                                params
                        ));
                    }
                }
        );
        return future;
    }

    private List<MediaItem> neteasePlaylistItems(String sectionId, String userId) {
        boolean created = NETEASE_CREATED_ID.equals(sectionId);
        List<MediaItem> items = new ArrayList<>();
        for (NeteasePlaylist playlist : neteasePlaylists.values()) {
            if (playlist.isCreatedBy(userId) == created) {
                items.add(neteasePlaylistItem(playlist));
            }
        }
        return items;
    }

    private MediaItem neteasePlaylistItem(NeteasePlaylist playlist) {
        MediaMetadata.Builder metadata = new MediaMetadata.Builder()
                .setTitle(playlist.getName())
                .setSubtitle(playlist.getTrackCount() + " 首歌曲")
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST);
        if (isPresent(playlist.getCoverUrl())) {
            metadata.setArtworkUri(Uri.parse(playlist.getCoverUrl()));
        }
        return new MediaItem.Builder()
                .setMediaId(NETEASE_PLAYLIST_PREFIX + playlist.getId())
                .setMediaMetadata(metadata.build())
                .build();
    }

    private List<MediaItem> neteaseTrackItems(String playlistId, List<Track> tracks) {
        List<MediaItem> items = new ArrayList<>();
        for (int index = 0; index < tracks.size(); index++) {
            items.add(trackItem(
                    neteaseTrackId(playlistId, index),
                    tracks.get(index)
            ));
        }
        return items;
    }

    private MediaItem messageItem(String mediaId, String title) {
        return new MediaItem.Builder()
                .setMediaId(mediaId)
                .setMediaMetadata(new MediaMetadata.Builder()
                        .setTitle(title)
                        .setIsBrowsable(false)
                        .setIsPlayable(false)
                        .build())
                .build();
    }

    private void rememberNeteasePlaylists(List<NeteasePlaylist> playlists) {
        neteasePlaylists.clear();
        for (NeteasePlaylist playlist : playlists) {
            neteasePlaylists.put(playlist.getId(), playlist);
        }
    }

    private ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> immediatePagedResult(
            List<MediaItem> items,
            int page,
            int pageSize,
            @Nullable LibraryParams params
    ) {
        return Futures.immediateFuture(pagedResult(items, page, pageSize, params));
    }

    private LibraryResult<ImmutableList<MediaItem>> pagedResult(
            List<MediaItem> items,
            int page,
            int pageSize,
            @Nullable LibraryParams params
    ) {
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = Math.min(Math.max(page, 0) * safePageSize, items.size());
        int toIndex = Math.min(fromIndex + safePageSize, items.size());
        return LibraryResult.ofItemList(items.subList(fromIndex, toIndex), params);
    }

    private String localTrackId(String playlistId, int index) {
        return TRACK_PREFIX + playlistId + ":" + index;
    }

    private String neteaseTrackId(String playlistId, int index) {
        return NETEASE_TRACK_PREFIX + playlistId + ":" + index;
    }

    private MediaItem trackItem(String mediaId, Track track) {
        playbackTracks.put(mediaId, track);
        return TrackMediaItem.create(mediaId, track);
    }

    /**
     * A stream can still fail after a source has returned an audio URL. Retry the item once
     * through the multi-source resolver, then advance to the next healthy queue item instead of
     * leaving Media3 in the error state. This path is shared by the phone and Android Auto.
     */
    private void recoverOrSkipFailedTrack(PlaybackException error) {
        if (destroyed || player == null) {
            return;
        }

        MediaItem failedItem = player.getCurrentMediaItem();
        int failedIndex = player.getCurrentMediaItemIndex();
        if (failedItem == null || failedIndex == C.INDEX_UNSET) {
            return;
        }

        String mediaId = failedItem.mediaId;
        Log.w(
                SESSION_LOG_TAG,
                "Playback failed for " + mediaId + "; error=" + error.getErrorCodeName()
        );

        if (sourceRecoveryItems.add(mediaId) && retryItemWithOtherSources(failedIndex, mediaId)) {
            return;
        }

        failedPlaybackItems.add(mediaId);
        int nextIndex = findNextPlayableIndex(failedIndex);
        if (nextIndex == C.INDEX_UNSET) {
            Log.e(SESSION_LOG_TAG, "No playable item remains after source failure: " + mediaId);
            return;
        }

        playbackHandler.post(() -> {
            if (destroyed || player == null || nextIndex >= player.getMediaItemCount()) {
                return;
            }
            Log.i(SESSION_LOG_TAG, "Skipping failed item and continuing at queue index " + nextIndex);
            player.seekToDefaultPosition(nextIndex);
            player.prepare();
            player.play();
        });
    }

    private boolean retryItemWithOtherSources(int index, String mediaId) {
        if (index < 0 || index >= player.getMediaItemCount()) {
            return false;
        }

        Track reference = playbackTracks.get(mediaId);
        if (reference == null) {
            reference = TrackMediaItem.toTrack(player.getMediaItemAt(index));
        }
        if (reference == null) {
            return false;
        }

        Track retry = copyTrack(reference);
        // Clear the broken URL and use the existing multi-source resolver on the next prepare.
        retry.audioUrl = "";
        retry.audioUrlCachedAt = 0L;
        retry.externalMetadata = true;
        playbackTracks.put(mediaId, retry);

        playbackHandler.post(() -> {
            if (destroyed || player == null || index >= player.getMediaItemCount()) {
                return;
            }
            if (!mediaId.equals(player.getMediaItemAt(index).mediaId)) {
                return;
            }
            Log.i(SESSION_LOG_TAG, "Retrying failed item with alternate sources: " + mediaId);
            player.replaceMediaItem(index, TrackMediaItem.create(mediaId, retry));
            player.seekToDefaultPosition(index);
            player.prepare();
            player.play();
        });
        return true;
    }

    private int findNextPlayableIndex(int failedIndex) {
        int itemCount = player.getMediaItemCount();
        if (itemCount <= 1) {
            return C.INDEX_UNSET;
        }

        int playerNextIndex = player.getNextMediaItemIndex();
        if (playerNextIndex != C.INDEX_UNSET
                && playerNextIndex != failedIndex
                && !failedPlaybackItems.contains(player.getMediaItemAt(playerNextIndex).mediaId)) {
            return playerNextIndex;
        }

        // A bad item must not trap repeat-all playback. Fall back to queue order until a track
        // that has not already failed in this queue is found.
        for (int offset = 1; offset < itemCount; offset++) {
            int candidateIndex = (failedIndex + offset) % itemCount;
            if (!failedPlaybackItems.contains(player.getMediaItemAt(candidateIndex).mediaId)) {
                return candidateIndex;
            }
        }
        return C.INDEX_UNSET;
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
        copy.lyric = source.lyric;
        copy.translatedLyric = source.translatedLyric;
        copy.requestedBitrate = source.requestedBitrate;
        copy.externalMetadata = source.externalMetadata;
        return copy;
    }

    private List<MediaItem> preparePlayableItems(List<MediaItem> requested) {
        List<MediaItem> prepared = new ArrayList<>();
        for (MediaItem item : requested) {
            Track track = TrackMediaItem.toTrack(item);
            if (track == null) {
                TrackLocation location = findTrack(item.mediaId);
                track = location == null ? null : location.track;
            }
            if (track != null) {
                prepared.add(trackItem(item.mediaId, track));
            }
        }
        return prepared;
    }

    private QueueExpansion expandLibraryQueue(List<MediaItem> requested, int startIndex) {
        if (requested.size() != 1) {
            return new QueueExpansion(requested, startIndex);
        }

        String mediaId = requested.get(0).mediaId;
        boolean netease = mediaId.startsWith(NETEASE_TRACK_PREFIX);
        String prefix = netease ? NETEASE_TRACK_PREFIX : TRACK_PREFIX;
        if (!mediaId.startsWith(prefix)) {
            return new QueueExpansion(requested, startIndex);
        }

        String value = mediaId.substring(prefix.length());
        int separator = value.lastIndexOf(':');
        if (separator <= 0) {
            return new QueueExpansion(requested, startIndex);
        }

        try {
            String playlistId = value.substring(0, separator);
            int selectedIndex = Integer.parseInt(value.substring(separator + 1));
            List<Track> tracks;
            if (netease) {
                tracks = neteaseTracks.get(playlistId);
            } else {
                LocalPlaylistStore.LocalPlaylist playlist = findPlaylist(playlistId);
                tracks = playlist == null ? null : playlist.tracks;
            }
            if (tracks == null || tracks.isEmpty()
                    || selectedIndex < 0 || selectedIndex >= tracks.size()) {
                return new QueueExpansion(requested, startIndex);
            }

            List<MediaItem> expanded = new ArrayList<>();
            for (int index = 0; index < tracks.size(); index++) {
                String id = netease
                        ? neteaseTrackId(playlistId, index)
                        : localTrackId(playlistId, index);
                expanded.add(trackItem(id, tracks.get(index)));
            }
            return new QueueExpansion(expanded, selectedIndex);
        } catch (NumberFormatException ignored) {
            return new QueueExpansion(requested, startIndex);
        }
    }

    private DataSpec resolvePlaybackDataSpec(DataSpec dataSpec) throws IOException {
        if (!TrackMediaItem.isPlaybackUri(dataSpec.uri)) {
            return dataSpec;
        }

        String mediaId = TrackMediaItem.mediaIdFromPlaybackUri(dataSpec.uri);
        Track track = mediaId == null ? null : playbackTracks.get(mediaId);
        if (track == null) {
            throw new IOException("找不到播放项目：" + mediaId);
        }

        Track resolved = hasValidAudioUrl(track) ? track : resolveTrackBlocking(track);
        if (!isPresent(resolved.audioUrl)) {
            throw new IOException("没有拿到播放地址：" + resolved.name);
        }
        playbackTracks.put(mediaId, resolved);
        return dataSpec.withUri(Uri.parse(resolved.audioUrl));
    }

    private Track resolveTrackBlocking(Track track) throws IOException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Track> result = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        GdMusicApi.TrackCallback callback = new GdMusicApi.TrackCallback() {
            @Override
            public void onSuccess(Track resolvedTrack) {
                result.set(resolvedTrack);
                latch.countDown();
            }

            @Override
            public void onError(Exception error) {
                failure.set(error);
                latch.countDown();
            }
        };

        int bitrate = track.requestedBitrate > 0
                ? track.requestedBitrate
                : PlaybackPreferences.defaultBitrate(this);
        if (track.externalMetadata) {
            musicApi.resolveExternalTrack(track, bitrate, callback);
        } else {
            musicApi.getAudioUrl(track, bitrate, callback);
        }

        try {
            if (!latch.await(SOURCE_RESOLVE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IOException("获取播放地址超时：" + track.name);
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("获取播放地址被中断：" + track.name, error);
        }

        if (result.get() != null) {
            return result.get();
        }
        throw new IOException("获取播放地址失败：" + track.name, failure.get());
    }

    private boolean hasValidAudioUrl(Track track) {
        return isPresent(track.audioUrl)
                && System.currentTimeMillis() - track.audioUrlCachedAt < AUDIO_URL_MAX_AGE_MS;
    }

    @Nullable
    private LocalPlaylistStore.LocalPlaylist findPlaylist(String playlistId) {
        playlistStore = new LocalPlaylistStore(getApplicationContext());
        for (LocalPlaylistStore.LocalPlaylist playlist : playlistStore.getPlaylists()) {
            if (playlist.id.equals(playlistId)) {
                return playlist;
            }
        }
        return null;
    }

    @Nullable
    private TrackLocation findTrack(String mediaId) {
        if (mediaId == null) {
            return null;
        }

        boolean netease = mediaId.startsWith(NETEASE_TRACK_PREFIX);
        String value;
        if (netease) {
            value = mediaId.substring(NETEASE_TRACK_PREFIX.length());
        } else if (mediaId.startsWith(TRACK_PREFIX)) {
            value = mediaId.substring(TRACK_PREFIX.length());
        } else {
            return null;
        }
        int separator = value.lastIndexOf(':');
        if (separator <= 0) {
            return null;
        }

        try {
            String playlistId = value.substring(0, separator);
            int index = Integer.parseInt(value.substring(separator + 1));
            if (netease) {
                List<Track> tracks = neteaseTracks.get(playlistId);
                if (tracks == null || index < 0 || index >= tracks.size()) {
                    return null;
                }
                return new TrackLocation(mediaId, tracks.get(index));
            }
            LocalPlaylistStore.LocalPlaylist playlist = findPlaylist(playlistId);
            if (playlist == null || index < 0 || index >= playlist.tracks.size()) {
                return null;
            }
            return new TrackLocation(mediaId, playlist.tracks.get(index));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean isPresent(@Nullable String value) {
        return value != null && !value.isEmpty() && !"null".equals(value);
    }

    private static final class TrackLocation {
        final String mediaId;
        final Track track;

        TrackLocation(String mediaId, Track track) {
            this.mediaId = mediaId;
            this.track = track;
        }
    }

    private static final class QueueExpansion {
        final List<MediaItem> items;
        final int startIndex;

        QueueExpansion(List<MediaItem> items, int startIndex) {
            this.items = items;
            this.startIndex = startIndex;
        }
    }
}
