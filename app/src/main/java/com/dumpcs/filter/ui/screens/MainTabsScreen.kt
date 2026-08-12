package com.dumpcs.filter.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.dumpcs.filter.ui.viewmodel.DumpViewModel
import com.dumpcs.filter.ui.viewmodel.MainViewModel
import com.dumpcs.filter.ui.viewmodel.ScriptViewModel
import kotlinx.coroutines.launch

/**
 * 主界面三页容器：查找 / Dump / 脚本
 * - HorizontalPager 左右滑动切换，跟手流畅
 * - 底部导航点击与滑动双向联动
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainTabsScreen(
    navController: NavHostController,
    viewModel: MainViewModel,
    dumpViewModel: DumpViewModel,
    scriptViewModel: ScriptViewModel
) {
    val pagerState = rememberPagerState(initialPage = 0) { 3 }
    val scope = rememberCoroutineScope()

    Scaffold(
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
                0 -> HomeScreen(navController = navController, viewModel = viewModel)
                1 -> DumpScreen(
                    navController = navController,
                    dumpViewModel = dumpViewModel,
                    mainViewModel = viewModel
                )
                else -> ScriptScreen(navController = navController, vm = scriptViewModel)
            }
        }
    }
}