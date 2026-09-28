package com.box4.manager.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.atomic.AtomicLong

data class ShellResult(val rc: Int, val out: String) {
    val ok: Boolean get() = rc == 0
}

/**
 * 常驻 su 会话:进程启动一次,后续命令复用,避免每次 su -c 冷启动与反复授权。
 * 每条命令用子 shell 包裹并以标记行回传退出码;超时或断流后销毁重建。
 */
object Sh {
    private const val MARK = "__BOX4_RC_"
    private const val PATH_EXPORT =
        "/data/adb/magisk:/data/adb/ksu/bin:/data/adb/ap/bin:\$PATH:/system/bin:/system/xbin:/vendor/bin"

    private val mutex = Mutex()
    private val seq = AtomicLong()
    private var proc: Process? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null

    private fun ensure(): Boolean {
        proc?.let { if (it.isAlive) return true }
        close()
        return try {
            val p = ProcessBuilder("su").start()
            val w = BufferedWriter(OutputStreamWriter(p.outputStream))
            w.write("export PATH=$PATH_EXPORT\n")
            // stderr 并入 stdout,统一按行读取
            w.write("exec 2>&1\n")
            w.flush()
            proc = p
            writer = w
            reader = BufferedReader(InputStreamReader(p.inputStream))
            true
        } catch (e: Exception) {
            close()
            false
        }
    }

    fun close() {
        try {
            proc?.destroy()
        } catch (_: Exception) {
        }
        proc = null
        writer = null
        reader = null
    }

    suspend fun exec(cmd: String, timeoutMs: Long = 15000): ShellResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!ensure()) return@withLock ShellResult(-1, "!无法启动 su 会话(无 root 或未授权)")

            val id = seq.incrementAndGet()
            val mark = "$MARK${id}__"
            try {
                writer!!.write("( $cmd ) ; echo $mark\$?\n")
                writer!!.flush()
            } catch (e: Exception) {
                close()
                return@withLock ShellResult(-1, "!会话写入失败: ${e.message}")
            }

            val sb = StringBuilder()
            var rc = -1
            try {
                withTimeout(timeoutMs) {
                    while (true) {
                        val line = reader!!.readLine() ?: throw IOException("su 会话已结束")
                        if (line.startsWith(mark)) {
                            rc = line.substring(mark.length).trim().toIntOrNull() ?: -1
                            break
                        }
                        sb.append(line).append('\n')
                    }
                }
                ShellResult(rc, sb.toString().trimEnd('\n'))
            } catch (e: Exception) {
                close()
                ShellResult(-1, sb.toString().trimEnd('\n') + "\n!执行超时或会话中断,已重建")
            }
        }
    }
}

suspend fun sh(cmd: String, timeoutMs: Long = 15000): ShellResult = Sh.exec(cmd, timeoutMs)

suspend fun shOk(cmd: String, timeoutMs: Long = 15000): Boolean = Sh.exec(cmd, timeoutMs).rc == 0
