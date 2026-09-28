package com.box4.manager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.box4.manager.core.Box4Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LogScreen(vm: Box4ViewModel) {
    val tabs = listOf("run.log", "run_error.log")
    var tab by remember { mutableIntStateOf(0) }
    var content by remember { mutableStateOf("加载中...") }
    var version by remember { mutableIntStateOf(0) } // 刷新触发器
    val scope = rememberCoroutineScope()

    LaunchedEffect(tab, version) {
        content = withContext(Dispatchers.IO) {
            Box4Repository.readLog(tabs[tab])
        }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TabRow(selectedTabIndex = tab) {
            tabs.forEachIndexed { i, name ->
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    text = { Text(name) }
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = { version++ }) { Text("刷新") }
            OutlinedButton(onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) { Box4Repository.clearLog(tabs[tab]) }
                    version++
                }
            }) { Text("清空") }
            Text(
                "(最多显示最近 500 行)",
                Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            content,
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
        )
    }
}
