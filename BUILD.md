# 构建指南（BUILD.md）

> 本项目仅包含 Android App，引擎为预编译 `.so` 形式分发。

## 环境要求

- JDK 17+
- Android SDK 34
- Android NDK r26c（可选，仅用于重新编译引擎）

## 构建步骤

### 配置 local.properties

```properties
sdk.dir=/path/to/android-sdk
ndk.dir=/path/to/android-ndk-r26c
```

### 构建 Debug APK
```bash
./gradlew assembleDebug
```
输出：`build/outputs/apk/debug/app-debug.apk`

### 构建 Release APK
```bash
./gradlew assembleRelease
```
输出：`build/outputs/apk/release/app-release-unsigned.apk`

> Release 版本需要签名，见 README.md 中的签名配置说明。

## 引擎说明

底层引擎为闭源 Rust 库（`librodroid_il2cppdumper.so`），以预编译形式随 APK 分发：

| 架构 | 路径 |
|---|---|
| arm64-v8a | `src/main/jniLibs/arm64-v8a/librodroid_il2cppdumper.so` |
| armeabi-v7a | `src/main/jniLibs/armeabi-v7a/librodroid_il2cppdumper.so` |

如需重新编译引擎，请参考 [il2cpp-dumper-rs 仓库](https://github.com/fvgfgtxdeujv/il2cpp-dumper-rs)。
