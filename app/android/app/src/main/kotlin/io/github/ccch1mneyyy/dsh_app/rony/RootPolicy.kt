package io.github.ccch1mneyyy.dsh_app.rony

/**
 * Rony fork: the one place that decides how risky a root shell command is.
 *
 * - [Level.READ_ONLY]: every pipeline stage starts with a read-only tool (or a pure text filter
 *   after a pipe), no redirection, no command substitution, no `&&`/`;` chaining. Runs at once.
 * - [Level.DANGEROUS]: anything else (writes, installs, settings changes, reboots, chaining,
 *   redirection). Needs the person's approval while the approval switch is on.
 * - [Level.BLOCKED]: never runs, whatever the switches say: wiping or formatting, raw block
 *   devices, flashing, bootloader/recovery reboots, and reading secrets (passwords, tokens, keys,
 *   the keystore, lock-screen and Wi-Fi credentials).
 *
 * Pure Kotlin (no Android types) so RootPolicyTest pins the table from RONY-FORK-README.md.
 */
object RootPolicy {
    enum class Level(val wire: String) { READ_ONLY("read_only"), DANGEROUS("dangerous"), BLOCKED("blocked") }

    data class Verdict(val level: Level, val reason: String)

    const val MAX_COMMAND = 16_384

    /** Tools that only read, as the first word of a stage. */
    private val READ_ONLY_FIRST = setOf(
        "id", "ls", "cat", "head", "tail", "df", "du", "ps", "uname", "whoami", "pwd", "stat", "file",
        "getprop", "dumpsys", "pm", "cmd", "settings", "wc", "grep", "egrep", "fgrep", "find", "date",
        "uptime", "free", "mount", "lsof", "netstat", "ip", "ifconfig", "getenforce", "printenv",
        "which", "readlink", "realpath", "md5sum", "sha1sum", "sha256sum", "logcat", "top", "echo",
        "true", "test", "basename", "dirname", "nproc", "lsmod", "service", "appops", "dumpstate",
        "content", "am", "wm", "ss", "su", "magisk", "ksud", "apd",
    )

    /** Text filters allowed after a pipe in a read-only command. */
    private val FILTERS = setOf(
        "grep", "egrep", "fgrep", "head", "tail", "wc", "sort", "uniq", "cut", "tr", "cat", "column", "nl",
    )

    /** Sub-commands that only read, for tools that can also write. */
    private val READ_ONLY_SUB = mapOf(
        "pm" to setOf("list", "path", "dump", "resolve-activity", "query-activities", "get-max-users", "list-users", "get-install-location", "has-feature"),
        "settings" to setOf("get", "list"),
        "cmd" to setOf("package", "battery", "deviceidle", "stats", "netpolicy", "connectivity", "wifi", "overlay", "notification", "appops", "activity", "window", "display", "thermalservice", "power", "user", "role"),
        "appops" to setOf("get", "query-op"),
        "content" to setOf("query"),
        "am" to setOf("stack", "get-current-user", "get-config", "get-inactive", "get-standby-bucket", "monitor"),
        "wm" to setOf("size", "density"),
        "ip" to setOf("addr", "address", "a", "route", "r", "link", "l", "neigh", "rule", "-4", "-6", "-s", "-br"),
        "mount" to setOf(),
        "service" to setOf("list", "check"),
        "magisk" to setOf("-v", "-V", "--path", "-c"),
        "ksud" to setOf("-V", "--version", "-v", "debug", "module"),
        "apd" to setOf("-V", "--version", "-v"),
        "su" to setOf("-v", "-V", "--version"),
    )

    /** `cmd <svc> <verb>`: verbs that only read. */
    private val CMD_READ_VERBS = setOf("list", "get", "query", "dump", "status", "help", "get-standby-bucket", "list-packages", "resolve-activity", "get-mode", "get-app-link-owner", "get-app-links", "list-users")

    /** `wm size`/`wm density` with an argument write; `ip route add` writes. */
    private val WRITE_WORDS = setOf(
        "add", "del", "delete", "flush", "set", "put", "replace", "change", "append", "reset", "clear",
        "install", "uninstall", "enable", "disable", "grant", "revoke", "kill", "force-stop", "suspend",
        "unsuspend", "hide", "unhide", "remove", "write", "start", "stop", "broadcast", "trim-caches",
        "-i", "--install", "-m", "--module",
    )

    /** `dumpsys battery set level 5`, `dumpsys deviceidle force-idle`… change state. */
    private val DUMPSYS_WRITES = setOf("set", "reset", "unplug", "enable", "disable", "force-idle", "unforce", "step", "whitelist", "tempwhitelist", "--reset", "put", "clear")

    private val BLOCKED = listOf(
        Regex("""(^|[\s;&|(])rm\s+(-[a-zA-Z]*\s+)*-[a-zA-Z]*[rR][a-zA-Z]*\s+(-[a-zA-Z]*\s+)*(/|/\*|/system|/vendor|/product|/odm|/data|/sdcard|/storage|/storage/emulated/0|/metadata|/persist|/efs|/mnt)/?\*?(\s|$|;|&|\|)""") to "递归删除根目录或系统分区",
        Regex("""(^|[\s;&|(])(dd|mkfs[\w.]*|mke2fs|mkswap|make_ext4fs|mkfs\.f2fs|sgdisk|parted|fdisk|blkdiscard|flash_image|erase_image|fastboot|edl)(\s|$)""") to "块设备写入/格式化/刷写",
        Regex("""/dev/block""") to "触碰块设备",
        Regex("""(^|[\s;&|(])(reboot|svc\s+power\s+reboot)\s+(-p\s+)?(bootloader|recovery|fastboot|edl|download|sideload|dm-verity)""") to "重启到刷机模式",
        Regex(""">\s*/dev/(?!null\b|zero\b)""") to "写设备节点",
        Regex("""(^|[\s;&|(])(magisk\s+--install|magisk\s+--direct-install|ksud\s+(boot-patch|boot-restore|install|flash)|apd\s+(boot-patch|install)|update_engine_client|recovery\s+--wipe|wipe\s+data|vdc\s+cryptfs|sm\s+partition|twrp)""") to "刷机/安装 Root 内核/清数据",
        Regex("""chmod\s+(-R\s+)?[0-7]*777\s+/(\s|$)""") to "改根目录权限",
        Regex("""(^|[\s;&|(])(setprop\s+(ro\.|sys\.boot|persist\.sys\.usb)|resetprop\s+-?-?d?\s*ro\.boot)""") to "改启动关键属性",
        Regex("""(?i)(password|passwd|passcode|\btoken|secret|credential|\.key\b|\.pem\b|\.p12\b|keystore|keychain|locksettings|gatekeeper|gesture\.key|wificonfigstore|/data/misc/wifi|/data/misc/keystore|/data/system/users/\d+/accounts|accounts(_ce|_de)?\.db|cookies\b|login data)""") to "读取密码/令牌/密钥等机密",
        Regex("""(^|[\s;&|(])(:\(\)\s*\{|fork\s*bomb)""") to "fork 炸弹",
        // Ace7 hard blacklist: hide-root stack + ColorOS secure bits. Paths/ops only — version probes (su -v) still ok.
        Regex("""(?i)/data/adb""") to "触碰 /data/adb（KSU/模块/隐藏环境）",
        Regex("""(?i)/data/adb/(modules|ksu|magisk|ap|lspd|tricky_store|shamiko)""") to "触碰隐藏/Root 模块目录",
        Regex("""(?i)(com\.google\.android\.hmalist|com\.android\.vending\.hs|icu\.nullptr\.tricky_store|io\.github\.a13e\.hma|zygisknext)""") to "触碰隐藏环境包名/组件",
        Regex("""(?i)oplus_secure_guard""") to "触碰 ColorOS oplus_secure_guard",
        Regex("""(?i)(^|[\s;&|(])(setenforce|magiskpolicy|supolicy)\b""") to "改 SELinux 策略",
        Regex("""(?i)(^|[\s;&|(])(busybox\s+)?(u?mount)\b.*(-o\s+\S*remount|remount)""") to "remount 系统分区",
        Regex("""(?i)(^|[\s;&|(])(insmod|rmmod|modprobe)\b""") to "装/卸内核模块",
        Regex("""(?i)(^|[\s;&|(])(ksud|magisk|apd)\s+(module\s+(install|uninstall|enable|disable)|--install|boot-patch|flash|install)\b""") to "装/卸 Root 模块或刷写",
        Regex("""(?i)(^|[\s;&|(])resetprop\b""") to "resetprop 改属性（可能破坏隐藏）",
        Regex("""(?i)\bpersist\.(sys|vendor|oplus)\.""") to "改 persist 系统属性",
    )

    fun classify(command: String): Verdict {
        val cmd = command.trim()
        if (cmd.isEmpty()) return Verdict(Level.BLOCKED, "空命令")
        if (cmd.length > MAX_COMMAND) return Verdict(Level.BLOCKED, "命令过长")
        for ((re, why) in BLOCKED) if (re.containsMatchIn(cmd)) return Verdict(Level.BLOCKED, why)
        // Harmless stderr plumbing doesn't make a command write anything.
        val plain = cmd.replace(Regex("""\s2>\s*/dev/null"""), " ").replace(Regex("""\s2>&1"""), " ")
        if (plain.contains('`') || plain.contains("$(") || plain.contains("<(") || plain.contains(">(")) {
            return Verdict(Level.DANGEROUS, "含命令替换")
        }
        if (plain.contains('>') || plain.contains('<')) return Verdict(Level.DANGEROUS, "含重定向")
        if (plain.contains(';') || plain.contains("&&") || plain.contains("||") || plain.contains('&') || plain.contains('\n')) {
            return Verdict(Level.DANGEROUS, "含命令连接符")
        }
        val stages = plain.split('|')
        stages.forEachIndexed { i, raw ->
            val words = raw.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
            if (words.isEmpty()) return Verdict(Level.DANGEROUS, "空管道段")
            val first = words[0].substringAfterLast('/')
            if (i > 0) {
                if (first !in FILTERS) return Verdict(Level.DANGEROUS, "管道后接非过滤命令 $first")
                if (first == "sort" && words.any { it == "-o" || it.startsWith("--output") }) return Verdict(Level.DANGEROUS, "sort -o 写文件")
                return@forEachIndexed
            }
            stageVerdict(first, words)?.let { return it }
        }
        return Verdict(Level.READ_ONLY, "只读")
    }

    private fun stageVerdict(first: String, words: List<String>): Verdict? {
        if (first !in READ_ONLY_FIRST) return Verdict(Level.DANGEROUS, "$first 不在只读白名单")
        val args = words.drop(1)
        when (first) {
            "find" -> if (args.any { it == "-delete" || it.startsWith("-exec") || it.startsWith("-ok") || it == "-fprint" || it.startsWith("-fprint") || it == "-fls" }) return Verdict(Level.DANGEROUS, "find 带删除/执行")
            "echo", "true", "test" -> Unit
            "top" -> if (args.none { it == "-n" || it.startsWith("-n") || it == "-b" }) return Verdict(Level.DANGEROUS, "top 需带 -n（否则不会结束）")
            "logcat" -> if (args.any { it == "-c" || it == "--clear" || it == "-G" || it.startsWith("-G") || it == "-P" || it == "-f" || it.startsWith("--file") }) return Verdict(Level.DANGEROUS, "logcat 清空/改缓冲/写文件")
            "date" -> if (args.any { it == "-s" || it.startsWith("--set") || (it.isNotEmpty() && it[0].isDigit()) }) return Verdict(Level.DANGEROUS, "date 改时间")
            "mount" -> if (args.isNotEmpty()) return Verdict(Level.DANGEROUS, "mount 带参数")
            "printenv" -> Unit
        }
        val subs = READ_ONLY_SUB[first]
        if (subs != null) {
            val sub = args.firstOrNull() ?: return if (first == "mount" || first == "ip" || first == "su") null else Verdict(Level.DANGEROUS, "$first 缺少子命令")
            if (first == "su") return if (sub in subs && args.size == 1) null else Verdict(Level.DANGEROUS, "su 套娃执行")
            if (sub !in subs) return Verdict(Level.DANGEROUS, "$first $sub 会改动系统")
            if (first == "cmd") {
                val verb = args.getOrNull(1) ?: return null
                if (verb !in CMD_READ_VERBS) return Verdict(Level.DANGEROUS, "cmd $sub $verb 会改动系统")
            }
            if (first == "wm" && args.size > 1) return Verdict(Level.DANGEROUS, "wm $sub 带参数会改屏幕")
            if (first == "ip" && args.drop(1).any { it in WRITE_WORDS }) return Verdict(Level.DANGEROUS, "ip 改网络")
            if (first == "ksud" && sub == "module" && args.getOrNull(1) != "list") return Verdict(Level.DANGEROUS, "ksud module 改模块")
            if (first == "ksud" && sub == "debug") return Verdict(Level.DANGEROUS, "ksud debug")
            if (first == "magisk" && sub == "-c" && args.size > 1) return Verdict(Level.DANGEROUS, "magisk -c")
            if (first == "am" && sub == "monitor") return Verdict(Level.DANGEROUS, "am monitor 不会结束")
        }
        if (first == "dumpsys" && args.any { it in DUMPSYS_WRITES }) return Verdict(Level.DANGEROUS, "dumpsys 带修改参数")
        if (first == "content" && args.any { it == "insert" || it == "update" || it == "delete" || it == "call" }) return Verdict(Level.DANGEROUS, "content 写入")
        return null
    }

    /** For the audit log: values after password=/token=/key= masked, at most 300 chars. */
    fun redact(command: String): String {
        val r = command.replace(Regex("""(?i)(password|passwd|token|secret|key)(\s*[=:]\s*)\S+"""), "$1$2***")
        return if (r.length > 300) r.take(300) + "…" else r
    }
}
