package com.box4.manager

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.box4.manager.ui.AppsScreen
import com.box4.manager.ui.Box4ViewModel
import com.box4.manager.ui.ConfigScreen
import com.box4.manager.ui.CoreScreen
import com.box4.manager.ui.EditorScreen
import com.box4.manager.ui.LogScreen
import com.box4.manager.ui.OverviewScreen
import com.box4.manager.ui.theme.Box4Theme

class MainActivity : ComponentActivity() {

    private val vm: Box4ViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Box4Theme {
                Box4App(vm)
            }
        }
    }
}

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

@Composable
fun Box4App(vm: Box4ViewModel) {
    val navController = rememberNavController()
    val tabs = listOf(
        TabItem("overview", "概览", Icons.Filled.Home),
        TabItem("core", "内核", Icons.Filled.Build),
        TabItem("config", "配置", Icons.Filled.Edit),
        TabItem("apps", "应用", Icons.Filled.List),
        TabItem("logs", "日志", Icons.Filled.Refresh)
    )

    // 全局 toast
    val ctx = LocalContext.current
    LaunchedEffect(vm.toast) {
        vm.toast?.let {
            Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show()
            vm.toast = null
        }
    }

    LaunchedEffect(Unit) {
        vm.refresh()
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            // 编辑器页全屏,隐藏底部导航
            if (currentRoute != "editor") {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(navController, startDestination = "overview", modifier = Modifier.padding(padding)) {
            composable("overview") { OverviewScreen(vm) }
            composable("core") { CoreScreen(vm) }
            composable("config") { ConfigScreen(vm, navController) }
            composable("apps") { AppsScreen(vm) }
            composable("logs") { LogScreen(vm) }
            composable("editor") { EditorScreen(vm, navController) }
        }
    }
}
