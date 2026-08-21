package com.dumpcs.filter.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.dumpcs.filter.ui.UpdateBar
import com.dumpcs.filter.ui.DumpViewModel
import com.dumpcs.filter.ui.ExplorerViewModel
import com.dumpcs.filter.ui.MainViewModel
import com.dumpcs.filter.ui.ScriptViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 主界面三页容器：查找 / Dump / 脚本
 * - HorizontalPager 左右滑动切换，跟手流畅
 * - 底部导航点击与滑动双向联动
 * - 查找页内含查找+浏览两个子 tab
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainTabsScreen(
    navController: NavHostController,
    viewModel: MainViewModel,
    dumpViewModel: DumpViewModel,
    scriptViewModel: ScriptViewModel,
    explorerViewModel: ExplorerViewModel
) {
    val pagerState = rememberPagerState(initialPage = 0) { 3 }
    val scope = rememberCoroutineScope()
    var updateBarDismissed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(30_000)
            if (!updateBarDismissed) {
                viewModel.updateManager.queryDownloadProgress()
            }
        }
    }

    Scaffold(
        topBar = {
            if (!updateBarDismissed) {
                UpdateBar(
                    updateManager = viewModel.updateManager,
                    modifier = Modifier.padding(top = 4.dp),
                    onDismiss = { updateBarDismissed = true }
                )
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = pagerState.currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    icon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text("查找") }
                )
                NavigationBarItem(
                    selected = pagerState.currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    icon = { Icon(Icons.Outlined.Download, contentDescription = null) },
                    label = { Text("Dump") }
                )
                NavigationBarItem(
                    selected = pagerState.currentPage == 2,
                    onClick = { scope.launch { pagerState.animateScrollToPage(2) } },
                    icon = { Icon(Icons.Outlined.Terminal, contentDescription = null) },
                    label = { Text("脚本") }
                )
            }
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) { page ->
            when (page) {
                0 -> SearchBrowseScreen(navController = navController, viewModel = viewModel, explorerViewModel = explorerViewModel)
                1 -> DumpScreen(
                    navController = navController,
                    dumpViewModel = dumpViewModel,
                    mainViewModel = viewModel
                )
                2 -> ScriptScreen(navController = navController, vm = scriptViewModel)
            }
        }
    }
}
