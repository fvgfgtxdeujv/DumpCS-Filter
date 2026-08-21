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

[Project Knowledge Summary]
- Date: 2026-08-21
- Context: 项目目录结构调整完成
- Category: Operations & Deployment
- Instructions:
  - 引擎仓 il2cpp-dumper-rs 已删除，仅保留 app 项目
  - app/ 目录内容已全部移至项目根目录（src/、build.gradle.kts、gradlew 等）
  - workflow 文件位于 .github/workflows/build.yml
  - 如需重新引入引擎，需从 https://github.com/fvgfgtxdeujv/il2cpp-dumper-rs 获取源码

[Project Knowledge Summary]
- Date: 2026-08-21
- Context: GitHub Actions CI/CD 配置
- Category: Operations & Deployment
- Instructions:
  - workflow 位于 .github/workflows/build.yml
  - 手动触发（workflow_dispatch）时可选择 debug 或 release
  - debug 构建无需 tag，自动上传 artifact
  - release 构建可选择填写 tag，填写后自动创建 GitHub Release 并上传 APK
  - 构建命令：./gradlew assembleDebug 或 ./gradlew assembleRelease
  - Gradle Wrapper 已随仓分发（gradlew + gradle/wrapper/），无需额外安装 Gradle
  - 注意：推送 workflow 文件需要 token 具有 workflow scope 权限

[Project Knowledge Summary]
- Date: 2026-08-21
- Context: Android 构建环境配置
- Category: Build Methods
- Instructions:
  - NDK 安装位置：/tmp/opencode/android-ndk-r26c
  - Gradle Wrapper 已随仓分发，使用 ./gradlew 执行构建
  - local.properties 需配置 sdk.dir 和 ndk.dir
  - SDK 组件：platforms;android-34, build-tools;34.0.0, platform-tools, ndk;26.1.10909125

[Project Knowledge Summary]
- Date: 2026-08-21
- Context: data/ 与 ui/ 子包结构扁平化重构
- Category: Workflow & Collaboration
- Instructions:
  - data/ 目录下的 ai/dump/engine/explorer/local/model/parser/script/storage 已合并到 data/ 根目录，package 统一为 com.dumpcs.filter.data
  - ui/ 目录下的 components/navigation/screens/theme/viewmodel 已合并到 ui/ 根目录，package 统一为 com.dumpcs.filter.ui
  - service/ 目录为空，已删除
  - 文件移动后需同步更新所有 import 语句和文档路径引用
  - 批量更新 sed 命令参考：data 子包 import 替换为 com.dumpcs.filter.data.，ui 子包同理

[Project Knowledge Summary]
- Date: 2026-08-21
- Context: GitHub 推送凭证问题
- Category: Troubleshooting & Debugging
- Instructions:
  - git credential.helper 配置为 /app/agent/bin/agent git-credential-helper，返回 500 错误
  - SSH 方式不可用（ssh 命令不存在）
  - 推送时需检查凭证助手状态，如失败可手动在 GitHub 设置 Personal Access Token 或改用其他方式推送

[Project Knowledge Summary]
- Date: 2026-08-21
- Context: OLLVM 接入调研
- Category: Operations & Deployment
- Instructions:
  - 已创建接入规划文档：docs/OLLVM_TODO.md
  - 推荐方案：降级 NDK 至 r25c（Saint-Theana/ollvm-ndk-android），该版本所有 OLLVM pass 均正常
  - r26d 中 -flat 和 -bcf pass 失效，不推荐
  - 建议先采用策略 A（仅混淆引擎 so），验证效果后再考虑全量混淆
  - 现代替代方案 Amice（fuqiuluo/amice）支持 LLVM 11-22，可通过 -fpass-plugin 与 NDK r26 配合使用
  - 接入前需准备：下载 OLLVM NDK r25c、修改 local.properties、集成编译参数、更新 CI workflow
