package com.dumpcs.filter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dumpcs.filter.ui.navigation.Screen
import com.dumpcs.filter.ui.screens.AiChatScreen
import com.dumpcs.filter.ui.screens.DumpScreen
import com.dumpcs.filter.ui.screens.FileViewerScreen
import com.dumpcs.filter.ui.screens.HistoryScreen
import com.dumpcs.filter.ui.screens.HomeScreen
import com.dumpcs.filter.ui.screens.MainTabsScreen
import com.dumpcs.filter.ui.screens.ResultsScreen
import com.dumpcs.filter.ui.screens.ScriptScreen
import com.dumpcs.filter.ui.screens.SettingsScreen
import androidx.compose.foundation.layout.Box
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.dumpcs.filter.music.MusicPlayer
import com.dumpcs.filter.ui.components.MusicNowPlayingOverlay
import com.dumpcs.filter.ui.screens.SplashScreen
import com.dumpcs.filter.ui.theme.DumpCSFilterTheme
import com.dumpcs.filter.ui.viewmodel.DumpViewModel
import com.dumpcs.filter.ui.viewmodel.ExplorerViewModel
import com.dumpcs.filter.ui.viewmodel.MainViewModel
import com.dumpcs.filter.ui.viewmodel.ScriptViewModel
class MainActivity : ComponentActivity() {
    // Activity 级共享 ViewModel：所有页面共用同一份文件数据/筛选结果/历史记忆
    private val viewModel: MainViewModel by viewModels()
    private val dumpViewModel: DumpViewModel by viewModels()
    private val scriptViewModel: ScriptViewModel by viewModels()
    private val explorerViewModel: ExplorerViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            Toast.makeText(this, "需要存储权限才能导出文件", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestPermissions()

        val appPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)

        // 背景音乐：冷启动按用户设置自动随机播放
        startBackgroundMusic()
        // 启动时异步检查更新（6小时冷却期后才会发起网络请求）
        lifecycleScope.launch { viewModel.updateManager.checkUpdate(forceCheck = false) }

        setContent {
            DumpCSFilterTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        // 深空流星启动动画：仅首次安装启动播放一次，之后直接进主界面
                        var showSplash by rememberSaveable { androidx.compose.runtime.mutableStateOf(appPrefs.getBoolean("splash_first_run", true)) }
                        if (showSplash) {
                            SplashScreen(onFinished = {
                                appPrefs.edit().putBoolean("splash_first_run", false).apply()
                                showSplash = false
                            })
                        } else {
                        val navController = rememberNavController()
                        NavHost(
                            navController = navController,
                            startDestination = Screen.MainTabs.route,
                            modifier = Modifier.fillMaxSize(),
                            enterTransition = {
                                slideIntoContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Left,
                                    animationSpec = tween(350)
                                )
                            },
                            exitTransition = {
                                slideOutOfContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Left,
                                    animationSpec = tween(350)
                                )
                            },
                            popEnterTransition = {
                                slideIntoContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Right,
                                    animationSpec = tween(350)
                                )
                            },
                            popExitTransition = {
                                slideOutOfContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Right,
                                    animationSpec = tween(350)
                                )
                            }
                        ) {
                            composable(Screen.MainTabs.route) {
                                MainTabsScreen(
                                    navController = navController,
                                    viewModel = viewModel,
                                    dumpViewModel = dumpViewModel,
                                    scriptViewModel = scriptViewModel,
                                    explorerViewModel = explorerViewModel
                                )
                            }
                            composable(Screen.Results.route) {
                                ResultsScreen(navController = navController, viewModel = viewModel)
                            }
                            composable(Screen.History.route) {
                                HistoryScreen(navController = navController, viewModel = viewModel)
                            }
                            composable(Screen.FileViewer.route) { backStackEntry ->
                                val filePath = backStackEntry.arguments?.getString("filePath") ?: ""
                                FileViewerScreen(navController = navController, filePath = filePath)
                            }
                            composable(Screen.AiChat.route) {
                                AiChatScreen(navController = navController, viewModel = viewModel)
                            }
                            composable(Screen.Settings.route) {
                                SettingsScreen(navController = navController, viewModel = viewModel)
                            }
                        }
                    }
                    MusicNowPlayingOverlay()
                }
            }
        }

    /** 背景音乐：按用户设置扫描并随机播放 */
    private fun startBackgroundMusic() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("music_enabled", true)) return
        lifecycleScope.launch(Dispatchers.IO) {
            if (!MusicPlayer.loaded) {
                MusicPlayer.loadTracks(this@MainActivity)
                if (MusicPlayer.tracks.isNotEmpty()) MusicPlayer.playRandom(this@MainActivity)
            }
        }
    }

    private fun checkAndRequestPermissions() {
        // Android 11+ 需要"所有文件访问"特殊权限才能读写公共存储
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                requestManageStoragePermission()
            }
        }
        // 普通存储权限（Android 10 及以下）
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun requestManageStoragePermission() {
        // 跳转到"所有文件访问"设置页引导用户开启
        val intent = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:$packageName")
        )
        try {
            startActivity(intent)
        } catch (e: Exception) {
            // 部分设备不支持直接跳应用详情，跳总开关页
            startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
        Toast.makeText(this, "请在设置中开启「允许访问所有文件」", Toast.LENGTH_LONG).show()
    }

    override fun onResume() {
        super.onResume()
        // 从设置页返回后检查授权状态
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            Toast.makeText(this, "已获得所有文件访问权限", Toast.LENGTH_SHORT).show()
        }
    }
}
