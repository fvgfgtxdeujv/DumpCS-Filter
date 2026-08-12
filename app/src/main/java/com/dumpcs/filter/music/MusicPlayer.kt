package com.dumpcs.filter.music

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * 背景音乐播放器（单例）
 * - 扫描设备全部音频 + 应用内导入目录，构建随机播放列表
 * - 每次进入应用重新洗牌 → 每次听到的顺序都不一样
 * - 播放完自动切下一首（随机顺序）
 * - 切歌时发射 nowPlayingEvent 供 UI 弹顶部卡片（专辑图 + 歌名）
 */
data class MusicTrack(
    val uri: Uri,
    val title: String,
    val artist: String,
    val albumArt: Bitmap?,
    val durationMs: Long,
    val path: String
)

object MusicPlayer {
    private const val TAG = "MusicPlayer"
    private val AUDIO_EXTS = setOf("mp3", "flac", "wav", "m4a", "ogg", "aac", "opus")
    private const val MAX_TRACKS = 500

    private var mediaPlayer: MediaPlayer? = null
    private var shuffledOrder: MutableList<Int> = mutableListOf()
    private var playIndex = 0

    /** 全部曲目（扫描 + 导入） */
    val tracks = mutableListOf<MusicTrack>()

    /** 已加载曲目数量（供 UI 显示） */
    private val _trackCount = MutableStateFlow(0)
    val trackCount: StateFlow<Int> = _trackCount

    /** 当前播放曲目 */
    private val _currentTrack = MutableStateFlow<MusicTrack?>(null)
    val currentTrack: StateFlow<MusicTrack?> = _currentTrack

    /** 是否正在播放 */
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    /** 切歌事件（弹顶部卡片：专辑图 + 歌名，1.5s 自动消失） */
    private val _nowPlayingEvent = MutableSharedFlow<MusicTrack>(extraBufferCapacity = 4)
    val nowPlayingEvent: SharedFlow<MusicTrack> = _nowPlayingEvent

    /** 是否已加载过（避免重复扫描） */
    var loaded = false
        private set

    /** 扫描设备音乐 + 导入目录，构建随机播放列表（IO 线程调用） */
    fun loadTracks(context: Context) {
        if (loaded) return
        tracks.clear()
        val found = mutableListOf<File>()

        // 1) MediaStore 快查（系统媒体库已索引的音频，毫秒级返回）
        queryMediaStore(context, found)

        // 2) 文件系统补充扫描（未索引/自建目录的音频，兜底）
        val root = Environment.getExternalStorageDirectory()
        scanDir(root, found)

        // 3) 应用内导入目录（SAF 导入的歌复制到这里）
        val importDir = File(context.filesDir, "music_import")
        if (importDir.exists()) {
            importDir.listFiles()?.forEach { if (isAudio(it)) found.add(it) }
        }

        // 4) 提取元数据
        found.distinctBy { it.absolutePath }.forEach { f ->
            try {
                val track = extractTrack(Uri.fromFile(f), f.absolutePath)
                if (track != null) tracks.add(track)
            } catch (_: Exception) {
            }
        }
        reshuffle()
        _trackCount.value = tracks.size
        loaded = true
    }

    /** MediaStore 快查：查询媒体库已索引的音频文件路径 */
    private fun queryMediaStore(context: Context, out: MutableList<File>) {
        try {
            val projection = arrayOf(android.provider.MediaStore.Audio.Media.DATA)
            context.contentResolver.query(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection, null, null, null
            )?.use { c ->
                val idx = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATA)
                while (c.moveToNext()) {
                    val path = c.getString(idx) ?: continue
                    val f = File(path)
                    if (f.exists() && isAudio(f)) out.add(f)
                    if (out.size >= MAX_TRACKS) break
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun scanDir(dir: File, out: MutableList<File>) {
        val children = try {
            dir.listFiles() ?: return
        } catch (_: Exception) {
            return
        }
        // 限制深度与总量防止卡死
        for (c in children) {
            if (out.size >= MAX_TRACKS) return
            if (c.isDirectory) {
                val name = c.name
                if (name == "Android" || name == "obb" || name.startsWith(".")) continue
                val depth = c.absolutePath.count { it == '/' } - Environment.getExternalStorageDirectory().absolutePath.count { it == '/' }
                if (depth <= 4) scanDir(c, out)
            } else if (isAudio(c)) {
                out.add(c)
            }
        }
    }

    private fun isAudio(f: File): Boolean {
        val ext = f.extension.lowercase()
        return ext in AUDIO_EXTS && f.length() > 500 // 跳过 <500B 的无效/损坏文件
    }

    private fun extractTrack(uri: Uri, path: String): MusicTrack? {
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(path)
        } catch (_: Exception) {
            return null
        }
        val title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: File(path).nameWithoutExtension
        val artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "未知艺术家"
        val duration = try {
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
        } catch (_: Exception) {
            0L
        }
        var art: Bitmap? = null
        try {
            val pic = mmr.embeddedPicture
            if (pic != null) art = BitmapFactory.decodeByteArray(pic, 0, pic.size)
        } catch (_: Exception) {
        }
        try {
            mmr.release()
        } catch (_: Exception) {
        }
        return MusicTrack(uri, title, artist, art, duration, path)
    }

    /** 重新洗牌播放顺序 */
    private fun reshuffle() {
        shuffledOrder = tracks.indices.toMutableList().shuffled().toMutableList()
        playIndex = 0
    }

    /** 随机开始播放（每次进入应用调一次） */
    fun playRandom(context: Context) {
        if (tracks.isEmpty()) return
        stopInternal()
        reshuffle()
        playIndex = 0
        playCurrent(context)
    }

    /** 播放下一首（随机顺序，播完自动调用） */
    fun playNext(context: Context) {
        if (tracks.isEmpty()) return
        stopInternal()
        playIndex = (playIndex + 1) % tracks.size
        if (playIndex == 0) reshuffle()
        playCurrent(context)
    }

    private fun playCurrent(context: Context) {
        if (tracks.isEmpty()) return
        val idx = shuffledOrder.getOrNull(playIndex) ?: return
        val track = tracks[idx]
        try {
            val mp = MediaPlayer()
            mp.setDataSource(context, track.uri)
            mp.setOnCompletionListener {
                _isPlaying.value = false
                playNext(context)
            }
            mp.setOnErrorListener { _, _, _ ->
                _isPlaying.value = false
                playNext(context)
                true
            }
            mp.prepare()
            mp.start()
            mediaPlayer = mp
            _currentTrack.value = track
            _isPlaying.value = true
            // 发射切歌事件 → UI 顶部弹卡片（专辑图 + 歌名）
            kotlinx.coroutines.runBlocking {
                _nowPlayingEvent.emit(track)
            }
        } catch (_: Exception) {
            // 单个文件播放失败 → 跳下一首
            playNext(context)
        }
    }

    /** 暂停 / 继续 */
    fun toggle() {
        val mp = mediaPlayer ?: return
        try {
            if (mp.isPlaying) {
                mp.pause()
                _isPlaying.value = false
            } else {
                mp.start()
                _isPlaying.value = true
            }
        } catch (_: Exception) {
        }
    }

    /** 停止播放 */
    fun stop() {
        stopInternal()
        _currentTrack.value = null
        _isPlaying.value = false
    }

    private fun stopInternal() {
        try {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) mp.stop()
                mp.release()
            }
        } catch (_: Exception) {
        }
        mediaPlayer = null
    }

    /** 导入歌曲：把 SAF 选中的音频复制到应用私有目录，并加入播放列表 */
    fun importMusic(context: Context, uri: Uri): String? {
        return try {
            val importDir = File(context.filesDir, "music_import")
            if (!importDir.exists()) importDir.mkdirs()
            val name = queryDisplayName(context, uri) ?: "imported_${System.currentTimeMillis()}.mp3"
            val safeName = name.replace(Regex("[^a-zA-Z0-9._\\-\\u4e00-\\u9fa5]"), "_")
            val dest = File(importDir, safeName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            val track = extractTrack(Uri.fromFile(dest), dest.absolutePath)
            if (track != null) {
                tracks.add(track)
                shuffledOrder = tracks.indices.toMutableList().shuffled().toMutableList()
            }
            dest.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) c.getString(idx) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
