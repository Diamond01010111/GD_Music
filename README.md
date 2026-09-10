# GD Music

[简体中文](README_ZH.md)

GD Music is an Android music player built with Kotlin, Java, and Jetpack Compose. It is an independent learning project for Android development, Media3 playback, Compose UI, networking, and Android Auto integration.

> For learning and technical discussion only. Follow applicable laws, service terms, and copyright requirements. Do not use this project to download, redistribute, or commercially exploit unauthorized music content.

## Features

- Search songs, artists, albums, and NetEase Cloud Music playlists
- Switch music sources, load more results, and save recent searches
- Resolve playback URLs, artwork, and lyrics; retry another available source on failure
- Playback queue, seeking, repeat, shuffle, play-next, and track removal
- Mini player, full player, synchronized lyrics, and bilingual translations when available
- Local favorites with playlist creation, playback, and local persistence
- NetEase user playlists: configure a user ID, browse public playlists and tracks
- Android Auto browsing and playback for favorites and NetEase playlists

## Tech Stack

Kotlin, Java, Jetpack Compose, Material 3, AndroidX Media3 / ExoPlayer, OkHttp, Coil 3, SharedPreferences, and Android Auto media browsing.

## Requirements

- Android Studio and JDK 17
- Android SDK; minimum Android 6.0 (API 23), target API 36
- Network access to the required music services

## Run Locally

```bash
git clone https://github.com/Diamond01010111/GD_Music.git
cd GD_Music
```

Open the project in Android Studio, complete Gradle Sync, then run it on a device or emulator. For Android Auto, use Android Studio's Desktop Head Unit or a compatible device and vehicle.

## Project Structure

```text
GD_Music/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── androidTest/                  # Instrumented tests
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/diamond/gdmusic/
│       │   │   ├── data/                 # Playlist cache, repositories, search history
│       │   │   ├── model/                # Pages, music sources, search types
│       │   │   └── ui/                   # UI, components, and screens
│       │   ├── keepRules/
│       │   └── res/
│       └── test/                         # Unit tests
├── gradle/
├── README.md
├── README_ZH.md
├── build.gradle.kts
├── gradlew
└── settings.gradle.kts
```

## Music Service

GD Music uses the [GD Studio Online Music Platform API](https://music.gdstudio.xyz/) for music search, playback URLs, artwork, and lyrics. Its documentation specifies a maximum of 50 requests within five minutes; the app tracks these requests and warns before the limit is reached.

Source availability, returned data, audio quality, and playback URLs are controlled by third-party services and may change without notice.

## Known Limitations

- Third-party sources may be unavailable and playback URLs may expire.
- Only publicly visible NetEase Cloud Music playlists can be loaded.
- Clearing app data removes local favorites, search history, playlist cache, and the saved NetEase user ID.
- Queue, current track, and position may not be fully restored after process termination.

## Disclaimer

GD Music is not affiliated with, endorsed by, or sponsored by GD Studio, NetEase Cloud Music, Google, Android Auto, music platforms, or content providers. It does not host, provide, or distribute music files. Music, artwork, lyrics, playlist data, trademarks, and other third-party content belong to their respective rights holders.

Users are responsible for complying with applicable laws, copyright requirements, API terms, and platform terms of service.

## License

Code written for this project is released under the MIT License. Third-party APIs, dependencies, and content remain subject to their own terms and licenses.
