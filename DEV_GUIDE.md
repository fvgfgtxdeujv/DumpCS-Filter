# DumpCSFilter 二次开发与插件教程（DEV_GUIDE.md）

本教程面向**想基于 DumpCSFilter 二改、加插件、发布自己版本**的开发者。
源码已去除装饰性注释，干净可读，可自由修改、重新打包、发布。

---

## 一、拉取项目

### 方式 A：从 Git 仓库拉取（推荐）

```bash
# 1. 克隆仓库（把 URL 换成作者仓库地址）
git clone https://github.com/你的用户名/DumpCSFilter.git
cd DumpCSFilter

# 2. 查看提交历史
git log --oneline

# 3. 后续更新（作者推送新版本后）
git pull origin main
```

### 方式 B：直接下载源码压缩包

从作者处获取 `DumpCSFilter-src.zip`，解压到本地任意目录即可。

---

## 二、环境搭建（首次）

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 17+ | 编译必需 |
| Android Studio | 最新稳定版 | 推荐 IDE |
| Android SDK | API 34（android-34） | 编译目标 |
| Gradle | 8.4（项目自带配置） | 无需单独安装 |

```bash
# Linux/Mac 命令行构建
export ANDROID_HOME=$HOME/Android/Sdk
cd DumpCSFilter
./gradlew assembleDebug
# 产物: app/build/outputs/apk/debug/app-debug.apk
```

> Android Studio：`File → Open` 选项目目录 → 等 Gradle 同步 → `Run` 直接装到手机。

---

## 三、常见二改点（改完即可重新打包）

| 想改什么 | 文件位置 | 怎么改 |
|---|---|---|
| **卡密** | `ui/screens/LoginScreen.kt` | `FIXED_CARD_KEY = "WUHAI-8888"` 改成你的卡密 |
| **登录音频** | `res/raw/vip_welcome.mp3` | 替换同名 mp3（保持文件名） |
| **应用名称** | `res/values/strings.xml` | `app_name` |
| **应用图标** | `res/mipmap-*` | 替换各密度图标 |
| **包名** | `app/build.gradle.kts` + `AndroidManifest.xml` | 修改 `applicationId` / `package` / `namespace` |
| **紫白主题色** | `ui/screens/LoginScreen.kt` | 搜索 `0xFF7B1FA2`（主紫）、`0xFFAB47BC`（渐变）、`0xFFFDF7FF`（背景） |
| **启动动画** | `ui/screens/SplashScreen.kt` | 星星/流星/粒子参数在 Canvas 绘制代码里 |
| **背景音乐开关默认值** | `SettingsScreen.kt` | `music_enabled` 默认 true → 改 false 即默认不播 |
| **三页顺序/名称** | `ui/screens/MainTabsScreen.kt` | NavigationBar 三个 Tab 与 Pager 页 |
| **输出目录** | `service/` 或导出逻辑 | 默认 `/sdcard/DumpCSFilter/` |

> 改包名后记得同步改 `AndroidManifest.xml` 的 `authorities`（FileProvider）里的 `${applicationId}`。

---

## 四、插件机制（两种玩法）

### 玩法 1：Lua 脚本插件（免编译，直接装）

应用内置 **GameGuardian Lua 工具集**（「脚本」页），生成的脚本输出到：

```
/sdcard/DumpCSFilter/lua功能/
```

你可以：
1. 在「脚本」页用内置生成器（基址/功能/菜单/静态基址/偏移/去注释）生成脚本
2. 或**手写 .lua 脚本**放进 `lua功能/` 目录，GG 直接执行
3. 分享插件 = 分享这个目录下的 .lua 文件

示例插件（`lua功能/示例_热透.lua`）：

```lua
-- 热透插件示例：修改浮点值
local pid = gg.getTargetInfo().pid
gg.setRanges(gg.REGION_ANONYMOUS)
gg.searchNumber("1.0;2.0;3.0", gg.TYPE_FLOAT)
local t = gg.getResults(100)
for i, v in ipairs(t) do
    v.value = 999.0
    v.freeze = true
end
gg.setValues(t)
gg.toast("插件已生效")
```

### 玩法 2：代码级插件（需重新编译）

以「新增一个页面」为例：

1. **注册路由**：`ui/navigation/Screen.kt` 加一行
   ```kotlin
   data object MyPlugin : Screen("my_plugin")
   ```

2. **写页面**：`ui/screens/MyPluginScreen.kt`
   ```kotlin
   @Composable
   fun MyPluginScreen(navController: NavHostController) {
       // 你的功能代码
   }
   ```

3. **挂到导航**：`MainActivity.kt` 的 NavHost 里加
   ```kotlin
   composable(Screen.MyPlugin.route) { MyPluginScreen(navController) }
   ```

4. **入口按钮**：任意页面加 `Button { navController.navigate(Screen.MyPlugin.route) }`

> 应用内已用 MVVM（ViewModel）+ Compose 单 Activity 架构，新增模块照抄现有页面（如 SettingsScreen）即可。

---

## 五、打包与签名

```bash
# Debug 包（可直接安装调试）
./gradlew assembleDebug

# Release 包（未开混淆，方便二改；发布前建议配签名）
./gradlew assembleRelease
```

### 自定义签名（正式发布）

1. `Build → Generate Signed Bundle / APK` 生成 keystore
2. 或命令行：
   ```bash
   keytool -genkey -v -keystore my.keystore -alias mykey -keyalg RSA -keysize 2048 -validity 3650
   ```

### 安装

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 六、发布你自己的版本

1. 改包名（避免与作者版本冲突）
2. 换图标 / 应用名 / 卡密
3. 构建签名 APK
4. 发布到应用市场 / 群 / 网盘

> 尊重原作者：建议保留 README 中的项目来源说明。

---

## 七、常见问题

| 问题 | 解决 |
|---|---|
| 改包名后崩溃 | 检查 FileProvider `authorities`、`namespace`、`applicationId` 三者一致 |
| 音乐不播放 | 确认设备有音频文件；设置页开关已打开 |
| 卡密忘记 | 在 `LoginScreen.kt` 的 `FIXED_CARD_KEY` 查看/修改 |
| 构建报错 | 先 `./gradlew clean`，再重新构建；确认 JDK17 + SDK34 |
| 想加 AI 接口 | 设置页已有第三方 AI 接口配置入口，填入 BaseURL/Key 即可 |

---

## 八、项目架构速览

```
app/src/main/java/com/dumpcs/filter/
├── MainActivity.kt          # 单 Activity：卡密判断 → 导航 → 音乐启动
├── DumpCSApplication.kt     # Application
├── music/MusicPlayer.kt     # 背景音乐单例（随机/切歌/导入/专辑图）
├── ui/
│   ├── screens/             # 全部页面（Compose）
│   │   ├── LoginScreen.kt   # 卡密登录 + 图形验证（紫白主题）
│   │   ├── MainTabsScreen.kt# 三页滑动容器
│   │   ├── HomeScreen / DumpScreen / ScriptScreen / Results / History / FileViewer / AiChat / Settings
│   │   └── SplashScreen.kt  # 深空流星动画
│   ├── components/          # ImageCaptcha（图形验证）/ MusicNowPlayingCard（切歌弹窗）
│   ├── navigation/Screen.kt # 路由表
│   ├── viewmodel/           # MVVM 状态层
│   └── theme/               # Compose 主题
├── data/                    # Room 数据库（历史记录）
├── service/                 # 业务服务
└── res/                     # 资源（含 vip_welcome.mp3）
```

**祝二次开发愉快！有问题看 `BUILD.md` 或源码注释。** 🚀