package com.box4.manager.core

import com.box4.manager.root.RootFs
import com.box4.manager.root.sh

data class Box4State(
    val rootOk: Boolean = false,
    val moduleInstalled: Boolean = false,
    /** 对应 root 管理器里的模块开关:无 disable 文件 = 开 */
    val moduleEnabled: Boolean = false,
    val binName: String = "",
    val coreRunning: Boolean = false,
    val corePid: Long? = null,
    val tproxyActive: Boolean = false,
    /** /data/adb/box/manual 存在 = 开机不拉起内核 */
    val autoStartSuppressed: Boolean = false,
    val msg: String = ""
)

data class CoreInfo(
    val name: String,
    val binExists: Boolean,
    val version: String,
    val active: Boolean
)

object Box4Repository {
    const val BOX_PATH = "/data/adb/box"
    const val SCRIPTS = "$BOX_PATH/scripts"
    const val RUN_PATH = "$BOX_PATH/run"
    const val CONFIG_PATH = "$SCRIPTS/box.config"

    val CORES = listOf("sing-box", "clash", "mihomo", "xray", "v2ray", "hysteria")

    @Volatile
    private var moduleDir: String? = null

    private suspend fun resolveModuleDir(): String {
        moduleDir?.let { return it }
        val lite = sh("[ -d /data/adb/lite_modules/box4 ] && echo lite || echo normal").out.trim() == "lite"
        val dir = if (lite) "/data/adb/lite_modules/box4" else "/data/adb/modules/box4"
        moduleDir = dir
        return dir
    }

    suspend fun checkRoot(): Boolean {
        val r = sh("id -u")
        return r.rc == 0 && r.out.trim() == "0"
    }

    suspend fun state(): Box4State {
        if (!checkRoot()) return Box4State(msg = "未获得 root 授权,请在 root 管理器中允许本应用")

        val mDir = resolveModuleDir()
        val installed = RootFs.exists(mDir)
        val cfg = BoxConfigParser.parse(RootFs.readText(CONFIG_PATH) ?: "")
        val binName = BoxConfigParser.scalar(cfg, "bin_name").ifBlank { "mihomo" }

        var pid: Long? = null
        var running = false
        val pidText = RootFs.readText("$RUN_PATH/$binName.pid")?.trim()
        if (!pidText.isNullOrEmpty() && pidText.all { it.isDigit() }) {
            val p = pidText.toLong()
            pid = p
            running = sh("kill -0 $p 2>/dev/null").rc == 0
        }

        // TPROXY 走 mangle 表,REDIRECT 走 nat 表,链名均为 BOX_LOCAL
        val tproxy = sh(
            "iptables -t mangle -nL BOX_LOCAL >/dev/null 2>&1 || iptables -t nat -nL BOX_LOCAL >/dev/null 2>&1"
        ).rc == 0

        val moduleEnabled = installed && !RootFs.exists("$mDir/disable")
        val autoStartSuppressed = RootFs.exists("$BOX_PATH/manual")

        return Box4State(
            rootOk = true,
            moduleInstalled = installed,
            moduleEnabled = moduleEnabled,
            binName = binName,
            coreRunning = running,
            corePid = pid,
            tproxyActive = tproxy,
            autoStartSuppressed = autoStartSuppressed
        )
    }

    /**
     * 主开关:镜像 root 管理器里模块开关的语义。
     * 开 = 删除 disable 文件 + 立即拉起内核与透明代理;
     * 关 = 停止内核 + 清理透明代理规则 + 写入 disable 文件(重启后也不会自启)。
     */
    suspend fun setModuleEnabled(on: Boolean): String {
        val mDir = resolveModuleDir()
        return if (on) {
            sh("rm -f '$mDir/disable'")
            startCore()
        } else {
            val out = stopCore()
            sh("touch '$mDir/disable'")
            out
        }
    }

    suspend fun startCore(): String = runService("start") + runTproxy("enable")

    suspend fun stopCore(): String = runService("stop") + runTproxy("disable")

    suspend fun restartCore(): String = runService("restart", 60000) + runTproxy("enable")

    private suspend fun runService(action: String, timeout: Long = 40000): String {
        val r = sh("$SCRIPTS/box.service $action", timeout)
        return r.out.ifBlank { if (r.ok) "" else "box.service $action 失败(rc=${r.rc})" }
    }

    private suspend fun runTproxy(action: String, timeout: Long = 40000): String {
        val r = sh("$SCRIPTS/box.tproxy $action", timeout)
        return r.out.ifBlank { if (r.ok) "" else "box.tproxy $action 失败(rc=${r.rc})" }
    }

    suspend fun readConfig(): String = RootFs.readText(CONFIG_PATH) ?: ""

    suspend fun saveConfig(text: String): Boolean = RootFs.writeText(CONFIG_PATH, text)

    suspend fun switchCore(name: String): Boolean =
        saveConfig(BoxConfigParser.editScalar(readConfig(), "bin_name", name))

    suspend fun setProxyMode(mode: String): Boolean =
        saveConfig(BoxConfigParser.editScalar(readConfig(), "proxy_mode", mode))

    suspend fun userPackages(): List<String> =
        BoxConfigParser.arrayItemsMultiline(readConfig(), "user_packages_list")

    suspend fun saveUserPackages(entries: List<String>): Boolean =
        saveConfig(BoxConfigParser.editArray(readConfig(), "user_packages_list", entries))

    suspend fun coreInfos(activeName: String): List<CoreInfo> = CORES.map { name ->
        val bin = "$BOX_PATH/bin/$name"
        val exists = RootFs.exists(bin)
        var ver = ""
        if (exists) {
            // clash/mihomo 用 -v,其余核心用 version 子命令
            val arg = if (name == "clash" || name == "mihomo") "-v" else "version"
            sh("chmod 0755 '$bin' 2>/dev/null; '$bin' $arg 2>&1 | head -n 1", timeoutMs = 10000)
                .out.trim().let { if (it.isNotBlank()) ver = it }
        }
        CoreInfo(name, exists, ver, name == activeName)
    }

    /** 自启开关:true = 允许开机自启(删 manual),false = 抑制自启(建 manual) */
    suspend fun setAutoStart(enabled: Boolean): Boolean =
        if (enabled) RootFs.delete("$BOX_PATH/manual") else RootFs.touch("$BOX_PATH/manual")

    suspend fun readLog(name: String, lines: Int = 500): String =
        sh("tail -n $lines '$RUN_PATH/$name' 2>/dev/null").out.ifBlank { "(空)" }

    suspend fun clearLog(name: String): Boolean = sh(": > '$RUN_PATH/$name' 2>/dev/null").rc == 0

    /** 当前内核目录下的配置文件(如 config.json / config.yaml) */
    suspend fun coreConfigFiles(binName: String): List<String> =
        RootFs.listDir("$BOX_PATH/$binName")
            .filter { it.startsWith("config") }
            .map { "$BOX_PATH/$binName/$it" }
}
