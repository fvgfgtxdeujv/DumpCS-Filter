# User Instruction Memory

This file records user instructions, preferences, and teachings for reference in future interactions.

## Format

### User Instruction Entry
[User Instruction Summary]
- Date: [YYYY-MM-DD]
- Context: [Mentioned scenario or time]
- Instructions:
  - [Content of user teaching or instruction, described line by line]

### Project Knowledge Entry
[Project Knowledge Summary]
- Date: [YYYY-MM-DD]
- Context: Discovered by Agent while performing [specific task description]
- Category: [Operations & Deployment|Build Methods|Testing Methods|Troubleshooting & Debugging|Workflow & Collaboration|Environment Configuration]
- Instructions:
  - [Specific knowledge points, described line by line]

## Deduplication Strategy
- Before adding a new entry, check for similar or identical instructions.
- If a duplicate is found, skip the new entry or merge it with the existing one.

## Entries

[User Instruction Summary]
- Date: 2026-08-21
- Context: 用户要求将 DumpCSFilter 预编译 librodroid_il2cppdumper.so 改为源码构建；提供 APK 进行对比分析
- Instructions:
  - 不信任预编译 .so，要求从 il2cpp-dumper-rs 源码重建 arm64/armeabi .so
  - APK（com.rodroid.il2cppdumper v7.0）经分析与本地 Rust 引擎高度同源（版本 7.0 是旧版，本地源码已超越）
  - 使用 cargo-ndk 交叉编译 release + LTO fat 构建，替代原预编译 .so

[Project Knowledge Summary]
- Date: 2026-08-21
- Context: 交叉编译 librodroid_il2cppdumper.so 到 Android（arm64-v8a + armeabi-v7a）
- Category: Build Methods
- Instructions:
  - NDK 安装位置：/tmp/opencode/android-ndk-r26c（ANDROID_NDK_HOME）
  - cargo-ndk v2.0.0 安装至 /root/.cargo/bin/cargo-ndk
  - rustup targets：aarch64-linux-android、armv7-linux-androideabi 已安装
  - 交叉编译命令：cargo ndk -t arm64-v8a -t armeabi-v7a -o ../jniLibs build --release
  - cargo-ndk 在 NDK r26c 的 armeabi-v7a 构建中 strip 步骤会 crash（找不到 llvm-strip 路径），需手动用 /tmp/opencode/android-ndk-r26c/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip --strip-unneeded 处理 target/armv7-linux-androideabi/release/librodroid_il2cppdumper.so 后拷贝到 jniLibs/armeabi-v7a/
  - 产物：app/src/main/jniLibs/arm64-v8a/librodroid_il2cppdumper.so（6.2MB release+strip）和 app/src/main/jniLibs/armeabi-v7a/librodroid_il2cppdumper.so（5.3MB release+strip）
  - build.gradle.kts 中 buildRustEngine 任务默认 enabled=false（需 rust.engine.enabled=true）

[Project Knowledge Summary]
- Date: 2026-08-21
- Context: APK 反向分析验证引擎差异
- Category: Troubleshooting & Debugging
- Instructions:
  - 用 apktool d -f -o decoded real.apk 反编译 APK（包名 com.rodroid.il2cppdumper v7.0，以 MT管理器 bin.mt.plus.TranslationData 格式打包）
  - 用 readelf/strings/radare2 分析 so 导出符号、函数大小、字符串特征
  - 本地 engine 字符串集与 APK so 字符串集做 comm -13 diff，过滤模板噪音后识别功能差异
  - APK v7.0 的 so 是引擎旧版：getDefaultConfig 8120B vs 我们 3580B；dumpIl2Cpp 1192B vs 我们 3544B；detectFormat 1444B vs 我们 3268B；initCrashHandler 880B vs 我们 2368B
  - 我们的源码已包含阶段 2 全部功能（v106.1/v107/v108/v110 + Mfuscator）
  - APK 独有字符串 "Auto-selected architecture"（dump 流程架构推断），本地缺失，后续可补充


[Project Knowledge Summary]
- Date: 2026-08-21
- Context: 仓库拆分：引擎仓 il2cpp-dumper-rs 独立，App 仓 app 依赖预编译 .so
- Category: Workflow & Collaboration
- Instructions:
  - 引擎仓路径：/workspace/il2cpp-dumper-rs/（含 src/、jniLibs/、Cargo.toml、dotnetdll-main/）
  - App 仓路径：/workspace/app/（Kotlin 源码，jniLibs 引用引擎预编译 .so）
  - app/build.gradle.kts 中 buildRustEngine 任务已移除，gradle.properties 中 rust.engine.enabled 已删除
  - 引擎更新需先在 il2cpp-dumper-rs 仓重新编译 .so，再复制到 app/src/main/jniLibs/
  - dotnetdll（GPL-3.0+）是 DummyDll 输出的 GPL 依赖，引擎仓 README 中有说明
  - DumpViewModel 中 InputRequest 结构化协议（DumpAddress/ManualAddresses/Architecture）已合并到 app 仓

[Project Knowledge Summary]
- Date: 2026-08-21
- Context: APK 反向分析验证引擎差异
- Category: Workflow & Collaboration
- Instructions:
  - APK（com.rodroid.il2cppdumper v7.0）经二进制分析和字符串 diff 后确认：.so 与本地 Rust 引擎高度同源，APK 是旧版（v7.0），本地已超越
  - APK 独有功能差异：结构化 InputRequest 类型（DumpAddress/ManualAddresses/Architecture）、GCC/MSVC 编译器布局选项、RememberSelectedPaths 持久化
  - 已将 InputRequest 结构化类型合并到 DumpViewModel（sealed class + 三步分发逻辑 + pendingCodeReg 两步状态）
  - NDK r26c 安装于 /tmp/opencode/android-ndk-r26c，cargo-ndk v2.0.0 可用
  - release .so 交叉编译输出：app/src/main/jniLibs/arm64-v8a/（6.2MB）+ armeabi-v7a/（5.3MB）

[Project Knowledge Summary]
- Date: 2026-08-21
- Context: 配置 GitHub Actions CI/CD 构建流程
- Category: Operations & Deployment
- Instructions:
  - workflow 位于 app/.github/workflows/build.yml
  - 手动触发（workflow_dispatch）时可选择 debug 或 release
  - debug 构建无需 tag，自动上传 artifact
  - release 构建可选择填写 tag，填写后自动创建 GitHub Release 并上传 APK
  - 构建命令：./gradlew :app:assembleDebug 或 :app:assembleRelease
  - Gradle Wrapper 已随仓分发（gradlew + gradle/wrapper/），无需额外安装 Gradle

[Project Knowledge Summary]
- Date: 2026-08-21
- Context: 引擎仓 il2cpp-dumper-rs 已删除，仅保留 app 项目
- Category: Operations & Deployment
- Instructions:
  - 引擎源码已从仓库删除，/workspace/il2cpp-dumper-rs 不再存在
  - app/build.gradle.kts 中无 rust.engine 相关配置，jniLibs 使用预编译 .so
  - 如需重新引入引擎，需从 GitHub 仓库 https://github.com/fvgfgtxdeujv/il2cpp-dumper-rs 获取
