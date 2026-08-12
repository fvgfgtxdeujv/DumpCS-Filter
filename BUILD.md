# DumpCSFilter 构建指南（BUILD.md）

本指南教你如何把 **DumpCSFilter**（DumpCS 筛选工具）源码从零构建成可安装的 APK。

---

## 一、项目简介

- **包名**：`com.dumpcs.filter`
- **应用名**：DumpCS Filter
- **版本**：V10.1
- **技术栈**：Kotlin + Jetpack Compose（Material 3）+ Room + DataStore + Navigation Compose
- **最低系统**：Android 8.0（API 26）
- **目标系统**：Android 14（API 34）

---

## 二、环境要求

| 组件 | 版本要求 | 说明 |
|---|---|---|
| JDK | 17+ | 编译必需 |
| Android SDK | API 34（android-34） | 编译目标 |
| Gradle | 8.4 | 项目自带 wrapper，也可用全局 gradle-8.4 |
| Kotlin | 1.9.x（编译器扩展 1.5.8） | 项目已配置 |

> 本项目**不需要 NDK / CMake**（已移除 Native 依赖），纯 Kotlin/Java 源码，构建更简单。

---

## 三、目录结构

```
DumpCSFilter/
├── app/
│   ├── build.gradle.kts          # App 模块构建配置
│   ├── proguard-rules.pro        # 混淆规则（当前未启用）
│   └── src/main/
│       ├── AndroidManifest.xml   # 清单文件
│       ├── java/com/dumpcs/filter/
│       │   ├── MainActivity.kt           # 入口：卡密绑定判断 + 导航 + 音乐启动
│       │   ├── DumpCSApplication.kt      # Application
│       │   ├── music/MusicPlayer.kt      # 背景音乐引擎（随机播放/导入/切歌）
│       │   ├── ui/screens/
│       │   │   ├── LoginScreen.kt        # 紫白卡密登录页（固定卡密 WUHAI-8888）
│       │   │   ├── MainTabsScreen.kt     # 三页左右滑动容器（查找/Dump/脚本）
│       │   │   ├── HomeScreen.kt / DumpScreen.kt / ScriptScreen.kt
│       │   │   ├── ResultsScreen.kt / HistoryScreen.kt / FileViewerScreen.kt
│       │   │   ├── AiChatScreen.kt / SettingsScreen.kt（含退出登录）
│       │   │   └── SplashScreen.kt       # 深空流星启动动画
│       │   ├── ui/components/
│       │   │   ├── ImageCaptcha.kt       # 自绘图形人机验证（12 种图形，设备+时间双种子）
│       │   │   └── MusicNowPlayingCard.kt# 顶部切歌弹窗（专辑图+歌名）
│       │   ├── ui/navigation/Screen.kt   # 路由定义
│       │   ├── data/                     # Room 数据库
│       │   └── service/                  # 业务服务
│       └── res/raw/vip_welcome.mp3       # 登录成功音频（欢迎回来尊贵的VIP用户）
├── build.gradle.kts / settings.gradle.kts / gradle.properties
└── gradle/                              # Gradle wrapper
```

---

## 四、构建步骤

### 方法 A：命令行构建（推荐）

```bash
# 1. 配置环境变量（按你的 SDK 路径修改）
export ANDROID_HOME=/path/to/android-sdk

# 2. 进入项目目录
cd DumpCSFilter

# 3. 构建 Debug APK
gradle assembleDebug --no-daemon
# 或使用 wrapper：./gradlew assembleDebug

# 4. 输出路径
#    app/build/outputs/apk/debug/app-debug.apk
```

### 方法 B：Android Studio 构建

1. `File → Open` 选择 `DumpCSFilter` 目录
2. 等待 Gradle 同步完成（首次需下载依赖）
3. `Build → Build APK(s)` 或点击 Run 直接安装到设备

---

## 五、安装 APK

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

或拷贝到手机后用 MT 管理器 / 系统安装器安装。

> 首次启动需授予「所有文件访问」权限（用于读取 dump.cs 和扫描音乐）。

---

## 六、登录与卡密

- **固定卡密**：`WUHAI-8888`（在 `LoginScreen.kt` 中 `FIXED_CARD_KEY` 修改）
- 登录流程：输入卡密 → 弹出**图形人机验证**（自绘九宫格，选目标图形）→ 验证通过 → 播放登录音频 → 绑定成功
- 绑定后**下次启动免登录**；设置页可「退出登录」清除绑定

---

## 七、常见问题

| 问题 | 解决 |
|---|---|
| Gradle 同步失败 | 确认 ANDROID_HOME 指向 API 34 的 SDK |
| 构建报 Kotlin 版本错误 | 使用 JDK 17，Kotlin 编译器扩展固定 1.5.8 |
| 安装后进不去 | 检查是否开启「所有文件访问」权限 |
| 背景音乐不播 | 设置页开启背景音乐开关；确认设备有 mp3/flac 等音频 |

---

## 八、功能清单（V10.1）

- ✅ 卡密登录 + 图形人机验证（紫白谷歌风格，纯 Canvas 自绘，无后端）
- ✅ 登录成功播放音频「欢迎回来，尊贵的VIP用户」
- ✅ 三页左右滑动（查找 / Dump / 脚本），底部导航双向联动
- ✅ 背景音乐随机播放（每次进入顺序不同、播完自动切歌、设置页暂停/永久关闭/导入歌曲）
- ✅ 切歌顶部弹窗（专辑图 + 歌名，1.5 秒自动消失）
- ✅ 深空流星启动页动画（仅首次安装播放）
- ✅ 大文件流式解析（76MB dump.cs 不 OOM）
- ✅ 设置页退出登录
