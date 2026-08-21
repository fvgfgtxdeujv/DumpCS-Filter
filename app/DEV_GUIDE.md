# il2cpp-dumper-rs 二次开发与插件教程（DEV_GUIDE.md）

本教程面向**想基于 il2cpp-dumper-rs 引擎二改、或基于 DumpCS Filter App 二改**的开发者。

---

## 仓库结构

```
il2cpp-dumper-rs/          # Rust 引擎仓
├── src/                   # 引擎源码（49 个 .rs 文件）
├── jniLibs/               # 预编译 .so（arm64-v8a + armeabi-v7a）
├── dotnetdll-main/        # GPL 依赖源码
├── Cargo.toml / Cargo.lock
└── README.md              # 引擎构建说明

app/                       # Android App 仓（DumpCS Filter）
├── src/main/java/         # Kotlin 应用源码
├── src/main/jniLibs/      # 引用引擎预编译 .so
├── build.gradle.kts
└── BUILD.md / DEV_GUIDE.md
```

---

## 一、引擎仓二改（Rust 部分）

> 引擎源码仅供审查，不公开构建流程。此处仅作结构说明。

### 新增 metadata 版本支持

编辑 `src/il2cpp/structures.rs`：
- `known_versions` 数组追加版本号
- `resolve_sub_version` 函数增加版本分支
- `Il2CppGlobalMetadataHeader` 追加新 section 字段
- `IndexWidths` 追加动态宽度推导函数

### 新增解密算法

编辑 `src/dump_engine.rs` 的 `try_decrypt_metadata` 函数，在现有解密链末尾追加新分支。

### 宿主 CLI 测试

```bash
cargo check --all-targets   # 零警告
cargo test                  # 16 个测试通过
cargo run --release -- --help
```

---

## 二、App 仓二改（Kotlin 部分）

### 常见二改点

| 想改什么 | 文件位置 | 怎么改 |
|---|---|---|
| **应用名称** | `app/res/values/strings.xml` | `app_name` |
| **应用图标** | `app/res/mipmap-*` | 替换各密度图标 |
| **包名** | `app/build.gradle.kts` + `AndroidManifest.xml` | 修改 `applicationId` / `package` / `namespace` |
| **主题色** | `app/ui/theme/` | 搜索颜色常量修改 |
| **启动动画** | `app/ui/screens/SplashScreen.kt` | Canvas 绘制参数 |
| **背景音乐开关默认值** | `app/ui/screens/SettingsScreen.kt` | `music_enabled` 默认 true → false |
| **四页顺序/名称** | `app/ui/screens/MainTabsScreen.kt` | NavigationBar 四个 Tab 与 Pager 页 |
| **输出目录** | `app/service/` | 默认 `/sdcard/DumpCSFilter/` |

> 改包名后记得同步改 `AndroidManifest.xml` 的 `authorities`（FileProvider）里的 `${applicationId}`。

---

## 三、插件机制

### Lua 脚本插件（免编译）

应用内置 GameGuardian Lua 工具集，脚本输出到 `/sdcard/DumpCSFilter/lua功能/`，手写 .lua 放进去 GG 直接执行。

### 代码级插件（需重新编译）

以「新增一个页面」为例：

1. **注册路由**：`app/ui/navigation/Screen.kt` 加一行
   ```kotlin
   data object MyPlugin : Screen("my_plugin")
   ```

2. **写页面**：`app/ui/screens/MyPluginScreen.kt`
   ```kotlin
   @Composable
   fun MyPluginScreen(navController: NavHostController) { ... }
   ```

3. **挂到导航**：`app/src/main/java/com/rodroid/il2cppdumper/utils/DumperBridge.java` 所在包的 NavHost 里加
   ```kotlin
   composable(Screen.MyPlugin.route) { MyPluginScreen(navController) }
   ```

4. **入口按钮**：任意页面加 `Button { navController.navigate(Screen.MyPlugin.route) }`

---

## 四、打包

```bash
# App Debug 包
cd app
./gradlew assembleDebug
# 产物: app/build/outputs/apk/debug/app-debug.apk
```

---

## 五、常见问题

| 问题 | 解决 |
|---|---|
| App 改包名后崩溃 | 检查 FileProvider `authorities`、`namespace`、`applicationId` 三者一致 |
| 音乐不播放 | 确认设备有音频文件；设置页开关已打开 |
| App 构建报错 | 先 `./gradlew clean`，再重新构建；确认 JDK17 + SDK34 |
| 引擎 cargo check 警告 | 宿主环境无 NDK target，部分 Android-only 代码会被标记为 dead_code；在 Android target 下正常 |
| NDK 路径找不到 | 设置 `ANDROID_NDK_HOME=/path/to/android-ndk-r26c` |
| 更新引擎 .so | 引擎仓内部流程，不对外公开 |
