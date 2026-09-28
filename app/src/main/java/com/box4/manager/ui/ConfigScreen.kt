package com.box4.manager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.box4.manager.core.Box4Repository
import com.box4.manager.core.BoxConfigParser
import com.box4.manager.root.RootFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun ConfigScreen(vm: Box4ViewModel, nav: NavController) {
    var loaded by remember { mutableStateOf(false) }
    var cfgText by remember { mutableStateOf("") }
    var tproxyPort by remember { mutableStateOf("") }
    var redirPort by remember { mutableStateOf("") }
    var proxyMethod by remember { mutableStateOf("TPROXY") }
    var proxyMode by remember { mutableStateOf("blacklist") }
    var ipv6 by remember { mutableStateOf("disable") }

    LaunchedEffect(Unit) {
        val text = Box4Repository.readConfig()
        val cfg = BoxConfigParser.parse(text)
        cfgText = text
        tproxyPort = BoxConfigParser.scalar(cfg, "tproxy_port")
        redirPort = BoxConfigParser.scalar(cfg, "redir_port")
        proxyMethod = BoxConfigParser.scalar(cfg, "proxy_method").ifBlank { "TPROXY" }
        proxyMode = BoxConfigParser.scalar(cfg, "proxy_mode").ifBlank { "blacklist" }
        ipv6 = BoxConfigParser.scalar(cfg, "ipv6").ifBlank { "disable" }
        loaded = true
    }

    fun saveStructured() {
        var t = cfgText
        t = BoxConfigParser.editScalar(t, "tproxy_port", tproxyPort.trim())
        t = BoxConfigParser.editScalar(t, "redir_port", redirPort.trim())
        t = BoxConfigParser.editScalar(t, "proxy_method", proxyMethod)
        t = BoxConfigParser.editScalar(t, "proxy_mode", proxyMode)
        t = BoxConfigParser.editScalar(t, "ipv6", ipv6)
        cfgText = t
        vm.toast("已写入 box.config,重启内核生效")
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!loaded) {
            Text("读取中...", style = MaterialTheme.typography.bodySmall)
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("box.config 关键项", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = tproxyPort,
                        onValueChange = { tproxyPort = it.filter(Char::isDigit) },
                        label = { Text("tproxy_port") },
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = redirPort,
                        onValueChange = { redirPort = it.filter(Char::isDigit) },
                        label = { Text("redir_port") },
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                ChipRow("proxy_method", listOf("TPROXY", "REDIRECT", "MIXED"), proxyMethod) { proxyMethod = it }
                ChipRow("proxy_mode", listOf("blacklist", "whitelist", "core"), proxyMode) { proxyMode = it }
                ChipRow("ipv6", listOf("enable", "disable"), ipv6) { ipv6 = it }
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.material3.Button(onClick = { saveStructured() }) { Text("保存") }
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("文本编辑", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = {
                    vm.editorPath = Box4Repository.CONFIG_PATH
                    nav.navigate("editor")
                }) { Text("编辑 box.config 原文") }
                HorizontalDivider()
                Text("当前内核 (${vm.state.binName}) 配置文件:", style = MaterialTheme.typography.bodyMedium)
                var files by remember { mutableStateOf<List<String>>(emptyList()) }
                LaunchedEffect(vm.state.binName) {
                    if (vm.state.binName.isNotEmpty()) {
                        files = Box4Repository.coreConfigFiles(vm.state.binName)
                    }
                }
                if (files.isEmpty()) {
                    Text(
                        "未找到配置文件(config.json / config.yaml)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                files.forEach { path ->
                    TextButton(onClick = {
                        vm.editorPath = path
                        nav.navigate("editor")
                    }) { Text(path.substringAfterLast("/data/adb/box/")) }
                }
            }
        }
    }
}

@Composable
private fun ChipRow(label: String, options: List<String>, current: String, onSelect: (String) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { opt ->
                FilterChip(
                    selected = current == opt,
                    onClick = { onSelect(opt) },
                    label = { Text(opt) }
                )
            }
        }
    }
}

/**
 * 通用文本编辑器:加载 root 路径文件,保存前做校验。
 * .json → JSONObject/JSONArray 严格校验;.yaml/.yml → 禁止 Tab 缩进的弱校验。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: Box4ViewModel, nav: NavController) {
    val path = vm.editorPath
    var text by remember { mutableStateOf("加载中...") }
    var hint by remember { mutableStateOf("") }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(path) {
        if (path.isEmpty()) {
            text = ""
            hint = "未指定文件"
        } else {
            val loaded = RootFs.readText(path) ?: "(读取失败或文件不存在)"
            text = loaded
            hint = "已加载: $path"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(path.substringAfterLast('/').ifBlank { "编辑器" }) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            val reloaded = RootFs.readText(path)
                            withContext(Dispatchers.Main) {
                                if (reloaded != null) {
                                    text = reloaded
                                    hint = "已重新加载"
                                } else {
                                    hint = "重新加载失败"
                                }
                            }
                        }
                    }) { Text("重载") }
                    TextButton(onClick = {
                        // 校验
                        val err = validate(path, text)
                        if (err != null) {
                            vm.toast("校验失败: $err(可检查后重试)")
                            return@TextButton
                        }
                        scope.launch(Dispatchers.IO) {
                            val ok = RootFs.writeText(path, text)
                            withContext(Dispatchers.Main) {
                                hint = if (ok) "已保存,重启内核生效" else "保存失败"
                                vm.toast(hint)
                            }
                        }
                    }) { Text("保存") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (hint.isNotBlank()) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            )
        }
    }
}

/** 返回错误信息,null 表示通过 */
private fun validate(path: String, content: String): String? {
    val name = path.substringAfterLast('/').lowercase()
    return when {
        name.endsWith(".json") -> try {
            if (content.trimStart().startsWith("[")) JSONArray(content) else JSONObject(content)
            null
        } catch (e: Exception) {
            e.message ?: "JSON 语法错误"
        }
        name.endsWith(".yaml") || name.endsWith(".yml") ->
            content.lines().filter { it.startsWith("\t") }.isNotEmpty().let { hasTab ->
                if (hasTab) "YAML 不允许 Tab 缩进" else null
            }
        else -> null
    }
}
