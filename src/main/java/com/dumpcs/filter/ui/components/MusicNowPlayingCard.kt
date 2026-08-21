package com.dumpcs.filter.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dumpcs.filter.music.MusicPlayer
import com.dumpcs.filter.music.MusicTrack
import kotlinx.coroutines.delay

/**
 * 切歌顶部弹窗：歌曲切换时从屏幕顶部滑入，
 * 显示专辑封面 + 歌名 + 歌手，持续 1.5 秒后自动滑出关闭。
 */
@Composable
fun MusicNowPlayingOverlay(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // 收集切歌事件（StateFlow 无法直接收集 SharedFlow，用 remember 缓存最新一次）
    var showCard by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf<MusicTrack?>(null) }
    val nowPlaying by MusicPlayer.currentTrack.collectAsStateWithLifecycle()

    // 有当前曲目且正在播放 → 弹卡片；切歌事件触发时也弹
    LaunchedEffect(nowPlaying) {
        val t = nowPlaying ?: return@LaunchedEffect
        current = t
        showCard = true
        delay(1500)
        showCard = false
    }

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = showCard && current != null,
            enter = slideInVertically(tween(250)) { -it } + fadeIn(tween(250)),
            exit = slideOutVertically(tween(200)) { -it } + fadeOut(tween(200)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            val track = current
            if (track != null) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xF21A1A1F),
                    shadowElevation = 12.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 专辑封面（无内嵌图则显示音符图标）
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            val art = track.albumArt
                            if (art != null) {
                                Image(
                                    bitmap = art.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(44.dp)
                                )
                            } else {
                                Icon(
                                    Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        // 歌名 + 歌手
                        androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                            Text(
                                track.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                track.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        // 播放指示（三个小音符动画点，简单实现为 "♪"）
                        Text(
                            "♪♪♪",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}