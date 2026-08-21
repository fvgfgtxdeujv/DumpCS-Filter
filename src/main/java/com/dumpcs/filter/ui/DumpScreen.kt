package com.dumpcs.filter.ui

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.dumpcs.filter.ui.Screen
import com.dumpcs.filter.ui.DumpState
import com.dumpcs.filter.ui.DumpViewModel
import com.dumpcs.filter.ui.InputRequest
import com.dumpcs.filter.ui.MainViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DumpScreen(navController: NavController, dumpViewModel: DumpViewModel, mainViewModel: MainViewModel) {
    val context = LocalContext.current
    val binaryPath by dumpViewModel.binaryPath.collectAsStateWithLifecycle()
    val metadataPath by dumpViewModel.metadataPath.collectAsStateWithLifecycle()
    val binaryFormat by dumpViewModel.binaryFormat.collectAsStateWithLifecycle()
    val metadataFormat by dumpViewModel.metadataFormat.collectAsStateWithLifecycle()
    val logs by dumpViewModel.logs.collectAsStateWithLifecycle()
    val state by dumpViewModel.state.collectAsStateWithLifecycle()
    val outputPath by dumpViewModel.outputPath.collectAsStateWithLifecycle()
    val saveDir by dumpViewModel.saveDir.collectAsStateWithLifecycle()
    val inputRequest by dumpViewModel.inputRequest.collectAsStateWithLifecycle()

    var showSettings by remember { mutableStateOf(true) }
    var showDirDialog by remember { mutableStateOf(false) }
    var savedToast by remember { mutableStateOf<String?>(null) }

    val binaryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val p = copyToCache(context, it, "libil2cpp")
            if (p != null) dumpViewModel.setBinaryPath(p) else savedToast = "文件读取失败"
        }
    }
    val metadataPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val p = copyToCache(context, it, "global-metadata")
            if (p != null) dumpViewModel.setMetadataPath(p) else savedToast = "文件读取失败"
        }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text("Il2Cpp Dump", fontWeight = FontWeight.Bold)
                        Text(
                            "libil2cpp.so + global-metadata.dat → dump.cs",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { dumpViewModel.clearLogs() }) {
                        Icon(Icons.Default.Delete, contentDescription = "清空日志")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                var showBinaryManual by remember { mutableStateOf(false) }
                var showMetadataManual by remember { mutableStateOf(false) }
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("① 选择文件", fontWeight = FontWeight.Bold)
                        FilePickRow("libil2cpp.so", binaryPath, binaryFormat, { binaryPicker.launch("*/*") }) { showBinaryManual = true }
                        FilePickRow("global-metadata.dat", metadataPath, metadataFormat, { metadataPicker.launch("*/*") }) { showMetadataManual = true }
                    }
                }
                if (showBinaryManual) {
                    ManualPathDialog(
                        title = "手动输入 libil2cpp.so 路径",
                        hint = "例如 /sdcard/Download/libil2cpp.so",
                        onConfirm = { dumpViewModel.setBinaryPath(it) },
                        onDismiss = { showBinaryManual = false }
                    )
                }
                if (showMetadataManual) {
                    ManualPathDialog(
                        title = "手动输入 global-metadata.dat 路径",
                        hint = "例如 /sdcard/Download/global-metadata.dat",
                        onConfirm = { dumpViewModel.setMetadataPath(it) },
                        onDismiss = { showMetadataManual = false }
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier.clickable { showDirDialog = true }
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("② 储存设置（保存位置）", fontWeight = FontWeight.Bold)
                            Icon(Icons.Default.Edit, contentDescription = "更改保存位置", tint = MaterialTheme.colorScheme.primary)
                        }
                        Text(
                            saveDir.ifBlank { "未设置（点击选择）" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "点击选择保存目录",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            item {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { showSettings = !showSettings },
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("③ Dump 设置（完全版）", fontWeight = FontWeight.Bold)
                            Icon(
                                if (showSettings) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null
                            )
                        }
                        if (showSettings) {
                            SectionTitle("输出内容")
                            SettingsSwitch("dumpMethod", "方法", dumpViewModel)
                            SettingsSwitch("dumpField", "字段", dumpViewModel)
                            SettingsSwitch("dumpProperty", "属性", dumpViewModel)
                            SettingsSwitch("dumpAttribute", "特性", dumpViewModel)
                            SettingsSwitch("dumpMethodOffset", "方法偏移 (RVA)", dumpViewModel)
                            SettingsSwitch("dumpFieldOffset", "字段偏移", dumpViewModel)
                            SettingsSwitch("dumpTypeDefIndex", "TypeDefIndex", dumpViewModel)
                            SettingsSwitch("dumpAssemblyName", "程序集名", dumpViewModel)

                            SectionTitle("生成选项")
                            SettingsSwitch("generateStruct", "生成结构体 (Struct)", dumpViewModel)
                            SettingsSwitch("generateDummyDll", "生成 DummyDll", dumpViewModel)
                            SettingsSwitch("dummyDllAddToken", "DummyDll 添加 Token", dumpViewModel)
                            SettingsSwitch("generateCppScaffold", "生成 C++ 脚手架", dumpViewModel)
                            SettingsSwitch("generateUnityHeaders", "生成 Unity 头文件", dumpViewModel)
                            SettingsSwitch("mangleNames", "混淆名称 (Mangle)", dumpViewModel)
                            SettingsSwitch("enhancedIdaMetadata", "增强 IDA 元数据", dumpViewModel)
                            SettingsSwitch("useTopologicalSort", "拓扑排序", dumpViewModel)
                            SettingsSwitch("codm", "COD:M 模式", dumpViewModel)

                            SectionTitle("版本 / 指针")
                            SettingsSwitch("forceIl2cppVersion", "强制 Il2Cpp 版本", dumpViewModel)
                            if (dumpViewModel.config.getBool("forceIl2cppVersion")) {
                                NumberInput(
                                    label = "版本号",
                                    placeholder = "如 27.2",
                                    value = dumpViewModel.config.getDouble("forceVersion"),
                                    onValue = { dumpViewModel.config.setDouble("forceVersion", it) }
                                )
                            }
                            SettingsSwitch("forceDump", "强制 Dump", dumpViewModel)
                            SettingsSwitch("noRedirectedPointer", "不使用重定向指针", dumpViewModel)
                            SettingsSwitch("splitDumpPerType", "按类型拆分输出", dumpViewModel)

                            SectionTitle("泛型 Dump")
                            SettingsSwitch("generateGenericsDump", "生成泛型 Dump", dumpViewModel)
                            SettingsSwitch("dumpGenericsRgctx", "泛型 Rgctx", dumpViewModel)
                            SettingsSwitch("dumpGenericsMethodSpecs", "泛型方法特化", dumpViewModel)
                            SettingsSwitch("dumpGenericsCustomAttributes", "泛型特性", dumpViewModel)
                            SettingsSwitch("dumpGenericsStringLiterals", "泛型字符串", dumpViewModel)
                            SettingsSwitch("dumpGenericsMetadataUsages", "泛型元数据引用", dumpViewModel)
                            SettingsSwitch("dumpGenericsVtables", "泛型虚表", dumpViewModel)
                            SettingsSwitch("dumpGenericsInterfaces", "泛型接口", dumpViewModel)

                            SectionTitle("反汇编")
                            SettingsSwitch("dumpDisassembly", "反汇编 (Disassembly)", dumpViewModel)
                            if (dumpViewModel.config.getBool("dumpDisassembly")) {
                                NumberInput(
                                    label = "反汇编目标",
                                    placeholder = "0=方法 1=字段",
                                    value = dumpViewModel.config.getInt("dumpDisassemblyTarget").toDouble(),
                                    onValue = { dumpViewModel.config.setInt("dumpDisassemblyTarget", it.toInt()) }
                                )
                                SettingsSwitch("dumpDisassemblyHexBytes", "十六进制字节", dumpViewModel)
                                SettingsSwitch("dumpDisassemblyFieldNames", "字段名", dumpViewModel)
                                SettingsSwitch("dumpDisassemblyAnnotations", "注解", dumpViewModel)
                                SettingsSwitch("dumpDisassemblyCfg", "CFG 控制流图", dumpViewModel)
                                NumberInput(
                                    label = "最大指令数",
                                    placeholder = "如 300",
                                    value = dumpViewModel.config.getInt("maxDisassemblyInstructions").toDouble(),
                                    onValue = { dumpViewModel.config.setInt("maxDisassemblyInstructions", it.toInt()) }
                                )
                            }

                            SectionTitle("其他")
                            OutlinedTextField(
                                value = dumpViewModel.config.getString("compilerLayout"),
                                onValueChange = { dumpViewModel.config.setString("compilerLayout", it) },
                                label = { Text("CompilerLayout（编译器布局）") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = { dumpViewModel.startDump() },
                    enabled = state != DumpState.DUMPING,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    if (state == DumpState.DUMPING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("正在 Dump…（日志实时刷新）")
                    } else {
                        Text("开始 Dump", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (state == DumpState.DONE && outputPath != null) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("✔ Dump 完成！", fontWeight = FontWeight.Bold)
                            Text(
                                outputPath ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        val f = File(outputPath ?: "")
                                        if (f.exists()) savedToast = "已保存到：${f.absolutePath}"
                                    }
                                ) { Text("保存") }
                                Button(
                                    onClick = {
                                        val p = outputPath ?: return@Button
                                        mainViewModel.loadFileByPath(p)
                                        navController.navigate(Screen.Results.route)
                                    }
                                ) { Text("直接查找", fontWeight = FontWeight.Bold) }
                            }
                            Text(
                                "点「直接查找」可把刚生成的 dump.cs 直接载入查找页搜索",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            if (state == DumpState.ERROR) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(
                            "✘ Dump 失败，请查看下方日志",
                            Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            item {
                Card {
                    Column(Modifier.padding(12.dp)) {
                        Text("日志（最新 ${logs.takeLast(80).size} 条）", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 140.dp, max = 320.dp)
                                .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
                                .verticalScroll(rememberScrollState())
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            if (logs.isEmpty()) {
                                Text(
                                    "等待开始…\n选择 libil2cpp.so 和 global-metadata.dat 后点「开始 Dump」",
                                    color = Color(0xFF9E9E9E),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            logs.takeLast(80).forEach { log ->
                                Text(
                                    log,
                                    color = Color(0xFF4CAF50),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDirDialog) {
        var customPath by remember { mutableStateOf(saveDir) }
        AlertDialog(
            onDismissRequest = { showDirDialog = false },
            title = { Text("选择保存目录") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "DumpCSFilter/dump" to File(Environment.getExternalStorageDirectory(), "DumpCSFilter/dump").absolutePath,
                        "Download" to File(Environment.getExternalStorageDirectory(), "Download").absolutePath,
                        "私有目录" to File(context.getExternalFilesDir(null), "dump").absolutePath
                    ).forEach { (label, path) ->
                        OutlinedButton(
                            onClick = {
                                dumpViewModel.setSaveDir(path)
                                showDirDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(label) }
                    }
                    OutlinedTextField(
                        value = customPath,
                        onValueChange = { customPath = it },
                        label = { Text("自定义路径") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (customPath.isNotBlank()) dumpViewModel.setSaveDir(customPath.trim())
                    showDirDialog = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDirDialog = false }) { Text("取消") }
            }
        )
    }

    if (inputRequest != null) {
        var inputText by remember { mutableStateOf("") }
        // ManualAddresses 第二步：展示已输入的 CodeReg 供确认
        val manualStep = (inputRequest as? InputRequest.ManualAddresses)?.step
        val pendingCodeReg = dumpViewModel.pendingCodeReg
        AlertDialog(
            onDismissRequest = { dumpViewModel.cancelInput() },
            title = {
                val ir = inputRequest
                when (ir) {
                    is InputRequest.DumpAddress -> Text("引擎请求输入：Dump 基址")
                    is InputRequest.ManualAddresses ->
                        Text(if (ir.step == InputRequest.ManualAddresses.Step.CODE_REG) "输入 CodeRegistration" else "输入 MetadataRegistration")
                    is InputRequest.Architecture -> Text("引擎请求输入：架构选择")
                    else -> Text("引擎请求输入")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(inputRequest?.let {
                        when (it) {
                            is InputRequest.DumpAddress -> it.prompt
                            is InputRequest.ManualAddresses -> it.prompt
                            is InputRequest.Architecture -> it.prompt
                        }
                    } ?: "", style = MaterialTheme.typography.bodySmall)
                    if (manualStep == InputRequest.ManualAddresses.Step.META_REG && pendingCodeReg != null) {
                        Text(
                            "CodeRegistration: $pendingCodeReg（已填，请输入 MetadataRegistration）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            val default = (inputRequest as? InputRequest.DumpAddress)?.default
                                ?: (inputRequest as? InputRequest.Architecture)?.default
                            Text(default ?: "输入内容…")
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { dumpViewModel.submitInput(inputText) }) { Text("提交") }
            },
            dismissButton = {
                TextButton(onClick = { dumpViewModel.cancelInput() }) { Text("跳过") }
            }
        )
    }

    // Toast
    savedToast?.let { msg ->
        LaunchedEffect(msg) {
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
            savedToast = null
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 6.dp)
    )
}

@Composable
private fun FilePickRow(label: String, path: String?, format: String?, onClick: () -> Unit, onManualInput: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier
                .weight(1f)
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.Medium)
                Text(
                    path?.let { f ->
                        val name = File(f).name
                        val fmt = format?.let { "  [$it]" } ?: ""
                        "$name$fmt"
                    } ?: "点击选择文件",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (path != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = "选择")
        }
        IconButton(onClick = onManualInput) {
            Icon(Icons.Default.Edit, contentDescription = "手动输入路径", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ManualPathDialog(title: String, hint: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var input by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(hint, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (input.isNotBlank()) onConfirm(input.trim())
                onDismiss()
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun SettingsSwitch(key: String, label: String, vm: DumpViewModel) {
    val value = vm.config.getBool(key)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { vm.config.setBool(key, !value) },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = value,
            onCheckedChange = { vm.config.setBool(key, it) }
        )
    }
}

@Composable
private fun NumberInput(label: String, placeholder: String, value: Double, onValue: (Double) -> Unit) {
    var text by remember { mutableStateOf(if (value == 0.0) "" else value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            it.toDoubleOrNull()?.let(onValue)
        },
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

/** 把 SAF 选中的文件复制到缓存目录（大缓冲加速），返回真实路径 */
private fun copyToCache(context: Context, uri: Uri, prefix: String): String? {
    return try {
        // file:// 直接拿路径，跳过复制
        if (uri.scheme == "file") {
            val p = uri.path
            if (!p.isNullOrBlank() && File(p).exists()) return p
        }
        var name = ""
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) name = cursor.getString(idx) ?: ""
        }
        if (name.isBlank()) name = uri.lastPathSegment ?: "$prefix.bin"
        val cacheDir = File(context.cacheDir, "dump_input").apply { mkdirs() }
        val target = File(cacheDir, name)
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output ->
                val buf = ByteArray(256 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n)
                }
            }
        }
        target.absolutePath
    } catch (e: Exception) {
        null
    }
}