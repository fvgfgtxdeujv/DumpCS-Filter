package com.dumpcs.filter.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import java.io.File
import java.net.URLDecoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewerScreen(navController: NavController, filePath: String) {
    val decodedPath = remember(filePath) {
        URLDecoder.decode(filePath, "UTF-8")
    }
    val file = remember(decodedPath) { File(decodedPath) }
    val rawContent = remember(file) {
        if (file.exists() && file.canRead()) file.readText() else "无法读取文件内容"
    }
    val scrollState = rememberScrollState()
    var isJsonFormat by remember { mutableStateOf(rawContent.trimStart().startsWith("{") || rawContent.trimStart().startsWith("[")) }
    var formattedJson by remember { mutableStateOf<String?>(null) }

    // 简单的 JSON 格式化（不使用外部库）
    LaunchedEffect(rawContent) {
        if (rawContent.trimStart().startsWith("{") || rawContent.trimStart().startsWith("[")) {
            formattedJson = try {
                var result = ""
                var indent = 0
                var inString = false
                var escape = false
                for (ch in rawContent) {
                    when {
                        escape -> { result += ch; escape = false }
                        ch == '\\' && inString -> { result += ch; escape = true }
                        ch == '"' -> { result += ch; inString = !inString }
                        inString -> result += ch
                        ch == '{' || ch == '[' -> {
                            result += ch
                            indent++
                            result += "\n" + "  ".repeat(indent)
                        }
                        ch == '}' || ch == ']' -> {
                            indent--
                            result += "\n" + "  ".repeat(indent)
                            result += ch
                        }
                        ch == ',' -> {
                            result += ch
                            result += "\n" + "  ".repeat(indent)
                        }
                        ch == ':' -> result += ": "
                        ch.isWhitespace() -> if (!result.endsWith('\n')) result += " "
                        else -> result += ch
                    }
                }
                result
            } catch (_: Exception) {
                rawContent
            }
        }
    }

    val displayContent = if (isJsonFormat && formattedJson != null) formattedJson!! else rawContent

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(file.name, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(
                            "${com.dumpcs.filter.ui.formatFileSize(file.length())}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (rawContent.trimStart().startsWith("{") || rawContent.trimStart().startsWith("[")) {
                        FilterChip(
                            selected = isJsonFormat,
                            onClick = { isJsonFormat = !isJsonFormat },
                            label = { Text(if (isJsonFormat) "JSON 格式化" else "原始格式") },
                            leadingIcon = { Icon(if (isJsonFormat) Icons.Default.FormatIndentIncrease else Icons.Default.FormatAlignLeft, null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Surface(
            modifier = Modifier.fillMaxSize().padding(padding),
            color = MaterialTheme.colorScheme.surface
        ) {
            Text(
                text = displayContent,
                modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
