package io.github.ccch1mneyyy.dsh_app.hostroot

import io.github.ccch1mneyyy.dsh_app.phone.ToolOutput
import java.io.File

/**
 * Host-root MCP tools. Tool arguments cannot grant approval: `approved`,
 * `force` and `skipApproval` are ignored. A write runs only after [approve]
 * returns true for that exact command.
 *
 * The shell is the Android host's uid 0. It is not a sandbox and it is not
 * the uid 0 shown inside PRoot.
 */
object RootTools {
    const val DISABLED = "ROOT_DISABLED"
    const val DENIED = "APPROVAL_DENIED"
    const val NOT_A_SANDBOX =
        "mcp__root__shell 在安卓主机上以 uid 0 执行，不是沙箱，也不是 PRoot 里的假 root。"

    val definitions: List<Map<String, Any?>> = listOf(
        tool("check_root", "检查本应用能否通过 su 取得 uid 0。不会改系统。", emptyMap()),
        tool(
            "shell",
            "$NOT_A_SANDBOX 写入和未知命令一定先预览。只读免确认默认关闭，只有用户在设置里打开且分类为 readonly 才跳过。分类不是隔离。background 为真时在审批后放入最多 2 个、最长 180 秒的后台任务，不是常驻服务。",
            mapOf(
                "command" to mapOf("type" to "string", "description" to "要执行的 shell 命令"),
                "cwd" to mapOf("type" to "string", "description" to "可选工作目录"),
                "timeoutMs" to mapOf("type" to "integer", "description" to "超时，最多 180000"),
                "background" to mapOf("type" to "boolean", "description" to "审批后转后台。不能跳过审批。"),
            ),
            required = listOf("command"),
        ),
        tool(
            "job",
            "查看或中断一个后台 root 任务。不新开命令。",
            mapOf(
                "id" to mapOf("type" to "string"),
                "cancel" to mapOf("type" to "boolean"),
            ),
            required = listOf("id"),
        ),
        tool(
            "read_file",
            "按固定命令读取一个绝对路径。默认 256KB，最大 1MB，可用 offset 和 limit 分段，包括 /data/adb。不弹审批。",
            mapOf(
                "path" to mapOf("type" to "string"),
                "offset" to mapOf("type" to "integer"),
                "limit" to mapOf("type" to "integer"),
            ),
            required = listOf("path"),
        ),
        tool(
            "write_file",
            "按原样写入字节。内容不会当 shell 执行。任何写入都先预览；/data/adb 和隐藏/模块路径会标明。取消则不写。",
            mapOf(
                "path" to mapOf("type" to "string"),
                "content" to mapOf("type" to "string", "description" to "UTF-8 文本"),
                "contentBase64" to mapOf("type" to "string", "description" to "精确字节；有则优先于 content"),
            ),
            required = listOf("path"),
        ),
    )

    fun interface Approver {
        /** Return true only after the person accepted [preview]. */
        fun approve(command: String, preview: String, protected: Boolean): Boolean
    }

    fun call(
        name: String,
        args: Map<String, Any?>,
        enabled: () -> Boolean,
        approver: Approver,
        spoolDir: File?,
        readonlySkip: () -> Boolean = { false },
        autoAllow: () -> Boolean = { false },
        sessionAllows: (String) -> Boolean = { false },
        runner: (command: String, timeoutMs: Long, cwd: String?) -> RootExec.Result = { c, t, d ->
            RootExec.run(c, t, d, spoolDir)
        },
    ): ToolOutput {
        RootExec.releaseIdle()
        if (!enabled() && name != "check_root") {
            return ToolOutput("$DISABLED：root 开关是关的，没有调用 su。", isError = true)
        }
        return when (name) {
            "check_root" -> {
                if (!enabled()) {
                    return ToolOutput(checkJson(granted = false, suCalled = false), isError = false)
                }
                val raw = runner(CHECK_SCRIPT, 8_000, null)
                ToolOutput(checkJson(raw), isError = raw.state != RootExec.State.OK)
            }
            "job" -> {
                val id = args["id"] as? String ?: return ToolOutput("缺少 id", isError = true)
                if (args["cancel"] == true) {
                    val ok = RootExec.cancel(id)
                    return ToolOutput("""{"id":${jsonString(id)},"cancelled":$ok}""", isError = !ok)
                }
                val job = RootExec.job(id) ?: return ToolOutput("""{"id":${jsonString(id)},"state":"absent"}""", isError = true)
                val result = job.result
                if (result == null) return ToolOutput("""{"id":${jsonString(id)},"state":"running"}""")
                present(result)
            }
            "shell" -> {
                val command = args["command"] as? String ?: return ToolOutput("缺少 command", isError = true)
                if (command.isBlank()) return ToolOutput("空命令，没有调用 su。", isError = true)
                val classified = CommandClassifier.classifyCommand(command)
                val looked = RootPolicy.classify(command)
                val verdict = when {
                    looked.level == RootPolicy.Level.PROTECTED -> looked
                    classified.readonly -> RootPolicy.Verdict(RootPolicy.Level.READ, classified.reason)
                    else -> RootPolicy.Verdict(RootPolicy.Level.APPROVAL, classified.reason)
                }
                val timeout = ((args["timeoutMs"] as? Number)?.toLong() ?: SHELL_TIMEOUT_MS).coerceIn(500, RootExec.MAX_TIMEOUT_MS)
                val cwd = args["cwd"] as? String
                val key = CommandClassifier.sessionKey(command)
                val auto = autoAllow()
                val skip = classified.readonly && readonlySkip()
                val remembered = key != null && sessionAllows(key)
                if (!auto && !skip && !remembered) {
                    val preview = previewOf(command, classified)
                    val ok = approver.approve(command, preview, verdict.level == RootPolicy.Level.PROTECTED)
                    if (!ok || !enabled()) return ToolOutput("$DENIED：没有执行。\n$preview", isError = true)
                }
                if (args["background"] == true) {
                    val job = RootExec.startJob(command, timeout, cwd, spoolDir)
                        ?: return ToolOutput("后台任务已满（最多 2 个）。先查 job 或等当前任务结束。", isError = true)
                    val body = """{"job":${jsonString(job.id)},"bounded":true,"timeoutMs":$timeout}"""
                    return if (!auto) ToolOutput(body) else ToolOutput(body.removeSuffix("}") + ",\"approval\":\"auto\"}")
                }
                val ran = present(runner(command, timeout, cwd))
                if (!auto) ran else ToolOutput(ran.text.removeSuffix("}") + ",\"approval\":\"auto\"}", isError = ran.isError)
            }
            "read_file" -> {
                val path = args["path"] as? String ?: return ToolOutput("缺少 path", isError = true)
                val why = RootFiles.rejectPath(path)
                if (why != null) return ToolOutput("路径无效：$why", isError = true)
                val offset = (args["offset"] as? Number)?.toLong()?.coerceAtLeast(0) ?: 0L
                val limit = (args["limit"] as? Number)?.toInt()?.coerceIn(1, RootFiles.MAX_CHUNK) ?: RootFiles.MAX_READ
                val size = runner(RootFiles.sizeCommand(path), 8_000, null).stdout.trim().toLongOrNull()
                val read = runner(RootFiles.readCommand(path, offset, limit), 15_000, null)
                val bytes = decodeRead(read.stdout)
                val slice = RootFiles.utf8Slice(bytes)
                val next = offset + slice.consumed
                val shown = read.copy(stdout = slice.text)
                val paging = size != null && next < size
                present(shown, extra = if (paging) chunkHint(size, next) else "")
            }
            "write_file" -> {
                val path = args["path"] as? String ?: return ToolOutput("缺少 path", isError = true)
                val why = RootFiles.rejectPath(path)
                if (why != null) return ToolOutput("路径无效：$why", isError = true)
                val bytes = decodeContent(args) ?: return ToolOutput("缺少 content 或 contentBase64", isError = true)
                val command = RootFiles.writeCommand(path, bytes)
                val verdict = if (RootPolicy.classify(path).level == RootPolicy.Level.PROTECTED ||
                    Regex("""(?i)/data/adb\b|zygisk|hma_oss|hma_plus|hmalist|tricky_store|shamiko|oplus_secure_guard""").containsMatchIn(path)
                ) {
                    RootPolicy.Verdict(RootPolicy.Level.PROTECTED, "写入受保护路径")
                } else {
                    RootPolicy.Verdict(RootPolicy.Level.APPROVAL, "写入文件")
                }
                runApproved(command, verdict, 20_000, null, enabled, approver, runner)
            }
            else -> ToolOutput("未知工具 $name", isError = true)
        }
    }

    private fun decodeContent(args: Map<String, Any?>): ByteArray? {
        val b64 = args["contentBase64"] as? String
        if (b64 != null) {
            return try {
                java.util.Base64.getDecoder().decode(b64)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
        val text = args["content"] as? String ?: return null
        return text.toByteArray(Charsets.UTF_8)
    }

    const val SHELL_TIMEOUT_MS = 180_000L
    private const val SUMMARY_BYTES = 64 * 1024

    const val CHECK_SCRIPT = """
id -u
printf '\n__D__\n'
id -Z
printf '\n__D__\n'
getenforce
printf '\n__D__\n'
uname -r
printf '\n__D__\n'
getprop ro.build.version.sdk
printf '\n__D__\n'
command -v su
printf '\n__D__\n'
ls -1 /data/adb/modules
printf '\n__D__\n'
ksud -V
"""

    private fun runApproved(
        command: String,
        verdict: RootPolicy.Verdict,
        timeoutMs: Long?,
        cwd: String?,
        enabled: () -> Boolean,
        approver: Approver,
        runner: (String, Long, String?) -> RootExec.Result,
    ): ToolOutput {
        val preview = RootPolicy.preview(command, verdict)
        val ok = approver.approve(command, preview, verdict.level == RootPolicy.Level.PROTECTED)
        if (!ok || !enabled()) return ToolOutput("$DENIED：没有执行。\n$preview", isError = true)
        return present(runner(command, timeoutMs ?: RootExec.DEFAULT_TIMEOUT_MS, cwd))
    }

    /** stdout is the body. stderr, exit and the spool path stay in their own fields. */
    internal fun present(result: RootExec.Result, extra: String = ""): ToolOutput {
        val source = result.spool?.let { path ->
            val file = File(path)
            if (file.isFile && file.length() > SUMMARY_BYTES) runCatching { file.readText() }.getOrNull() else null
        } ?: result.stdout
        val summarized = source.length > SUMMARY_BYTES || source.lines().size > 50
        val stdout = if (summarized) summarize(source, result.spool) else source
        val failure = failureOf(result)
        val text = buildString {
            append("{")
            append("\"stdout\":").append(jsonString(stdout))
            append(",\"stderr\":").append(jsonString(result.stderr))
            append(",\"exitCode\":").append(result.exitCode)
            append(",\"durationMs\":").append(result.durationMs)
            val rawBytes = result.stdout.toByteArray(Charsets.UTF_8).size.toLong()
            val spoolBytes = result.spool?.let { runCatching { File(it).length() }.getOrDefault(0L) } ?: 0L
            append(",\"stdoutBytes\":").append(maxOf(rawBytes, spoolBytes))
            if (result.spool != null) append(",\"spoolPath\":").append(jsonString(result.spool))
            append(",\"state\":").append(jsonString(result.state.name.lowercase()))
            if (failure != null) append(",").append(failure)
            if (result.truncated || summarized) {
                append(",\"attachment\":\"spool\"")
            }
            if (extra.isNotEmpty()) append(",").append(extra)
            append("}")
        }
        val error = result.state != RootExec.State.OK || result.exitCode != 0
        return ToolOutput(text, isError = error)
    }

    /** First 40 lines, last 10, line count, spool path. chars/4 is an estimate, not a model tokenizer. */
    internal fun summarize(text: String, spool: String?): String {
        val lines = text.lines()
        val head = lines.take(40)
        val tail = if (lines.size > 50) lines.takeLast(10) else emptyList()
        val tokens = (text.length + 3) / 4
        return buildString {
            append("摘要 共 ").append(lines.size).append(" 行，约 ").append(tokens).append(" token（按字符数/4 估算）")
            spool?.let { append("\n落盘 ").append(it) }
            append("\n")
            append(head.joinToString("\n"))
            if (tail.isNotEmpty()) {
                append("\n…\n")
                append(tail.joinToString("\n"))
            }
        }
    }

    private fun failureOf(result: RootExec.Result): String? {
        val failed = result.state != RootExec.State.OK || result.exitCode != 0
        if (!failed) return null
        val stage = when (result.state) {
            RootExec.State.TIMEOUT -> "timeout"
            RootExec.State.DENIED -> "su"
            RootExec.State.NO_SU -> "su"
            RootExec.State.ERROR -> "aborted"
            RootExec.State.OK -> "exit"
        }
        val raw = result.detail ?: result.stderr.ifBlank { result.state.name }
        val next = when (result.state) {
            RootExec.State.TIMEOUT -> "可带 background=true 再跑同一条命令，然后用 job 查看或中断"
            RootExec.State.NO_SU -> "到系统里确认 root 授权，或继续只用 PRoot"
            else -> "先看 stderr 和 exitCode，不要把同一条写入自动再跑"
        }
        return "\"stage\":${jsonString(stage)},\"error\":${jsonString(raw)},\"next\":${jsonString(next)}"
    }

    private fun previewOf(command: String, classified: CommandClassifier.Result): String = buildString {
        append("这条命令会在安卓主机上以 uid 0 执行，不是沙箱。\n")
        append(command).append("\n")
        classified.subcommands.forEach { sub ->
            append(sub.text).append("  ").append(sub.verdict)
            if (sub.reason != null) append("  ").append(sub.reason)
            append("\n")
        }
        append(classified.reason)
        if (classified.compoundBlocked) append("\n@@session=1")
    }

    internal fun checkJson(raw: RootExec.Result): String {
        val parts = raw.stdout.split("__D__").map { it.trim() }
        fun at(i: Int) = parts.getOrNull(i).orEmpty()
        val uid = at(0).lineSequence().lastOrNull()?.toIntOrNull()
        val modules = at(6).lineSequence().map { it.trim() }.filter { it.isNotEmpty() && it != "0" }.toList()
        val sdk = at(4).lineSequence().lastOrNull()?.toIntOrNull()
        val version = at(7).lineSequence().map { it.trim() }.lastOrNull { it.isNotEmpty() }.orEmpty()
        return checkJson(
            granted = uid == 0 && raw.state == RootExec.State.OK,
            suCalled = true,
            uid = uid,
            context = at(1),
            selinux = at(2),
            kernel = at(3),
            sdk = sdk,
            suPath = at(5).lineSequence().lastOrNull { it.isNotBlank() }.orEmpty(),
            modules = modules,
            manager = if (version.isNotEmpty()) "ksud" else "",
            managerVersion = version,
        )
    }

    internal fun checkJson(
        granted: Boolean,
        suCalled: Boolean,
        uid: Int? = null,
        context: String = "",
        selinux: String = "",
        kernel: String = "",
        sdk: Int? = null,
        suPath: String = "",
        modules: List<String> = emptyList(),
        manager: String = "",
        managerVersion: String = "",
    ): String = buildString {
        append("{\"state\":").append(jsonString(if (!suCalled) "disabled" else if (granted) "ok" else "degraded"))
        append(",\"granted\":").append(granted)
        append(",\"uid\":").append(uid?.toString() ?: "null")
        append(",\"suPath\":").append(jsonString(suPath))
        append(",\"managerName\":").append(jsonString(manager))
        append(",\"managerVersion\":").append(jsonString(managerVersion))
        append(",\"selinux\":").append(jsonString(selinux))
        append(",\"hideStackModules\":[")
        modules.forEachIndexed { i, name -> if (i > 0) append(","); append(jsonString(name)) }
        append("],\"kernelRelease\":").append(jsonString(kernel))
        append(",\"androidSdk\":").append(sdk?.toString() ?: "null")
        append(",\"context\":").append(jsonString(context))
        append(",\"suCalled\":").append(suCalled)
        append("}")
    }

    private fun chunkHint(total: Long, next: Long) =
        "\"needChunk\":true,\"totalSize\":$total,\"nextOffset\":$next,\"paging\":true"

    private fun decodeRead(stdout: String): ByteArray {
        val trimmed = stdout.trim()
        if (trimmed.isEmpty()) return ByteArray(0)
        return try {
            java.util.Base64.getMimeDecoder().decode(trimmed)
        } catch (_: IllegalArgumentException) {
            trimmed.toByteArray(Charsets.UTF_8)
        }
    }

    internal fun jsonString(s: String): String = buildString {
        append('"')
        for (c in s) {
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }

    private fun tool(name: String, description: String, properties: Map<String, Any?>, required: List<String> = emptyList()): Map<String, Any?> =
        linkedMapOf(
            "name" to name,
            "description" to description,
            "inputSchema" to linkedMapOf(
                "type" to "object",
                "properties" to properties,
                "required" to required,
            ),
        )
}
