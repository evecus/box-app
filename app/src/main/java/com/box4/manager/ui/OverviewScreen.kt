package com.box4.manager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun OverviewScreen(vm: Box4ViewModel) {
    val s = vm.state
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!s.rootOk) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(
                    s.msg.ifBlank { "未获得 root 授权" },
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else if (!s.moduleInstalled) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(
                    "未检测到 box4 模块(/data/adb/modules/box4 不存在),请先刷入 box4_v5.1.zip",
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // 主开关:语义与 root 管理器中的模块开关一致
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("代理模块", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (s.moduleEnabled) "已启用(等同模块开关:开)" else "已停用(等同模块开关:关)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = s.moduleEnabled,
                        onCheckedChange = { vm.setMaster(it) },
                        enabled = !vm.busy && s.moduleInstalled
                    )
                }
                HorizontalDivider()
                StatusRow("当前内核", s.binName)
                StatusRow(
                    "运行状态",
                    if (s.coreRunning) "运行中 (PID ${s.corePid ?: "?"})" else "未运行",
                    ok = s.coreRunning
                )
                StatusRow(
                    "透明代理",
                    if (s.tproxyActive) "已接管" else "未接管",
                    ok = s.tproxyActive
                )
                StatusRow(
                    "开机自启",
                    if (s.autoStartSuppressed) "已抑制(manual 文件)" else "正常",
                    ok = !s.autoStartSuppressed
                )
            }
        }

        // 运行时控制(不改动模块开关状态)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.start() }, enabled = !vm.busy && s.rootOk, modifier = Modifier.weight(1f)) {
                Text("启动")
            }
            OutlinedButton(onClick = { vm.stop() }, enabled = !vm.busy && s.rootOk, modifier = Modifier.weight(1f)) {
                Text("停止")
            }
            OutlinedButton(onClick = { vm.restart() }, enabled = !vm.busy && s.rootOk, modifier = Modifier.weight(1f)) {
                Text("重启")
            }
        }

        if (vm.busy) {
            Text(if (vm.busyText.isBlank()) "执行中..." else vm.busyText, style = MaterialTheme.typography.bodySmall)
        } else if (vm.busyText.isNotBlank()) {
            Text(vm.busyText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String, ok: Boolean? = null) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = when (ok) {
                true -> MaterialTheme.colorScheme.primary
                false -> MaterialTheme.colorScheme.error
                null -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}
