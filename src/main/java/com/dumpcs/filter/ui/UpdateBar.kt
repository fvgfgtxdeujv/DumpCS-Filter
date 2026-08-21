package com.dumpcs.filter.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dumpcs.filter.update.UpdateManager
import com.dumpcs.filter.update.UpdateStatus

/**
 * 更新通知横条，展示在 MainTabsScreen 顶部。
 * 三种状态：有新版本可更新 / 正在下载中 / 无更新。
 */
@Composable
fun UpdateBar(
    updateManager: UpdateManager,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {}
) {
    val status by remember { derivedStateOf { updateManager.status } }
    val info by remember { derivedStateOf { updateManager.updateInfo } }
    val progress by remember { derivedStateOf { updateManager.downloadProgress } }
    var showChangelog by remember { mutableStateOf(false) }

    if (status == UpdateStatus.IDLE || status == UpdateStatus.CHECKING || status == UpdateStatus.UP_TO_DATE) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                status == UpdateStatus.DOWNLOAD_FAILED -> MaterialTheme.colorScheme.errorContainer
                status == UpdateStatus.DOWNLOAD_COMPLETE -> MaterialTheme.colorScheme.primaryContainer
                status == UpdateStatus.INSTALLATION_READY -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.NewReleases,
                        contentDescription = null,
                        tint = if (status == UpdateStatus.DOWNLOAD_FAILED) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = when {
                                status == UpdateStatus.DOWNLOAD_FAILED -> "下载失败"
                                status == UpdateStatus.DOWNLOAD_COMPLETE -> "下载完成"
                                status == UpdateStatus.INSTALLATION_READY -> "安装就绪"
                                else -> "发现新版本"
                            },
                            fontWeight = FontWeight.Bold,
                            color = if (status == UpdateStatus.DOWNLOAD_FAILED) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        info?.let {
                            Text(
                                "v${it.versionName} · ${if (status == UpdateStatus.DOWNLOADING) "下载中..." else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (status == UpdateStatus.DOWNLOAD_FAILED) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                // 关闭按钮
                IconButton(
                    onClick = {
                        if (status == UpdateStatus.DOWNLOAD_FAILED) {
                            updateManager.cancelDownload()
                        }
                        onDismiss()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "关闭",
                        tint = if (status == UpdateStatus.DOWNLOAD_FAILED) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                    )
                }
            }

            //  changelog 折叠
            info?.let { updateInfo ->
                if (updateInfo.changelog.isNotBlank()) {
                    if (showChangelog) {
                        CollapsibleChangelog(changelog = updateInfo.changelog)
                    } else {
                        TextButton(
                            onClick = { showChangelog = true },
                            modifier = Modifier.align(Alignment.Start)
                        ) {
                            Text("查看更新日志", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // 下载进度条
            if (status == UpdateStatus.DOWNLOADING) {
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth(),
                    trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
                Text(
                    "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (status == UpdateStatus.DOWNLOAD_FAILED) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.End)
                )
            }

            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 手动重试
                if (status == UpdateStatus.DOWNLOAD_FAILED) {
                    OutlinedButton(
                        onClick = { updateManager.startDownload() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("重试", style = MaterialTheme.typography.bodySmall)
                    }
                }
                // 立即下载
                if (status == UpdateStatus.UPDATE_AVAILABLE || status == UpdateStatus.DOWNLOAD_COMPLETE) {
                    Button(
                        onClick = {
                            if (status == UpdateStatus.DOWNLOAD_COMPLETE) {
                                updateManager.installApk()
                            } else {
                                updateManager.startDownload()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.NewReleases, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (status == UpdateStatus.DOWNLOAD_COMPLETE) "立即安装" else "立即下载", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (status == UpdateStatus.INSTALLATION_READY) {
                    Button(
                        onClick = { updateManager.installApk() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("安装更新", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun CollapsibleChangelog(changelog: String) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("更新日志", style = MaterialTheme.typography.labelMedium)
            Icon(
                imageVector = if (expanded) Icons.Default.Refresh else Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier
            )
        }
        if (expanded) {
            Surface(
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = changelog,
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
