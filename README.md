# DumpCS Filter

Android 应用，用于筛选 Il2CppDumper 导出的 dump.cs 文件中的类名。支持 AI 智能搜索、猫娘解释、对话式筛选、自动更新等功能。

## 目录结构

```
├── .github/workflows/build.yml   # GitHub Actions CI/CD
├── build.gradle.kts              # Android 构建配置
├── gradle/                       # Gradle Wrapper
├── gradlew                       # Gradle Wrapper 脚本
├── local.properties              # 本地 SDK 路径配置
├── src/main/                     # Kotlin 源码
│   ├── java/com/dumpcs/filter/   # 应用源码
│   └── jniLibs/                  # 预编译引擎 .so
├── BUILD.md                      # 构建指南
├── DEV_GUIDE.md                  # 开发指南
└── update.json.example           # 自动更新配置示例
```

## 快速开始

### 构建 Debug APK
```bash
./gradlew assembleDebug
```

### 构建 Release APK
```bash
./gradlew assembleRelease
```

### 自动生成签名密钥（首次构建 release 需要）
```bash
keytool -genkeypair -v -keystore release.keystore -keyalg RSA -keysize 2048 -validity 10000 -alias dumpcs
```

## 功能特性

- **AI 智能搜索**：说中文自动找类，支持精准语义过滤
- **猫娘 AI 聊天**：解释每个类 + 对话式筛选
- **自动导出**：筛选结果保存到 `/sdcard/DumpCSFilter/`
- **历史记录**：本地数据库保存筛选历史
- **自动更新**：检查新版本并下载安装
- **脚本支持**：内置 GameGuardian Lua 脚本工具集
- **文件浏览**：树形浏览 dump.cs 解析结果

## CI/CD

GitHub Actions 支持手动触发构建：

1. 进入仓库 Actions 标签页
2. 选择 "Build Android APK"
3. 选择构建类型（debug / release）
4. Release 版本可选填写 tag 自动创建 GitHub Release
