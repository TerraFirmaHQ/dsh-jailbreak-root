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
            "$NOT_A_SANDBOX 任意 shell 都要用户看过预览才执行，文本看起来像只读也不等于隔离。读文件用 read_file。",
            mapOf(
                "command" to mapOf("type" to "string", "description" to "要执行的 shell 命令"),
                "cwd" to mapOf("type" to "string", "description" to "可选工作目录"),
                "timeoutMs" to mapOf("type" to "integer", "description" to "超时，最多 180000"),
            ),
            required = listOf("command"),
        ),
        tool(
            "read_file",
            "按固定命令读取一个绝对路径，最多 ${RootFiles.MAX_READ} 字节，包括 /data/adb。路径是参数，不是 shell。不弹审批。",
            mapOf("path" to mapOf("type" to "string")),
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
        runner: (command: String, timeoutMs: Long, cwd: String?) -> RootExec.Result = { c, t, d ->
            RootExec.run(c, t, d, spoolDir)
        },
    ): ToolOutput {
        if (!enabled()) {
            return ToolOutput("$DISABLED：root 开关是关的，没有调用 su。", isError = true)
        }
        return when (name) {
            "check_root" -> {
                if (!enabled()) return ToolOutput("$DISABLED：root 开关是关的，没有调用 su。", isError = true)
                render(runner("id -u", 8_000, null))
            }
            "shell" -> {
                val command = args["command"] as? String ?: return ToolOutput("缺少 command", isError = true)
                if (command.isBlank()) return ToolOutput("空命令，没有调用 su。", isError = true)
                // Classification only chooses the preview. It never skips approval.
                val looked = RootPolicy.classify(command)
                val verdict = if (looked.level == RootPolicy.Level.READ) {
                    RootPolicy.Verdict(RootPolicy.Level.APPROVAL, "任意 shell 不是隔离，需要确认")
                } else {
                    looked
                }
                runApproved(command, verdict, (args["timeoutMs"] as? Number)?.toLong(), args["cwd"] as? String, enabled, approver, runner)
            }
            "read_file" -> {
                val path = args["path"] as? String ?: return ToolOutput("缺少 path", isError = true)
                val why = RootFiles.rejectPath(path)
                if (why != null) return ToolOutput("路径无效：$why", isError = true)
                if (!enabled()) return ToolOutput("$DISABLED：root 开关是关的，没有调用 su。", isError = true)
                render(runner(RootFiles.readCommand(path), 15_000, null))
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
        return render(runner(command, timeoutMs ?: RootExec.DEFAULT_TIMEOUT_MS, cwd))
    }

    private fun render(result: RootExec.Result): ToolOutput {
        val text = buildString {
            append("state=").append(result.state.name.lowercase())
            append(" exit=").append(result.exitCode)
            if (result.truncated) append(" truncated=1")
            result.detail?.let { append("\n").append(it) }
            if (result.stdout.isNotEmpty()) append("\n").append(result.stdout)
            if (result.stderr.isNotEmpty()) append("\nstderr:\n").append(result.stderr)
            result.spool?.let { append("\nspool=").append(it) }
        }
        val error = result.state != RootExec.State.OK || result.exitCode != 0
        return ToolOutput(text, isError = error)
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
