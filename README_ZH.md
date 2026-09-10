# GD Music

[English](README.md)

GD Music 是一款使用 Kotlin、Java 和 Jetpack Compose 开发的 Android 音乐播放器。本项目用于学习 Android 开发、Media3 播放、Compose 界面、网络请求及 Android Auto 媒体集成。

> 本项目仅供学习与技术交流。请遵守适用法律、第三方服务条款及版权要求，请勿使用本项目下载、传播或商业利用未经授权的音乐内容。

## 功能

- 搜索单曲、歌手、专辑和网易云歌单，并支持加载更多结果与搜索历史
- 切换音乐源；当前音源无法解析时自动尝试其他可用来源
- 支持播放、暂停、进度拖动、上一首/下一首、队列管理及循环和随机播放
- 支持迷你播放器、完整播放页、同步歌词和可用时的双语翻译歌词
- 创建和管理本地收藏；可播放全部、移除歌曲或删除收藏
- 设置网易云音乐用户 ID，浏览公开创建/收藏歌单及歌单歌曲
- 支持 Android Auto 浏览和播放收藏与网易云歌单歌曲

## 技术栈

- Kotlin、Java、Jetpack Compose 和 Material 3
- AndroidX Media3 / ExoPlayer
- OkHttp、Coil 3 和 SharedPreferences
- Android Auto 媒体浏览

## 环境要求

- Android Studio 与 JDK 17
- 最低 Android 6.0（API 23）；目标 API 36
- 可访问所需音乐服务的网络环境

## 本地运行

```bash
git clone https://github.com/Diamond01010111/GD_Music.git
cd GD_Music
```

使用 Android Studio 打开项目，完成 Gradle Sync 后连接设备或启动模拟器并点击 **Run**。

## 项目结构

```text
GD_Music/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── androidTest/                  # 仪器测试
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/diamond/gdmusic/
│       │   │   ├── data/                 # 歌单缓存、仓库和搜索记录
│       │   │   ├── model/                # 页面、音乐源和搜索类型
│       │   │   └── ui/                   # 界面、组件和页面
│       │   ├── keepRules/
│       │   └── res/
│       └── test/                         # 单元测试
├── gradle/
│   ├── wrapper/
│   └── libs.versions.toml
├── README.md
├── README_ZH.md
├── build.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
└── settings.gradle.kts
```

## 音乐服务

GD Music 使用 [GD Studio 在线音乐平台 API](https://music.gdstudio.xyz/) 搜索音乐，并获取播放地址、专辑封面和歌词数据。该 API 文档标注的请求限制为 5 分钟内不超过 50 次；应用会记录请求次数，并在接近上限前提示。

音乐源可用性、返回数据、音质和播放链接由相关第三方服务决定，且可能随时变化。

## 已知限制

- 第三方音乐源可能暂时不可用，播放链接也可能过期。
- 仅可加载公开可见的网易云音乐歌单。
- 清除应用数据会删除本地收藏、搜索记录、歌单缓存和已保存的网易云用户 ID。
- 应用进程被终止后，播放队列、当前歌曲和播放进度可能无法完整恢复。

## 免责声明

GD Music 是独立的学习项目，与 GD Studio、网易云音乐、Google、Android Auto、音乐平台或内容提供方不存在隶属、认可或赞助关系。

本项目不托管、不提供也不分发音乐文件。音乐、封面、歌词、歌单信息、商标及其他第三方内容的权利均归各自权利人所有。用户应自行确保使用方式符合适用法律、版权要求、API 条款和平台服务条款。

## 许可证

本项目自行编写的代码采用 MIT License。第三方 API、依赖项、音乐、封面、歌词、商标及其他第三方内容仍分别受其自身条款和许可证约束。
