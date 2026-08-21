# 二次开发与插件教程（DEV_GUIDE.md）

本教程面向**想基于 DumpCS Filter App 二改**的开发者。

---

## 仓库结构

```
├── .github/workflows/build.yml   # GitHub Actions CI/CD
├── build.gradle.kts              # Android 构建配置
├── gradle/                       # Gradle Wrapper
├── gradlew                       # Gradle Wrapper 脚本
├── local.properties              # 本地 SDK 路径配置
├── src/main/                     # Kotlin 源码
│   ├── java/com/dumpcs/filter/   # 应用源码
│   │   ├── data/                 # 数据层（parser/engine/ai/local/storage）
│   │   ├── music/                # 背景音乐
│   │   ├── （已合并）              # 服务层
│   │   ├── ui/                   # UI 层（screens/components/theme/navigation/viewmodel）
│   │   └── update/               # 自动更新
│   └── jniLibs/                  # 预编译引擎 .so
├── BUILD.md                      # 构建指南
└── DEV_GUIDE.md                  # 本文件
```

---

## 一、常见二改点

| 想改什么 | 文件位置 | 怎么改 |
|---|---|---|
| **应用名称** | `src/main/res/values/strings.xml` | `app_name` |
| **应用图标** | `src/main/res/mipmap-*` | 替换各密度图标 |
| **包名** | `build.gradle.kts` + `AndroidManifest.xml` | 修改 `applicationId` / `package` / `namespace` |
| **主题色** | `src/main/java/.../ui/` | 搜索颜色常量修改 |
| **启动动画** | `src/main/java/.../ui/SplashScreen.kt` | Canvas 绘制参数 |
| **背景音乐开关默认值** | `src/main/java/.../ui/SettingsScreen.kt` | `music_enabled` 默认 true → false |
| **四页顺序/名称** | `src/main/java/.../ui/MainTabsScreen.kt` | NavigationBar 四个 Tab 与 Pager 页 |
| **输出目录** | `src/main/java/.../data/` | 默认 `/sdcard/DumpCSFilter/` |
| **版本/更新地址** | `src/main/java/.../update/UpdateManager.kt` | `DEFAULT_CHECK_URL` 常量 |

> 改包名后记得同步改 `AndroidManifest.xml` 的 `authorities`（FileProvider）里的 `${applicationId}`。

---

## 二、插件机制

### Lua 脚本插件（免编译）

应用内置 GameGuardian Lua 工具集，脚本输出到 `/sdcard/DumpCSFilter/lua功能/`，手写 `.lua` 放进去 GG 直接执行。

### 代码级插件（需重新编译）

以「新增一个页面」为例：

1. **注册路由**：`src/main/java/.../ui/Screen.kt` 加一行
   ```kotlin
   data object MyPlugin : Screen("my_plugin")
   ```

2. **写页面**：`src/main/java/.../ui/MyPluginScreen.kt`
   ```kotlin
   @Composable
   fun MyPluginScreen(navController: NavHostController) { ... }
   ```

3. **挂到导航**：`MainActivity.kt` 的 NavHost 里加
   ```kotlin
   composable(Screen.MyPlugin.route) { MyPluginScreen(navController) }
   ```

4. **入口按钮**：任意页面加 `Button { navController.navigate(Screen.MyPlugin.route) }`

---

## 三、打包

```bash
# Debug 包
./gradlew assembleDebug
# 产物: build/outputs/apk/debug/app-debug.apk

# Release 包
./gradlew assembleRelease
# 产物: build/outputs/apk/release/app-release-unsigned.apk
```

---

## 四、GitHub Actions 手动构建

1. 进入仓库 [Actions](https://github.com/fvgfgtxdeujv/DumpCS-Filter/actions) 标签页
2. 选择 "Build Android APK"
3. 下拉选择构建类型：
   - `debug`：直接下载 artifact
   - `release`：可选填写 tag 自动创建 GitHub Release
4. 点击 "Run workflow"

---

## 五、常见问题

| 问题 | 解决 |
|---|---|
| App 改包名后崩溃 | 检查 FileProvider `authorities`、`namespace`、`applicationId` 三者一致 |
| 音乐不播放 | 确认设备有音频文件；设置页开关已打开 |
| App 构建报错 | 先 `./gradlew clean`，再重新构建；确认 JDK17 + SDK34 |
| NDK 路径找不到 | 设置 `ANDROID_NDK_HOME=/path/to/android-ndk-r26c` |
| 更新检查失败 | 检查 `update.json` 格式；确认 `DEFAULT_CHECK_URL` 可访问 |
