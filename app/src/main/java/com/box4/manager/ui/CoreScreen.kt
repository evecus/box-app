package com.box4.manager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
fun CoreScreen(vm: Box4ViewModel) {
    LaunchedEffect(Unit) { vm.loadCores() }

    var pendingRestart by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("开机自启", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (!vm.state.autoStartSuppressed) "开机后自动拉起内核"
                            else "已抑制(/data/adb/box/manual 存在)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = !vm.state.autoStartSuppressed,
                        onCheckedChange = { vm.setAutoStart(it) },
                        enabled = !vm.busy && vm.state.rootOk
                    )
                }
            }
        }
        item {
            Text("选择内核(写入 box.config 的 bin_name)", style = MaterialTheme.typography.titleMedium)
        }
        items(vm.cores, key = { it.name }) { core ->
            Card {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = core.active,
                        onClick = {
                            if (!core.active) {
                                vm.switchCore(core.name)
                                pendingRestart = core.name
                            }
                        },
                        enabled = !vm.busy
                    )
                    Column(Modifier.weight(1f)) {
                        Text(core.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            when {
                                !core.binExists -> "二进制不存在:/data/adb/box/bin/${core.name}"
                                else -> core.version.ifBlank { "版本获取失败" }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        item {
            Text(
                "切换后需重启内核生效;二进制请自行放入 /data/adb/box/bin/ 并确保可执行。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    pendingRestart?.let { name ->
        AlertDialog(
            onDismissRequest = { pendingRestart = null },
            title = { Text("内核已切换为 $name") },
            text = { Text("立即重启内核使切换生效?") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestart = null
                    vm.restart()
                }) { Text("立即重启") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestart = null }) { Text("稍后手动重启") }
            }
        )
    }
}
