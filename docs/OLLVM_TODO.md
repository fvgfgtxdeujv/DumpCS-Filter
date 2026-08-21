# OLLVM 接入准备（TBD）

> 状态：**待接入**，仅做记录和准备工作，不生效。

---

## 调研结论

### NDK 版本选择

| 方案 | NDK 版本 | LLVM 版本 | OLLVM pass 可用性 | 推荐度 |
|------|----------|-----------|-------------------|--------|
| Saint-Theana/ollvm-ndk-android | r25c | LLVM 16 | 全部正常 | **推荐** |
| Saint-Theana/ollvm-ndk-android | r26d | LLVM 17 | -flat、-bcf 失效 | 备选 |
| fuqiuluo/amice（自行编译） | r26 | LLVM 17 | 全部支持（plugin 方式） | 进阶选项 |

**结论**：接入 OLLVM 时将降级 NDK 至 **r25c**。

### r25c 降级影响评估

- **兼容性**：无影响，minSdk=26 / targetSdk=34 正常运行
- **安全性**：丢失 LLVM 17 的硬件 CFI / Shadow Call Stack 增强，但对本项目 native 引擎影响可忽略
- **OLLVM 收益**：r25c 比 r26d 可用 pass 更多（-flat、-bcf 均可用）
- **性能**：差异可忽略

---

## 接入前需完成的准备工作

### 1. 获取 OLLVM NDK

下载 Saint-Theana/ollvm-ndk-android 的 r25c release：

```bash
# 从 releases 下载 android-ndk-r25c-linux.tar.gz（约 1.2GB）
# 解压到本地
tar xf android-ndk-r25c-linux.tar.gz -C /opt/
# 路径示例：/opt/android-ndk-r25c/
```

### 2. 修改 local.properties

```properties
# 接入 OLLVM 后指向 r25c
ndk.dir=/opt/android-ndk-r25c
```

### 3. 确定混淆范围

目前预编译的 `librodroid_il2cppdumper.so` 是保护目标。接入后有两种策略：

| 策略 | 说明 | 复杂度 |
|------|------|--------|
| **A：仅混淆引擎 so** | 重新用 OLLVM NDK 编译 `librodroid_il2cppdumper.so`，App 代码不变 | 低 |
| **B：全量混淆** | 引擎 + Android Kotlin 代码均经过 OLLVM | 高（需 Gradle 集成） |

**建议先执行策略 A**，验证效果后再考虑 B。

### 4. 引擎重新编译（策略 A）

需要修改 `il2cpp-dumper-rs` 的构建脚本，使用 OLLVM clang 替换系统 clang：

```bash
# 示例编译命令（实际需根据引擎 CMakeLists 调整）
$NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/clang \
  -target aarch64-linux-android26 \
  -fla \      # 控制流平坦化
  -bcf \      # 虚假控制流
  -sobf \     # 子程序混淆
  -split \    # 基本块拆分
  -o librodroid_il2cppdumper.so \
  <引擎源码>
```

具体 pass 参数需根据引擎源码结构调整。

---

## 接入后的变化

### Gradle 配置

需要在 `build.gradle.kts` 中为 native 编译任务指定 OLLVM toolchain：

```kotlin
android {
    defaultConfig {
        // 指定 OLLVM NDK 路径
        ndk {
            abiFilters.add("arm64-v8a")
            abiFilters.add("armeabi-v7a")
        }
    }
    // 自定义 preBuild 步骤，使用 OLLVM clang 重编 .so
}
```

### CI 工作流

GitHub Actions 需要额外步骤：

1. 下载 OLLVM NDK r25c
2. 设置 `NDK_HOME` 指向 OLLVM NDK
3. 重新编译引擎 `.so`
4. 执行常规 Gradle 构建

---

## 参考资料

| 资源 | 链接 |
|------|------|
| OLLVM NDK r25c release | https://github.com/Saint-Theana/ollvm-ndk-android/releases/tag/0.0 |
| Amice（现代 LLVM pass 插件） | https://github.com/fuqiuluo/amice |
| NDK r25 vs r26 差异 | https://github.com/android/ndk |

---

## 接入清单（后续执行）

- [ ] 下载 OLLVM NDK r25c 并解压
- [ ] 修改 `local.properties` 指向 r25c
- [ ] 在 `il2cpp-dumper-rs` 中集成 OLLVM pass 编译参数
- [ ] 本地验证混淆后 so 可正常加载运行
- [ ] 更新 `.github/workflows/build.yml` 支持 OLLVM NDK 下载和编译
- [ ] 更新 `BUILD.md` 文档说明
