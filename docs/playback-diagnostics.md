# 《七里香》播放诊断

两个测试都默认跳过，必须显式启用。它们会消耗真实 GD API 额度，沿用生产代码的 50 次/5 分钟限制；不要同时运行，也不要同时在应用中搜索或换源。测试不会请求封面、歌词或修改收藏和播放队列。

## 电脑端：搜索 → 匹配 → 地址 → HTTP 访问

在项目根目录 PowerShell 中执行：

```powershell
$env:GD_MUSIC_LIVE_TEST = "1"
.\gradlew.bat :app:testDebugUnitTest --tests "com.diamond.gdmusic.QilixiangLiveTest" --rerun-tasks
Remove-Item Env:GD_MUSIC_LIVE_TEST
```

打开 `app/build/reports/tests/testDebugUnitTest/index.html`，查看测试的 Standard output。测试会分别检查四个源，每源最多 1 次搜索和 3 次地址请求。HTTP 探测最多读取 4096 字节；成功只能证明地址可访问，不能证明手机播放器可以解码。

电脑测试的计数只属于测试进程，不会读取手机上的已有额度；同一公网出口的其他客户端仍可能触发服务器限流。

## 手机端：搜索 → 匹配 → 地址 → Media3 就绪

先连接手机，关闭应用的播放任务。在 Android Studio Terminal 的 PowerShell 中执行：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.diamond.gdmusic.QilixiangDeviceTest" "-Pandroid.testInstrumentationRunnerArguments.gdLive=1"
```

在 Android Studio Logcat 输入 `tag:GD_DIAG`，保存完整日志。测试使用独立、静音且不自动播放的 ExoPlayer，等待 `PLAYER_READY`；这验证准备/加载阶段，不保证整首歌都能持续播放。手机测试会读取并更新应用的真实 API 请求计数。

| 日志 | 意义 |
| --- | --- |
| SEARCH_FAILED | 搜索 HTTP、DNS、超时、API 格式或限流失败 |
| SEARCH_OK count=0 | API 正常返回，但搜索无结果 |
| MATCH … accepted=false | 返回结果被现有歌名/歌手匹配规则过滤 |
| URL_FAILED | 候选匹配成功，但获取音频地址失败 |
| AUDIO_HTTP … accessible=false | 电脑能拿到地址，但音频服务器响应异常 |
| PLAYER_FAILED | 手机播放器无法加载/解析，查看错误码和异常类型 |
| PLAYER_TIMEOUT | 手机播放器在 20 秒内没有进入就绪或报告错误 |
| PLAYER_READY | 手机播放器已准备完成 |

日志不输出带签名的完整音频 URL。搜索结果中的歌名、歌手和候选 ID 会输出，方便检查误匹配。

## 此环境的一次执行结果

电脑端实际运行消耗 4 次搜索尝试，四个来源均在 SEARCH 阶段出现 `UnknownHostException`（此环境无法解析 `music-api.gdstudio.xyz`）。这不是手机故障的证据。

日志还确认当前逐字繁体转换将 JOOX 查询词从“七里香”变为“七裏香”。测试保留生产行为以呈现真实流程；是否造成 JOOX 搜索无结果，需要手机日志或可连通环境进一步确认。
