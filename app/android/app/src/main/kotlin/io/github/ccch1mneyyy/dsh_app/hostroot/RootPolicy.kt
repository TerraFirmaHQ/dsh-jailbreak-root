package io.github.ccch1mneyyy.dsh_app.hostroot

/**
 * How a host-root command is allowed to run.
 *
 * [Level.READ] means the text looks like a bounded read. It is not isolation.
 * Arbitrary shell still needs a person's approval ([RootTools]). Only the
 * structured read_file path runs a read without that dialog, including
 * /data/adb. Writes, including /data/adb, run after an explicit preview.
 * There is no path ban and no flag that skips approval.
 */
object RootPolicy {
    enum class Level { READ, APPROVAL, PROTECTED }

    data class Verdict(val level: Level, val reason: String) {
        val needsApproval: Boolean get() = level != Level.READ
    }

    const val MAX_COMMAND = 16_384

    private val READ_FIRST = setOf(
        "id", "ls", "cat", "head", "tail", "df", "du", "ps", "uname", "whoami", "pwd", "stat", "file",
        "getprop", "dumpsys", "pm", "cmd", "settings", "wc", "grep", "egrep", "fgrep", "find", "date",
        "uptime", "free", "mount", "lsof", "netstat", "ip", "ifconfig", "getenforce", "printenv",
        "which", "readlink", "realpath", "md5sum", "sha1sum", "sha256sum", "logcat", "top", "echo",
        "true", "test", "basename", "dirname", "nproc", "lsmod", "service", "appops", "content",
        "am", "wm", "ss",
    )

    private val FILTERS = setOf(
        "grep", "egrep", "fgrep", "head", "tail", "wc", "sort", "uniq", "cut", "tr", "cat", "column", "nl",
    )

    private val READ_SUB = mapOf(
        "pm" to setOf("list", "path", "dump", "resolve-activity", "query-activities", "get-max-users", "list-users", "get-install-location", "has-feature"),
        "settings" to setOf("get", "list"),
        "cmd" to setOf("package", "battery", "deviceidle", "stats", "netpolicy", "connectivity", "wifi", "overlay", "notification", "appops", "activity", "window", "display", "thermalservice", "power", "user", "role"),
        "appops" to setOf("get", "query-op"),
        "content" to setOf("query"),
        "am" to setOf("stack", "get-current-user", "get-config", "get-inactive", "get-standby-bucket"),
        "wm" to setOf("size", "density"),
        "ip" to setOf("addr", "address", "a", "route", "r", "link", "l", "neigh", "rule", "-4", "-6", "-s", "-br"),
        "mount" to setOf<String>(),
        "service" to setOf("list", "check"),
    )

    private val CMD_READ = setOf("list", "get", "query", "dump", "status", "help", "get-standby-bucket", "list-packages", "resolve-activity", "get-mode", "list-users")
    private val WRITE_WORDS = setOf(
        "add", "del", "delete", "flush", "set", "put", "replace", "change", "append", "reset", "clear",
        "install", "uninstall", "enable", "disable", "grant", "revoke", "kill", "force-stop",
    )
    private val DUMPSYS_WRITES = setOf(
        "set", "reset", "unplug", "enable", "disable", "force-idle", "unforce", "step",
        "whitelist", "tempwhitelist", "add-to-power-save", "remove-from-power-save",
    )
    /** Flags that name an output file. A bare read must not carry these. */
    private val OUTPUT_FLAG = Regex("""^(?:-o|--output|--output=.*|--file|--file=.*|-fprint|-fls|of=.*)$""")

    private val PROTECTED_PATH = Regex("""(?i)(/data/adb\b|zygisk|hma_oss|hma_plus|hmalist|tricky_store|shamiko|oplus_secure_guard)""")
    private val PROTECTED_OP = Regex(
        """(?i)(^|[\s;&|(])(setenforce|magiskpolicy|supolicy|resetprop|insmod|rmmod|modprobe)\b""" +
            """|remount""" +
            """|(^|[\s;&|(])(ksud|magisk|apd)\s+(module\s+(install|uninstall|enable|disable)|boot-patch|--install)\b""",
    )

    fun classify(command: String): Verdict {
        val cmd = command.trim()
        if (cmd.isEmpty()) return Verdict(Level.APPROVAL, "空命令")
        if (cmd.length > MAX_COMMAND) return Verdict(Level.APPROVAL, "命令过长")
        if (isReadOnly(cmd)) return Verdict(Level.READ, "只读")
        if (PROTECTED_OP.containsMatchIn(cmd) || PROTECTED_PATH.containsMatchIn(cmd)) {
            return Verdict(Level.PROTECTED, protectedReason(cmd))
        }
        return Verdict(Level.APPROVAL, "会改动系统")
    }

    /** Text shown before a non-read runs. Cancel leaves the device unchanged. */
    fun preview(command: String, verdict: Verdict): String {
        val head = when (verdict.level) {
            Level.READ -> return ""
            Level.PROTECTED ->
                "这会改动隐藏、模块或 root 运行环境（例如 KSU、Zygisk、HMA、SELinux、挂载或 resetprop）。" +
                    "取消后不会执行，启动时也不会自动做这些事。\n原因：${verdict.reason}"
            Level.APPROVAL ->
                "这条命令会在安卓主机上以 uid 0 执行，不是沙箱。取消后不会执行。\n原因：${verdict.reason}"
        }
        return head + "\n\n" + redact(command)
    }

    fun redact(command: String): String {
        val r = command.replace(Regex("""(?i)(password|passwd|token|secret|key)(\s*[=:]\s*)\S+"""), "$1$2***")
        return if (r.length > 300) r.take(300) + "…" else r
    }

    private fun protectedReason(cmd: String): String = when {
        Regex("""(?i)setenforce|magiskpolicy|supolicy""").containsMatchIn(cmd) -> "改 SELinux"
        Regex("""(?i)remount""").containsMatchIn(cmd) -> "remount"
        Regex("""(?i)resetprop""").containsMatchIn(cmd) -> "resetprop"
        Regex("""(?i)insmod|rmmod|modprobe""").containsMatchIn(cmd) -> "内核模块"
        Regex("""(?i)module\s+(install|uninstall|enable|disable)|boot-patch|--install""").containsMatchIn(cmd) -> "root 模块或启动镜像"
        Regex("""(?i)/data/adb""").containsMatchIn(cmd) -> "写入 /data/adb"
        else -> "改动隐藏或模块配置"
    }

    private fun isReadOnly(cmd: String): Boolean {
        val plain = cmd.replace(Regex("""\s2>\s*/dev/null"""), " ").replace(Regex("""\s2>&1"""), " ")
        if (plain.contains('`') || plain.contains("$(") || plain.contains("<(") || plain.contains(">(")) return false
        if (plain.contains('>') || plain.contains('<')) return false
        if (plain.contains(';') || plain.contains("&&") || plain.contains("||") || plain.contains('&') || plain.contains('\n')) return false
        val stages = plain.split('|')
        stages.forEachIndexed { i, raw ->
            val words = raw.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
            if (words.isEmpty()) return false
            val rawFirst = words[0]
            // A path is a user-chosen executable, even when its basename is `cat` or `ls`.
            if (rawFirst.contains('/') || rawFirst.startsWith(".")) return false
            val first = rawFirst
            if (i > 0) {
                if (first !in FILTERS) return false
                if (first == "sort" && words.any { it == "-o" || it.startsWith("--output") }) return false
                return@forEachIndexed
            }
            if (!stageReadOnly(first, words)) return false
        }
        return true
    }

    private fun stageReadOnly(first: String, words: List<String>): Boolean {
        if (first !in READ_FIRST) return false
        val args = words.drop(1)
        if (args.any { OUTPUT_FLAG.matches(it) || it == "-f" && first in setOf("logcat", "tcpdump", "screencap") }) return false
        if (first == "dumpsys" && args.any { it in DUMPSYS_WRITES }) return false
        when (first) {
            "find" -> if (args.any { it == "-delete" || it.startsWith("-exec") || it.startsWith("-ok") || it == "-fprint" || it.startsWith("-fprint") || it == "-fls" }) return false
            "top" -> if (args.none { it == "-n" || it.startsWith("-n") || it == "-b" }) return false
            "logcat" -> if (args.any { it == "-c" || it == "--clear" || it == "-G" || it.startsWith("-G") || it == "-P" || it == "-f" || it.startsWith("--file") }) return false
            "date" -> if (args.any { it == "-s" || it.startsWith("--set") || (it.isNotEmpty() && it[0].isDigit()) }) return false
            "mount" -> if (args.isNotEmpty()) return false
        }
        val subs = READ_SUB[first] ?: return true
        val sub = args.firstOrNull() ?: return first == "mount"
        if (sub !in subs) return false
        if (first == "cmd") {
            val verb = args.getOrNull(1) ?: return true
            if (verb !in CMD_READ) return false
        }
        if (first == "wm" && args.size > 1) return false
        if (first == "ip" && args.drop(1).any { it in WRITE_WORDS }) return false
        if (first == "content" && args.any { it == "insert" || it == "update" || it == "delete" || it == "call" }) return false
        return true
    }
}
