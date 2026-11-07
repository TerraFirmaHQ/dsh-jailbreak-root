package io.github.ccch1mneyyy.dsh_app.hostroot

/**
 * Shell text classifier used by host-root approval.
 * String classification is not shell isolation. Unknown commands are writes.
 * The shape matches the spec's classifyCommand result. Written for this tree;
 * not copied from a GPL repository.
 */
object CommandClassifier {
    data class Sub(val text: String, val verdict: String, val reason: String?)
    data class Result(
        val readonly: Boolean,
        val subcommands: List<Sub>,
        val hit: List<String>,
        val reason: String,
        val compoundBlocked: Boolean,
    )

    private val simpleRead = setOf(
        "getprop", "id", "whoami", "ps", "uptime", "date", "df", "du", "ls", "stat",
        "cat", "head", "tail", "wc", "grep", "ss", "netstat", "free", "uname", "dumpsys",
    )
    private val writeNames = setOf(
        "rm", "mv", "cp", "chmod", "chown", "ln", "mkdir", "rmdir", "touch", "truncate",
        "dd", "setprop", "resetprop", "reboot", "kill", "killall", "pkill", "eval", "exec",
        "insmod", "rmmod", "modprobe", "tee",
    )
    private val pmWrite = setOf("install", "uninstall", "disable-user", "enable", "clear", "grant", "revoke", "set-installer")
    private val pmRead = setOf("list", "path", "dump")
    private val iptablesWrite = setOf("-I", "-A", "-D", "-F", "-X", "-Z", "-N", "-P")

    fun classifyCommand(raw: String): Result {
        val parts = split(raw)
        val subs = parts.map { classifySegment(it) }
        val hits = linkedSetOf<String>()
        subs.forEach { sub -> if (sub.reason != null && sub.verdict != "read") hits += sub.reason }
        val allRead = subs.isNotEmpty() && subs.all { it.verdict == "read" }
        val compoundBlocked = allRead && rawHasBareBackground(raw)
        if (compoundBlocked) hits += "compound-blocked"
        val readonly = allRead && !compoundBlocked
        val reason = when {
            readonly -> "全部为只读"
            compoundBlocked -> "全部子命令均为只读，被复合规则拦截"
            subs.any { it.verdict == "write" } -> "含写入：" + subs.filter { it.verdict == "write" }.joinToString("；") { it.text }
            else -> "含未知命令，按写入处理"
        }
        return Result(readonly, subs, hits.toList(), reason, compoundBlocked)
    }

    /**
     * Session-memory key for a readonly misclassification only.
     * Paths and package-like tokens are dropped. Writes return null.
     */
    fun sessionKey(raw: String): String? {
        val result = classifyCommand(raw)
        if (!result.compoundBlocked) return null
        if (result.subcommands.any { it.verdict != "read" }) return null
        return result.subcommands.joinToString("|") { signature(it.text) }
    }

    private fun signature(text: String): String = text.split(Regex("\\s+")).filter { tok ->
        tok.isNotEmpty() && !tok.startsWith("/") && !tok.contains("/") && !(tok.contains('.') && !tok.startsWith("-"))
    }.joinToString(" ")

    /** `;` `&&` `||` `|` `&` and newlines. Quoted spans stay intact. */
    internal fun split(raw: String): List<String> {
        val out = mutableListOf<String>()
        val buf = StringBuilder()
        var i = 0
        var quote: Char? = null
        fun push() {
            val text = buf.toString().trim()
            if (text.isNotEmpty()) out += text
            buf.setLength(0)
        }
        while (i < raw.length) {
            val c = raw[i]
            if (quote != null) {
                buf.append(c)
                if (c == quote && raw.getOrNull(i - 1) != '\\') quote = null
                i++
                continue
            }
            if (c == '\'' || c == '"') {
                quote = c
                buf.append(c)
                i++
                continue
            }
            if (c == '\n' || c == ';') {
                push(); i++; continue
            }
            if (c == '&' && raw.getOrNull(i + 1) == '&') {
                push(); i += 2; continue
            }
            if (c == '|' && raw.getOrNull(i + 1) == '|') {
                push(); i += 2; continue
            }
            if (c == '|' || c == '&') {
                push(); i++; continue
            }
            buf.append(c)
            i++
        }
        push()
        return out
    }

    private fun classifySegment(segment: String): Sub {
        if (hasSubst(segment)) return Sub(segment, "write", "subst")
        if (hasRedirect(segment)) return Sub(segment, "write", "redirect")
        val words = words(segment)
        if (words.isEmpty()) return Sub(segment, "unknown", "empty")
        val first = words[0]
        val second = words.getOrNull(1)
        val third = words.getOrNull(2)
        if (first == "sh" || first == "bash") {
            if (second == "-c") {
                val inner = if (Regex("""\brm\b""").containsMatchIn(segment)) "sh-c+rm" else "sh-c"
                return Sub(segment, "write", inner)
            }
        }
        if (first == "svc" && (second == "stop" || second == "start")) return Sub(segment, "write", "svc")
        if (first in writeNames) return Sub(segment, "write", first)
        if (first == "settings") {
            return when (second) {
                "get" -> Sub(segment, "read", null)
                "put", "delete" -> Sub(segment, "write", "settings-$second")
                else -> Sub(segment, "unknown", "settings")
            }
        }
        if (first == "pm") {
            return when {
                second in pmRead -> Sub(segment, "read", null)
                second in pmWrite -> Sub(segment, "write", "pm-$second")
                else -> Sub(segment, "unknown", "pm")
            }
        }
        if (first == "cmd" && second == "appops") {
            return when (third) {
                "get" -> Sub(segment, "read", null)
                "set" -> Sub(segment, "write", "appops-set")
                else -> Sub(segment, "unknown", "appops")
            }
        }
        if (first == "cmd" && second == "settings" && third == "put") return Sub(segment, "write", "cmd-settings-put")
        if (first == "am" && second in setOf("start", "force-stop", "broadcast", "kill")) {
            return Sub(segment, "write", "am-$second")
        }
        if (first == "iptables" || first == "ip6tables") {
            val flags = words.drop(1)
            if (flags.any { it in iptablesWrite }) return Sub(segment, "write", "$first-write")
            if (flags.any { it == "-L" || it == "-S" }) return Sub(segment, "read", null)
            return Sub(segment, "unknown", first)
        }
        if (first == "logcat") {
            return if (words.drop(1).any { it == "-d" }) Sub(segment, "read", null) else Sub(segment, "unknown", "logcat")
        }
        if (first == "wm") {
            return if (second == "size" || second == "density") Sub(segment, "read", null) else Sub(segment, "unknown", "wm")
        }
        if (first == "ip") {
            return if (second == "addr" || second == "route") Sub(segment, "read", null) else Sub(segment, "unknown", "ip")
        }
        if (first == "find") {
            return if (words.any { it == "-delete" || it == "-exec" || it == "-execdir" }) Sub(segment, "write", "find-exec")
            else Sub(segment, "read", null)
        }
        if (first == "mount" || first == "umount") {
            return if (words.size > 1) Sub(segment, "write", first) else Sub(segment, "read", null)
        }
        if (first in simpleRead) return Sub(segment, "read", null)
        return Sub(segment, "unknown", "unknown")
    }

    private fun words(segment: String): List<String> {
        val out = mutableListOf<String>()
        val buf = StringBuilder()
        var quote: Char? = null
        for (c in segment) {
            if (quote != null) {
                if (c == quote) quote = null else buf.append(c)
                continue
            }
            if (c == '\'' || c == '"') {
                quote = c
                continue
            }
            if (c.isWhitespace()) {
                if (buf.isNotEmpty()) {
                    out += buf.toString()
                    buf.setLength(0)
                }
            } else buf.append(c)
        }
        if (buf.isNotEmpty()) out += buf.toString()
        return out
    }

    private fun hasSubst(segment: String): Boolean {
        var quote: Char? = null
        var i = 0
        while (i < segment.length) {
            val c = segment[i]
            if (quote != null) {
                if (c == quote) quote = null
                i++
                continue
            }
            if (c == '\'' || c == '"') {
                quote = c
                i++
                continue
            }
            if (c == '`') return true
            if (c == '$' && segment.getOrNull(i + 1) == '(') return true
            i++
        }
        return false
    }

    private fun hasRedirect(segment: String): Boolean {
        var quote: Char? = null
        var i = 0
        while (i < segment.length) {
            val c = segment[i]
            if (quote != null) {
                if (c == quote) quote = null
                i++
                continue
            }
            if (c == '\'' || c == '"') {
                quote = c
                i++
                continue
            }
            if (c == '>') return true
            i++
        }
        return false
    }

    /** A trailing background operator after an otherwise read-only command. */
    private fun rawHasBareBackground(raw: String): Boolean {
        val trimmed = raw.trimEnd()
        return trimmed.endsWith("&") && !trimmed.endsWith("&&")
    }
}
