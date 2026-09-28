package com.box4.manager.root

import android.util.Base64

/**
 * 通过 root shell 完成对 /data/adb 下文件的读写。
 * App 进程本身无权限,文本写入用 base64 编码传输,天然规避引号/换行转义问题。
 */
object RootFs {

    suspend fun exists(path: String): Boolean = sh("test -e '$path'").rc == 0

    suspend fun listDir(path: String): List<String> =
        sh("ls -1A '$path' 2>/dev/null").out.lines().filter { it.isNotBlank() }

    suspend fun readText(path: String): String? {
        val r = sh("cat '$path' 2>/dev/null")
        return if (r.rc == 0) r.out else null
    }

    suspend fun writeText(path: String, content: String): Boolean {
        val b64 = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val dir = path.substringBeforeLast('/')
        return sh("mkdir -p '$dir' && echo '$b64' | base64 -d > '$path'").rc == 0
    }

    suspend fun touch(path: String): Boolean = sh("touch '$path'").rc == 0

    suspend fun delete(path: String): Boolean = sh("rm -f '$path'").rc == 0
}
