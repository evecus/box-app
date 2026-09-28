package com.box4.manager.ui

import android.app.Application
import android.content.pm.PackageInfoFlags
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.box4.manager.core.Box4Repository
import com.box4.manager.core.Box4State
import com.box4.manager.core.CoreInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppEntry(
    val label: String,
    val pkg: String,
    val userId: Int,
    val isSystem: Boolean
) {
    val key: String get() = "$userId:$pkg"
}

class Box4ViewModel(app: Application) : AndroidViewModel(app) {

    var state by mutableStateOf(Box4State())
        private set
    var busy by mutableStateOf(false)
        private set
    var busyText by mutableStateOf("")
    var toast by mutableStateOf<String?>(null)

    var cores by mutableStateOf<List<CoreInfo>>(emptyList())
        private set

    var apps by mutableStateOf<List<AppEntry>>(emptyList())
        private set
    var selected by mutableStateOf<Set<String>>(emptySet())
    var externalCount by mutableStateOf(0)
        private set
    var proxyMode by mutableStateOf("blacklist")
        private set

    /** 当前在编辑器页打开的文件路径 */
    var editorPath by mutableStateOf("")

    private fun io(block: suspend () -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        busy = true
        try {
            block()
        } catch (e: Exception) {
            toast = "操作异常: ${e.message}"
        } finally {
            busy = false
        }
    }

    fun refresh() = io {
        state = Box4Repository.state()
    }

    fun toast(text: String) {
        toast = text
    }

    // ---- 概览 ----

    fun setMaster(on: Boolean) = io {
        busyText = if (on) "正在启动内核..." else "正在停止内核..."
        val out = Box4Repository.setModuleEnabled(on)
        busyText = out
        state = Box4Repository.state()
        toast = if (on) "已启动" else "已停止,模块已停用"
    }

    fun start() = io {
        busyText = "正在启动..."
        busyText = Box4Repository.startCore()
        state = Box4Repository.state()
    }

    fun stop() = io {
        busyText = "正在停止..."
        busyText = Box4Repository.stopCore()
        state = Box4Repository.state()
    }

    fun restart() = io {
        busyText = "正在重启..."
        busyText = Box4Repository.restartCore()
        state = Box4Repository.state()
        toast = "已重启"
    }

    // ---- 内核 ----

    fun loadCores() = io {
        if (state.binName.isEmpty()) state = Box4Repository.state()
        cores = Box4Repository.coreInfos(state.binName)
    }

    fun switchCore(name: String) = io {
        if (!Box4Repository.switchCore(name)) {
            toast = "写入 box.config 失败"
            return@io
        }
        state = Box4Repository.state()
        loadCores()
    }

    fun setAutoStart(enabled: Boolean) = io {
        Box4Repository.setAutoStart(enabled)
        state = Box4Repository.state()
        toast = if (enabled) "开机自启已开启" else "开机自启已抑制(manual)"
    }

    // ---- 分应用 ----

    fun initApps() = viewModelScope.launch(Dispatchers.IO) {
        val existing = Box4Repository.userPackages()
        val mode = BoxConfigSnapshot.mode()
        withContext(Dispatchers.Main) {
            proxyMode = mode
        }
        loadAppList()
        val installedKeys = apps.map { it.key }.toSet()
        withContext(Dispatchers.Main) {
            selected = existing.filter { it in installedKeys }.toSet()
            externalCount = existing.count { it !in installedKeys }
        }
    }

    private suspend fun loadAppList() {
        val pm = getApplication<Application>().packageManager
        val pkgs = if (Build.VERSION.SDK_INT >= 33) {
            pm.getInstalledPackages(PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(0)
        }
        val list = pkgs.mapNotNull { pi ->
            try {
                val ai = pm.getApplicationInfo(pi.packageName, 0)
                AppEntry(
                    label = pm.getApplicationLabel(ai).toString(),
                    pkg = pi.packageName,
                    userId = ai.uid / 100000,
                    isSystem = (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                )
            } catch (e: Exception) {
                null
            }
        }.sortedWith(compareBy({ it.isSystem }, { it.label.lowercase() }))
        withContext(Dispatchers.Main) { apps = list }
    }

    fun setProxyMode(mode: String) = io {
        if (Box4Repository.setProxyMode(mode)) {
            proxyMode = mode
            toast = "proxy_mode 已设为 $mode,重启内核生效"
        } else {
            toast = "写入失败"
        }
    }

    fun toggleApp(key: String) {
        selected = if (key in selected) selected - key else selected + key
    }

    fun saveApps() = io {
        val existing = Box4Repository.userPackages()
        val installedKeys = apps.map { it.key }.toSet()
        // 保留配置中存在但本机看不到的条目(其他 user / 工作资料)
        val merged = selected + existing.filter { it !in installedKeys }
        if (Box4Repository.saveUserPackages(merged.sorted())) {
            externalCount = merged.count { it !in installedKeys }
            toast = "已保存 ${selected.size} 个应用,重启内核生效"
        } else {
            toast = "写入失败"
        }
    }
}

/** 读取 proxy_mode 的轻量快照(独立于 state 刷新) */
private object BoxConfigSnapshot {
    suspend fun mode(): String =
        com.box4.manager.core.BoxConfigParser.scalar(
            com.box4.manager.core.BoxConfigParser.parse(
                com.box4.manager.core.Box4Repository.readConfig()
            ),
            "proxy_mode"
        ).ifBlank { "blacklist" }
}
