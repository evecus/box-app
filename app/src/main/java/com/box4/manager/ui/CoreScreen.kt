package com.box4.manager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.box4.manager.core.Box4Repository

@Composable
fun CoreScreen(vm: Box4ViewModel) {
    LaunchedEffect(Unit) { vm.loadCores() }

    var pendingRestart by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<CoreInfo?>(null) }

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("内核列表", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "写入 box.config 的 bin_name;不限内核名,二进制放 bin/<名称>,配置放 <名称>/",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalButton(onClick = { showAddDialog = true }, enabled = !vm.busy) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  添加")
                }
            }
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
                    IconButton(
                        onClick = { deleteTarget = core },
                        enabled = !core.active && !vm.busy
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "删除 ${core.name}",
                            tint = if (core.active) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
        item {
            Text(
                "切换后需重启内核生效。自定义内核:把二进制放到 /data/adb/box/bin/<名称> 并 chmod +x," +
                    "配置文件放 /data/adb/box/<名称>/config.*,配置页会自动跟随当前内核切换目录。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // 新增内核对话框
    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        val valid = Box4Repository.validCoreName(name.trim())
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("添加内核") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("内核名称") },
                        singleLine = true,
                        supportingText = {
                            Text(
                                if (name.isBlank()) "如 sing-box、mihomo-dev、xray-rpr"
                                else if (valid) "将加入列表: ${name.trim()}" else "名称不合法"
                            )
                        },
                        isError = name.isNotBlank() && !valid
                    )
                    Text(
                        "仅添加名称,不会创建文件;请自行上传二进制到 bin/<名称>。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAddDialog = false
                        vm.addCore(name)
                    },
                    enabled = valid
                ) { Text("添加") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("取消") }
            }
        )
    }

    // 删除确认
    deleteTarget?.let { core ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除内核 ${core.name}") },
            text = {
                Text(
                    if (core.binExists) "可选择仅移出列表,或连同 /data/adb/box/bin/${core.name} " +
                        "与 /data/adb/box/${core.name}/ 配置目录一起删除(不可恢复)。"
                    else "该内核没有对应二进制,仅从列表移除即可。"
                )
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        deleteTarget = null
                        vm.removeCore(core.name, deleteFiles = false)
                    }) { Text("仅移出列表") }
                    if (core.binExists) {
                        TextButton(onClick = {
                            deleteTarget = null
                            vm.removeCore(core.name, deleteFiles = true)
                        }) { Text("移出并删文件") }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }

    // 切换重启提示
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
