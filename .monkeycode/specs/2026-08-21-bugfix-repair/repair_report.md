# 修复报告 2026-08-21

## 问题汇总

### 1. Dump 页解析失败（对比未开源 APK）
**现象**：用户反映未开源的 APK 能自动检测并解析成功，我们的版本需要手动输入两个路径且解析失败。
**根因**：我们要求用户手动选择 `libil2cpp.so` 和 `global-metadata.dat` 两个文件，而未开源 APK 在用户选择 `.so` 后会**自动在同目录查找 `global-metadata.dat`**，无需二次选择。另外，通过 SAF 选中的文件会先复制到 cache 目录，可能导致部分设备权限丢失。
**修复**：选择 `libil2cpp.so` 后自动扫描同目录找 `global-metadata.dat`，找到则自动填入。

---

### 2. 设置面板收起状态不持久化
**现象**：用户收起 Dump 设置面板后，下次打开页面仍然自动展开。
**根因**：`DumpScreen.kt:53` 中 `var showSettings by remember { mutableStateOf(true) }` 每次重建都默认为 `true`，没有读写 SharedPreferences。
**修复**：在 `DumpViewModel` 中新增 `settingsCollapsed` 偏好保存；`DumpScreen` 初始化时读取该值，默认折叠。

---

### 3. 每次 onResume 弹出"已获得所有文件访问权限"
**现象**：每次切回应用都会弹 Toast，打扰用户。
**根因**：`MainActivity.kt:228` 在 `onResume` 中无条件弹出 Toast，只要当前是 Android 11+ 且有权限就弹。
**修复**：用 SharedPreferences 记录"已提示过"标志位，只在首次授予权限时提示，之后不再重复弹出。

---

### 4. Explorer 页面加载 dump.cs 时没有进度反馈
**现象**：大文件解析时 UI 卡住，用户以为应用崩溃。
**根因**：`ExplorerViewModel.loadDump` 一次性读完整个文件再返回，期间 UI 无进度指示。
**修复**：
- 在 `ExplorerViewModel` 中添加 `loadProgress` StateFlow（0.0 → 1.0）。
- 在 `DumpTreeParser.parse` 中加进度回调支持（每解析 N 行报告一次）。
- `ExplorerScreen` 在加载中时显示进度条和百分比，同时允许用户在等待时做其他操作（如搜索）。

---

### 5. Dump 浏览逐层加载（改进第4点的具体描述）
**现象**：用户希望第一层（程序集列表）立刻显示，其余层级后台加载。
**根因**：当前实现是同步解析完成后才渲染，无法做到渐进式展示。
**修复**：改为流式解析——先快速解析出程序集/命名空间/类型骨架，UI 立即渲染第一层；类型成员（方法/字段/属性/事件）在后台按需展开时加载。具体方案：
- 第一版：在解析完成后，UI 端实现 `expanded` 状态懒加载（已支持），配合进度条消除"卡死"感即可。
- 进阶：将 `DumpTreeParser.parse` 拆成两步——第一步只抽 Assembly+Namespace+Type 骨架，第二步再补全 Members。
  
**本次实现**：优先解决进度条问题，同时优化 `DumpTreeParser` 支持进度回调，让 UI 有实时反馈。

---

### 6. 注册地址需手动输入，无法自动获得偏移量（2026-08-21 追加）
**现象**：未开源 APK 可以自动获得偏移量（CodeRegistration / MetadataRegistration），我们的版本需要手动输入。
**根因**：native 库 `librodroid_il2cppdumper.so` 的自动检测逻辑会依次尝试 `Section scan → Symbol table → ARM32 search pattern → 64/32 bit`，全部失败后才要求手动输入。对于某些游戏的 il2cpp.so，自动检测成功率不高，需要用户手动提供地址。未开源 APK 可能使用了更准确的检测算法，或者预先配置了注册地址。
**修复**：
- 在 `DumpViewModel` 中新增 `resetInputQueue()` 方法：dump 开始时，自动从 SharedPreferences 读取上次保存的 `code_reg` 和 `meta_reg`，预填入 inputQueue，引擎无需弹窗即可获取。
- 用户每次手动输入 CodeRegistration / MetadataRegistration 后，自动保存到 `dump_prefs` 的 `code_reg` / `meta_reg` 键，下次 dump 同一游戏时直接注入，不再弹窗。
- `DumpScreen` 显示"注册地址已缓存"提示，并提供「清除」按钮，方便换游戏时重置。

---

## 修改文件清单

| 文件 | 修改内容 |
|------|---------|
| `DumpViewModel.kt` | 新增 `autoResolveMetadata`、`settingsCollapsed` 持久化、`hasCachedReg`、`resetInputQueue`、`clearRegistrationCache` |
| `DumpScreen.kt` | 默认折叠设置面板；选择 so 后自动查找 metadata；显示注册地址缓存状态 |
| `MainActivity.kt` | 移除 onResume 无条件 toast，改为条件提示 |
| `DumpTreeParser.kt` | 添加进度回调参数，流式报告解析进度 |
| `ExplorerViewModel.kt` | 添加 `loadProgress` StateFlow |
| `ExplorerScreen.kt` | 加载时显示百分比进度条 |
