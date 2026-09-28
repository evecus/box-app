package com.box4.manager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AppsScreen(vm: Box4ViewModel) {
    LaunchedEffect(Unit) { vm.initApps() }

    var search by remember { mutableStateOf("") }
    var showAll by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // proxy_mode 切换
        Card(Modifier.padding(horizontal = 16.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("proxy_mode", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("blacklist", "whitelist", "core").forEach { mode ->
                        FilterChip(
                            selected = vm.proxyMode == mode,
                            onClick = { vm.setProxyMode(mode) },
                            label = { Text(mode) },
                            enabled = !vm.busy
                        )
                    }
                }
                Text(
                    when (vm.proxyMode) {
                        "blacklist" -> "黑名单:列表内应用走代理,其余直连"
                        "whitelist" -> "白名单:仅列表内应用直连,其余走代理"
                        else -> "core:由核心配置决定,忽略下方应用列表"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 选择状态 + 搜索
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "已选 ${vm.selected.size} 个" + if (vm.externalCount > 0) "(另有 ${vm.externalCount} 个外部条目将保留)" else "",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            FilterChip(selected = showAll, onClick = { showAll = !showAll }, label = { Text("含系统应用") })
        }
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text("搜索应用") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        val filtered = vm.apps.filter {
            (showAll || !it.isSystem) &&
                (search.isBlank() || it.label.contains(search, true) || it.pkg.contains(search, true))
        }

        LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)) {
            items(filtered, key = { it.key }) { app ->
                val checked = app.key in vm.selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = checked, onCheckedChange = { vm.toggleApp(app.key) })
                    Column(Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${app.pkg} · user ${app.userId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Button(
            onClick = { vm.saveApps() },
            enabled = !vm.busy && vm.proxyMode != "core",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text("保存到 user_packages_list")
        }
    }
}
