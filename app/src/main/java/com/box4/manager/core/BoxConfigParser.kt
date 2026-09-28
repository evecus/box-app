package com.box4.manager.core

/**
 * box.config 解析与编辑。
 * 原则:只做"按 key 定位替换",不重排、不重写整个文件,保留用户注释与空白。
 */
object BoxConfigParser {

    private val lineRe = Regex("""^(\s*)([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$""")

    /** 逐行解析 key=value(数组取单行括号内容,多行数组用 [arrayItemsMultiline]) */
    fun parse(text: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        text.lines().forEach { ln ->
            val m = lineRe.find(ln) ?: return@forEach
            map[m.groupValues[2]] = m.groupValues[3].trim()
        }
        return map
    }

    fun scalar(cfg: Map<String, String>, key: String): String =
        cfg[key]?.trim()?.removeSurrounding("\"") ?: ""

    /** 支持多行数组:从 key= 行起,向后收集直到出现右括号 */
    fun arrayItemsMultiline(text: String, key: String): List<String> {
        val lines = text.lines()
        val startIdx = lines.indexOfFirst { Regex("""^\s*$key\s*=""").containsMatchIn(it) }
        if (startIdx == -1) return emptyList()
        var acc = lines[startIdx].substringAfter('=').trim()
        var i = startIdx
        while (!acc.contains(')') && i + 1 < lines.size) {
            i++
            acc += " " + lines[i].trim()
        }
        val inner = acc.removePrefix("(").substringBeforeLast(")")
        return inner.split(Regex("""\s+"""))
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotBlank() }
    }

    /** 替换标量值:定位该行整体替换,保留前导缩进;key 不存在则追加到末尾 */
    fun editScalar(text: String, key: String, value: String): String {
        val re = Regex("""(?m)^(\s*)$key\s*=.*$""")
        return if (re.containsMatchIn(text)) {
            re.replace(text) { m -> "${m.groupValues[1]}$key=\"$value\"" }
        } else {
            text.trimEnd('\n') + "\n$key=\"$value\"\n"
        }
    }

    /**
     * 替换数组:从 key= 行起覆盖到闭合右括号行。
     * 空数组写成 key=(),非空写成多行带引号形式(与原版注释风格一致)。
     */
    fun editArray(text: String, key: String, values: List<String>): String {
        val lines = text.lines()
        val startIdx = lines.indexOfFirst { Regex("""^\s*$key\s*=""").containsMatchIn(it) }
        if (startIdx == -1) {
            val entry = "$key=(" + values.joinToString(" ") { "\"$it\"" } + ")"
            return lines.joinToString("\n").trimEnd('\n') + "\n$entry\n"
        }
        var endIdx = startIdx
        if (!lines[startIdx].contains(')')) {
            var i = startIdx + 1
            while (i < lines.size) {
                endIdx = i
                if (lines[i].contains(')')) break
                i++
            }
        }
        val indent = lines[startIdx].takeWhile { it == ' ' || it == '\t' }
        val entryLines: List<String> = if (values.isEmpty()) {
            listOf("$indent$key=()")
        } else {
            listOf("$indent$key=(") +
                values.map { "$indent    \"$it\"" } +
                listOf("$indent)")
        }
        val newLines = lines.subList(0, startIdx) + entryLines + lines.subList(endIdx + 1, lines.size)
        return newLines.joinToString("\n")
    }
}
