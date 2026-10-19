<div align="center">

# NACRE 🐚

**一个治愈系的 Android 音频 App —— 既能安静地助眠放松，也能好好地听歌**

<p>
  🌏 <a href="README.en.md">English</a> &nbsp;|&nbsp; 中文
</p>

</div>

---

## 写在前面 · 关于这个项目 🙋

你好呀，我是这个项目的作者，一名还在读书的学生。NACRE 是我利用课余时间鼓捣出来的一个「个人项目」，也是我**第一次认真写一个比较大的 Android 应用**。

坦白讲，这是一个 **vibe-coding** 项目：里面有相当一部分代码，是我和 AI 助手（Claude Code）一起「边聊边写」出来的——我先描述我想要什么，它帮我写，我再一点点读、改、调、debug，踩了很多坑，也推翻重来了很多次。所以它**不完美**，可能有 bug，有些实现也很「野路子」，架构上未必是教科书式的。

但我挺喜欢它的。它承载了我对「好听的、好看的、能让人安静下来」的软件的一点想象：把助眠白噪音、双耳节拍、环境音，和在线音乐、歌词、漂亮的播放页揉在同一个 App 里。

如果你愿意看两眼、点点 star，或者给我提个 issue，我都会很开心 🙏。

> 名字取自 **NACRE（珍珠母）**——希望它能像珍珠母一样，温润、安静，又带一点柔和的光泽。

---

## 🎯 它是什么

NACRE 是一款 Android 音频应用，核心是**两个模式**：

| 模式 | 做什么 | 适合 |
|------|--------|------|
| 🌙 **ASMR 模式** | 本地音频 + 白噪音 + 双耳节拍 + 助眠工具 | 放松、冥想、专注、助眠 |
| 🎵 **MUSIC 模式** | 在线音乐（网易云 / QQ 音乐）+ 歌词 + 榜单推荐 | 日常听歌 |

两个模式共用同一套音频引擎（Media3 ExoPlayer 双播放器混音），你可以在主音频之外再叠一层背景环境音，营造沉浸氛围。

---

## ✨ 功能总览

### 🎵 在线音乐（MUSIC 模式）

| 功能 | 说明 |
|------|------|
| **双音源** | 网易云音乐 + QQ 音乐，原生实现（非套壳） |
| **搜索** | 跨平台搜索歌曲 |
| **歌单 / 榜单 / 推荐** | 排行榜、每日推荐、私人 FM、用户歌单 |
| **登录** | 网易云账号密码 / 扫码登录；QQ 音乐网页登录 |
| **歌词** | 逐词 / 整句歌词、滚动歌词（平铺 / 鱼眼两种样式）、歌词偏移校准 |
| **多音质** | 标准 / 较高 / 极高 / 无损（按源逐档回退） |
| **收藏** | 跨源免登录收藏到本地「我喜欢」 |

> 网易云 `weapi` / `xeapi` 加密、QQ 音乐 `zzc` 签名，都是对着公开资料一行行啃下来自己实现的——这也是这个项目里我最有成就感、也最容易写错的部分（X25519、AES、HMAC 之类的坑真的很多）。

### 🌙 ASMR 模式（助眠 · 放松）

| 功能 | 说明 |
|------|------|
| **本地音频库** | 自动扫描设备存储，也可手动导入 |
| **内置自然音效** | 🌧️ 雨声 · 🌊 溪流 · 🔥 篝火 · 💨 风声 · ⚡ 雷声 · 🌊 海浪（6 种免费 OGG） |
| **环境音混音** | 环境音可与主音频同时播放，独立调节 |
| **双耳节拍** | Delta / Theta / Alpha / Beta / Gamma 多档预设 |
| **噪音生成器** | 白噪音 / 粉红噪音 / 棕色噪音，独立音量 |
| **3D 空间音效** | 左右扫掠 / 环绕旋转 / 空间扩展 |
| **催眠模式** | 渐变动画背景 + 自动隐藏 UI |
| **睡眠定时器** | 定时自动停止，带倒计时 |
| **番茄钟** | 专注 / 休息循环 |
| **睡眠日志** | 记录并回顾睡眠时段 |

### 🎛️ 音频效果

| 效果 | 说明 |
|------|------|
| **3 段均衡器** | 低音 / 中音 / 高音 ±10dB，自动适配设备频段 |
| **音量触发特效** | 喷洒 / 闪烁 / 喷泉动画，颜色与 Emoji 可自定义 |
| **音频可视化** | 实时 FFT，柱状 / 波形 / 点阵 / 环形 / 对称 / 光晕 6 种风格 |
| **触觉反馈** | 跟随节拍振动，强度可调 |

### ✨ 视觉与主题

| 功能 | 说明 |
|------|------|
| **双色调封面背景** | 从封面真实取色，叠加 FFT 柔光柱，整屏跟随音频流动 |
| **7 套主题色** | 苹果蓝 / 暗夜紫 / 暖橙 / 森林绿 / 海洋蓝 / 玫瑰金 / 极简灰 |
| **主题模式** | 跟随系统 / 浅色 / 深色 |
| **自定义字体** | 导入 `.ttf` / `.otf`，全局生效 |
| **背景图库** | 导入图片，可绑定到特定音频或全局使用 |
| **图片幻灯片** | 全屏图片轮播，视觉放松 |

### 📂 内容管理

| 功能 | 说明 |
|------|------|
| **播放列表** | 创建 / 编辑 / 播放自定义歌单 |
| **收藏夹** | 星标收藏，快速访问 |
| **书签** | 保存带名称的播放位置 |
| **播放历史** | 带时间戳的播放记录 |
| **视频提取音频** | 从视频文件提取音频轨道 |
| **分享导入** | 从其他 App 分享链接，自动解析为可播放内容 |

### 🔧 便捷功能

| 功能 | 说明 |
|------|------|
| **后台播放** | 前台服务 + WakeLock + MediaSession，息屏持续播放 |
| **媒体控制** | 通知栏（MediaStyle）/ 锁屏 / 蓝牙耳机 / 线控 |
| **电池优化引导** | 自动检测国产 ROM（MIUI / ColorOS / OriginOS），引导加入白名单 |
| **桌面小组件** | 主屏一键播放 / 暂停 |
| **应用快捷方式** | 长按图标：继续播放 / 30 分钟定时 / 收藏 / 历史 |
| **隐私模式** | 截图与应用切换器中隐藏音频标题 |
| **触发板** | 自定义音效按键，独立 / 并行播放 |
| **场景预设** | 一键保存 / 恢复完整播放状态 |

---

## 🏗️ 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin 2.x |
| UI | Jetpack Compose + Material 3 |
| 导航 | Jetpack Navigation Compose（三页横向滑动 + 子页） |
| 依赖注入 | Hilt + KSP |
| 数据库 | Room + KSP |
| 播放 | Media3 ExoPlayer + MediaSession（双实例混音） |
| 网络 | OkHttp（音源层） |
| 加密 | 自研：网易云 weapi / xeapi（RSA、AES、X25519 ECDH、HMAC）、QQ zzc 签名 |
| 图片 | Coil + Palette |
| 异步 | Kotlin Coroutines + StateFlow |
| 音频特效 | Android Equalizer / Visualizer / Virtualizer + AudioTrack |
| 小组件 | AppWidgetProvider + RemoteViews |
| 触觉 | Vibrator / VibratorManager |

---

## 🧱 项目结构

```
app/src/main/java/com/asmrhelper/
├── data/                        # 数据层
│   ├── local/db/               # Room 数据库、DAO、实体
│   ├── local/scanner/          # 本地音频扫描
│   ├── mapper/                 # 实体 ↔ 领域模型映射
│   ├── music/                  # 在线音乐源
│   │   ├── netease/            #   网易云（weapi/xeapi/eapi）
│   │   ├── neteaselib/         #   X25519 / Xeapi / Weapi / Eapi 加密库
│   │   └── qq/                 #   QQ 音乐（zzc 签名）
│   └── repository/             # 仓库实现
├── di/                         # Hilt 依赖注入
├── domain/
│   ├── model/                  # 领域模型
│   └── repository/             # 仓库接口
├── player/                     # 音频引擎层
│   ├── PlayerManager.kt        #   播放编排（双 ExoPlayer）
│   ├── AsmrMediaService.kt     #   前台服务 + WakeLock + MediaSession
│   ├── EqualizerController.kt  #   均衡器
│   ├── BinauralBeatEngine.kt   #   双耳节拍合成
│   ├── NoiseGenerator.kt       #   白/粉/棕噪音
│   ├── SpatialAudioController.kt # 空间音效
│   ├── AudioVisualizerController.kt # FFT 采集
│   ├── SceneEffectsController.kt   # 触发特效
│   ├── AudioFocusManager.kt    #   音频焦点
│   ├── AudioDeviceMonitor.kt   #   耳机/蓝牙拔插监听
│   └── VideoAudioExtractor.kt  #   视频提取音频
├── ui/                         # 表现层（Compose）
│   ├── play/                   #   播放页（ASMR + MUSIC）
│   ├── discover/               #   发现页（在线音乐）
│   ├── profile/                #   我的
│   ├── library/ playlist/ history/ background/ triggerpad/ sleep/ slideshow/
│   ├── settings/               #   设置
│   ├── components/             #   共享组件
│   └── theme/                  #   Material 3 主题、色彩预设、字体
├── util/                       # 工具类（电池优化、隐私遮罩、字体、本地化等）
├── widget/                     # 桌面小组件
├── MainActivity.kt             # 单 Activity
├── AsmrApplication.kt          # Hilt 应用 + 通知渠道
└── CrashHandler.kt             # 全局崩溃捕获
```

---

## 🚀 构建与运行

| 要求 | 版本 |
|------|------|
| 系统 | Android 8.0+（API 26） |
| IDE | Android Studio（较新版本即可） |
| JDK | 17 |
| Gradle | 8.x |
| Kotlin | 2.x |

```bash
# 克隆仓库
git clone https://github.com/JiaoZi258/NACRE.git

# 用 Android Studio 打开 → 同步 Gradle → 运行
```

---

## ⚠️ 免责声明

- 这是一个**个人学习项目**，代码质量参差不齐，请勿用于生产或商业用途。
- 内置的在线音乐源是**逆向第三方公开接口**实现的，仅供学习研究，接口随时可能失效或产生账号风险，使用请自行负责。
- 不提供任何付费/版权内容的破解，VIP 歌曲等版权受限内容会正常提示无法播放。

---

## 🙏 致谢

- 感谢 Jetpack Compose、Media3、Room、Hilt、Coil 等开源库，以及整个 Android 开源生态。
- 感谢 AI 助手陪我一起把这个项目从零写到今天。

---

<div align="center">
  <br>
  <p>Made with ❤️、☕ 和一点点的 vibe ✨</p>
  <p>如果它对你有用，欢迎点个 ⭐️</p>
</div>
