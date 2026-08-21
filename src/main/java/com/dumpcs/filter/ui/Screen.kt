package com.dumpcs.filter.ui

sealed class Screen(val route: String) {
    data object MainTabs : Screen("main_tabs")
    data object Home : Screen("home")
    data object Dump : Screen("dump")
    data object Script : Screen("script")
    data object Results : Screen("results")
    data object History : Screen("history")
    data object FileViewer : Screen("fileViewer/{filePath}") {
        fun createRoute(filePath: String) = "fileViewer/$filePath"
    }
    data object AiChat : Screen("ai_chat")
    data object Settings : Screen("settings")
}
