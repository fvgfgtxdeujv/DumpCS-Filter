# 🛡️ DumpCS Filter

> **一款专为游戏逆向 / 二次开发打造的 dump.cs 智能筛选工具**
> 版本：**V10.1** ｜ 包名：`com.dumpcs.filter` ｜ 支持：Android 8.0+

---

## ✨ 它是什么？

`Il2CppDumper` 导出的 `dump.cs` 动辄几十 MB、类名成千上万，手动翻找类名简直是噩梦。
**DumpCS Filter** 让你像聊天一样筛选类名：

- 说一句中文（如「透视」「红透热透」），AI 自动帮你找到目标类
- 支持对话式筛选、猫娘解释、偏移量展示
- 筛选结果自动导出到 `/sdcard/DumpCSFilter/`

---

## 🎨 核心亮点（V10.1）

### 🔐 卡密登录 + 图形人机验证
- 谷歌风格**紫白**登录页，固定卡密 `WUHAI-8888`（本地校验，无后端）
- 登录必须过**图形人机验证**：纯 Canvas 自绘 12 种图形（五角星/圆形/爱心/月牙…），每次、每台设备都不同
- 登录成功播放「欢迎回来，尊贵的VIP用户」语音 + 紫色成功浮层
- 绑定后下次免登录；设置页可**退出登录**

### 📱 三页左右滑动
- 查找 / Dump / 脚本 三页**丝滑左右滑动**切换，底部导航双向联动，零卡顿

### 🎵 背景音乐
- 设备本地音乐**随机播放**，每次进入顺序都不一样，播完自动切下一首
- 顶部弹出**专辑图 + 歌名**卡片（1.5 秒自动关闭）
- 设置页可暂停 / 永久关闭 / **导入歌曲**

### 🚀 其他
- 深空流星启动动画（仅首次安装播放）
- 76MB 大文件**流式解析**，不卡不死
- Room 本地历史记录 + AI 多接口接入

---

## 📦 快速开始

1. **构建**：见同目录 `BUILD.md`（JDK17 + Android SDK34 + Gradle8.4）
2. **安装**：`adb install app/build/outputs/apk/debug/app-debug.apk`
3. **登录**：输入卡密 `WUHAI-8888` → 过图形验证 → 进入主界面
4. **使用**：选择 dump.cs 文件 → 中文提问筛选 → 结果自动导出

---

## 🔧 技术栈

| 层面 | 技术 |
|---|---|
| UI | Jetpack Compose + Material 3 |
| 导航 | Navigation Compose（含转场动画） |
| 存储 | Room + DataStore Preferences |
| 音频 | MediaPlayer + MediaMetadataRetriever（专辑图） |
| 验证 | 纯 Canvas 自绘图形验证（无 Emoji / 无后端） |
| 解析 | 流式逐行解析（Reader.forEachLine） |

---

## 📁 目录速览

```
app/src/main/java/com/dumpcs/filter/
├── MainActivity.kt            # 入口（卡密判断 + 导航 + 音乐）
├── music/MusicPlayer.kt       # 背景音乐引擎
├── ui/screens/                # 全部页面（登录/三页/设置/结果/历史/AI聊天）
├── ui/components/             # 图形验证 + 切歌弹窗
└── res/raw/vip_welcome.mp3    # 登录语音
```

---

## 📝 备注

- 卡密修改：`LoginScreen.kt` → `FIXED_CARD_KEY`
- 登录音频替换：`app/src/main/res/raw/vip_welcome.mp3`
- 本版本**未启用加固/混淆**，纯净源码，欢迎学习交流

---

*DumpCS Filter · 为热爱逆向的你而生* 🚀
