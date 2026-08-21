# Il2Cpp 逆向工具项目

## 目录结构

```
├── app/                # DumpCS Filter — Android App（Kotlin/Compose）
├── il2cpp-dumper-rs/   # Rust 引擎源码（MIT）
└── .monkeycode/        # 项目管理配置
```

## 快速开始

### 构建 App APK
```bash
cd app
./gradlew assembleDebug
```

### 引擎源码审查
见 [il2cpp-dumper-rs/README.md](il2cpp-dumper-rs/README.md)
