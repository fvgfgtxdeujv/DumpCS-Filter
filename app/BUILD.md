# il2cpp-dumper-rs 构建指南（BUILD.md）

> 双仓结构：
> - **引擎仓**：`il2cpp-dumper-rs/` — Rust 引擎源码，预编译 .so，MIT/GPL 混合
> - **App 仓**：`app/` — Kotlin Android App（DumpCS Filter），依赖预编译 .so

---

## 一、引擎仓（il2cpp-dumper-rs）

> 引擎源码仅供审查，二进制分发方式不公开。

### 依赖说明

| 依赖 | 许可证 | 用途 |
|---|---|---|
| jni | MIT OR Apache-2.0 | JNI 绑定 |
| dotnetdll | GPL-3.0+ | DummyDll 输出 |
| serde, byteorder 等 | MIT/Apache-2.0 | 通用工具 |

---

## 二、App 仓构建（app/）

见 [app/BUILD.md](app/BUILD.md)。无需 NDK / Rust，纯 Kotlin Gradle 构建。

---

## 三、引擎说明

底层引擎为闭源 Rust 库，以预编译 `.so` 形式随 APK 分发。App 仓不包含任何 Rust 编译相关配置。
