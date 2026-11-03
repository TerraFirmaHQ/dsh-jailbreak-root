package io.github.ccch1mneyyy.dsh_app.rony

import android.content.Context
import android.content.pm.PackageManager
import io.github.ccch1mneyyy.dsh_app.phone.ToolOutput
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Rony fork: serves the `root` MCP tools and the settings page's manual runs. Order for every
 * command: master switch → [RootPolicy] (blocked never runs) → approval card for dangerous
 * commands while 危险命令审批 is on (or inside a "10 分钟内都允许" window) → [RootExec] → audit log.
 */
class RootCore(private val app: Context, private val prefs: RonyPrefs) {

    // ── Root status ──

    /** Known root managers by package (Magisk can hide under a random one). */
    private val managers = linkedMapOf(
        "me.weishu.kernelsu" to "KernelSU",
        "com.rifsxd.ksunext" to "KernelSU Next",
        "com.sukisu.ultra" to "SukiSU Ultra",
        "me.bmax.apatch" to "APatch",
        "me.garfieldhan.apatch.next" to "APatch Next",
        "com.topjohnwu.magisk" to "Magisk",
        "io.github.vvb2060.magisk" to "Magisk Alpha",
        "io.github.huskydg.magisk" to "Kitsune Mask",
    )

    fun installedManagers(): List<String> = managers.filter { (pkg, _) ->
        try {
            app.packageManager.getPackageInfo(pkg, 0); true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }.values.toList()

    /**
     * Asks su for `id` (which makes KernelSU / Magisk show their grant prompt the first time) and,
     * once granted, which manager and version answered.
     */
    fun checkRoot(): Map<String, Any?> {
        val script = "id; echo \"SU:\$(su -v 2>/dev/null)\"; echo \"SUV:\$(su -V 2>/dev/null)\"; " +
            "echo \"MAGISK:\$(magisk -v 2>/dev/null)\"; echo \"KSU:\$(ksud -V 2>/dev/null)\"; echo \"APD:\$(apd -V 2>/dev/null)\"; " +
            "[ -d /data/adb/ksu ] && echo DIR:ksu; [ -d /data/adb/magisk ] && echo DIR:magisk; [ -d /data/adb/ap ] && echo DIR:ap; " +
            "echo \"SELINUX:\$(getenforce 2>/dev/null)\"; true"
        val r = RootExec.run(script, timeoutMs = 45_000)
        val state = when (r.state) {
            RootExec.State.OK -> "granted"
            RootExec.State.DENIED -> "denied"
            RootExec.State.NO_SU -> "no_su"
            RootExec.State.TIMEOUT -> "timeout"
            RootExec.State.ERROR -> "error"
        }
        val out = r.stdout.lines()
        fun v(tag: String) = out.firstOrNull { it.startsWith("$tag:") }?.substringAfter(':')?.trim()?.takeIf { it.isNotEmpty() }
        val dirs = out.filter { it.startsWith("DIR:") }.map { it.removePrefix("DIR:") }
        val su = v("SU").orEmpty()
        val manager = when {
            v("KSU") != null || "ksu" in dirs || su.contains("KernelSU", true) || su.contains("ksu", true) -> "KernelSU" + (v("KSU")?.let { " $it" } ?: "")
            v("APD") != null || "ap" in dirs || su.contains("APatch", true) -> "APatch" + (v("APD")?.let { " $it" } ?: "")
            v("MAGISK") != null || "magisk" in dirs || su.contains("MAGISK", true) -> "Magisk" + (v("MAGISK")?.let { " $it" } ?: "")
            else -> installedManagers().firstOrNull()
        }
        prefs.lastRootState = state
        prefs.lastRootDetail = r.detail
        prefs.lastCheckedAt = System.currentTimeMillis()
        return linkedMapOf(
            "state" to state,
            "granted" to (state == "granted"),
            "uid" to out.firstOrNull { it.startsWith("uid=") },
            "suPath" to RootExec.visibleSu(),
            "manager" to manager,
            "suVersion" to (su.ifEmpty { null } ?: v("SUV")),
            "selinux" to v("SELINUX"),
            "installedManagers" to installedManagers(),
            "detail" to r.detail,
            "package" to app.packageName,
        )
    }

    /** The one automatic request, made the first time root is switched on (never again after a denial). */
    fun autoRequestOnce(): Map<String, Any?>? {
        if (prefs.autoRequested) return null
        prefs.autoRequested = true
        return checkRoot()
    }

    fun status(): Map<String, Any?> = linkedMapOf(
        "state" to prefs.lastRootState,
        "detail" to prefs.lastRootDetail,
        "checkedAt" to prefs.lastCheckedAt,
        "suPath" to RootExec.visibleSu(),
        "installedManagers" to installedManagers(),
        "package" to app.packageName,
    )

    // ── Guarded execution ──

    class Outcome(val decision: String, val level: RootPolicy.Level, val reason: String, val result: RootExec.Result?) {
        fun toMap(): Map<String, Any?> = linkedMapOf(
            "decision" to decision, "level" to level.wire, "reason" to reason,
        ) + (result?.toMap() ?: emptyMap())
    }

    /**
     * The gate every command goes through. [run] executes once allowed. [forcedLevel] lets fixed
     * tools state their level (set_setting is dangerous whatever its text looks like).
     */
    fun guarded(source: String, tool: String, command: String, forcedLevel: RootPolicy.Level? = null, preset: RootPolicy.Verdict? = null, run: () -> RootExec.Result): Outcome {
        val verdict = preset ?: RootPolicy.classify(command).let { v ->
            if (forcedLevel != null && v.level != RootPolicy.Level.BLOCKED && forcedLevel.ordinal > v.level.ordinal) RootPolicy.Verdict(forcedLevel, "固定工具：$tool") else v
        }
        fun done(decision: String, result: RootExec.Result?): Outcome {
            audit(source, tool, command, verdict, decision, result)
            return Outcome(decision, verdict.level, verdict.reason, result)
        }
        if (!prefs.rootEnabled) return done("refused", null)
        if (verdict.level == RootPolicy.Level.BLOCKED) return done("blocked", null)
        var decision = "done"
        if (verdict.level == RootPolicy.Level.DANGEROUS) {
            if (!prefs.approval) {
                decision = "unapproved_switch_off"
            } else if (RootApproval.trusted()) {
                decision = "approved_window"
            } else {
                val title = if (source == "agent") "AI 要以 Root 执行危险命令" else "以 Root 执行危险命令"
                when (RootApproval.request(app, title, command, verdict.reason)) {
                    RootApproval.Decision.ALLOW_ONCE -> decision = "approved"
                    RootApproval.Decision.ALLOW_10MIN -> decision = "approved_10min"
                    RootApproval.Decision.DENY -> return done("denied", null)
                    RootApproval.Decision.TIMEOUT -> return done("denied_timeout", null)
                }
            }
        }
        val result = try {
            run()
        } catch (e: Throwable) {
            RootExec.Result(RootExec.State.ERROR, -1, "", "", false, 0, "${e.javaClass.simpleName}: ${e.message}")
        }
        if (result.state == RootExec.State.DENIED || result.state == RootExec.State.NO_SU) {
            prefs.lastRootState = if (result.state == RootExec.State.DENIED) "denied" else "no_su"
            prefs.lastRootDetail = result.detail
        } else if (result.state == RootExec.State.OK) {
            prefs.lastRootState = "granted"
        }
        return done(decision, result)
    }

    private fun audit(source: String, tool: String, command: String, verdict: RootPolicy.Verdict, decision: String, r: RootExec.Result?) {
        prefs.audit(
            linkedMapOf(
                "time" to OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                "source" to source,
                "tool" to tool,
                "command" to RootPolicy.redact(command),
                "class" to verdict.level.wire,
                "reason" to verdict.reason,
                "decision" to decision,
                "state" to r?.state?.wire,
                "exitCode" to (r?.exitCode ?: -1),
                "ms" to (r?.durationMs ?: 0),
            ),
        )
    }

    /** The settings page's 运行命令 (same gate, source `manual`). */
    fun manualRun(command: String): Map<String, Any?> =
        guarded("manual", "manual", command) { RootExec.run(command) }.toMap()

    // ── MCP tools ──

    fun call(name: String, args: Map<String, Any?>): ToolOutput {
        if (!prefs.rootEnabled) {
            // l10n:keep agent tool error
            return ToolOutput("Root 总开关是关的：请用户到 设置 → Root 与 MCP（Rony）打开 Root 总开关并重启引擎。", isError = true)
        }
        return when (name) {
            "check_root" -> {
                val r = if (args["request"] == true || prefs.lastRootState != "granted") checkRoot() else checkRoot()
                ToolOutput(render(r), isError = r["granted"] != true)
            }
            "shell" -> shell(args)
            "session" -> session(args)
            "job" -> job(args)
            "getprop" -> {
                val n = (args["name"] as? String)?.trim().orEmpty()
                if (n.isNotEmpty() && !Regex("^[A-Za-z0-9_.:-]{1,128}$").matches(n)) return ToolOutput("属性名不合法", isError = true)
                val cmd = if (n.isEmpty()) "getprop" else "getprop ${RootExec.quote(n)}"
                text(guarded("agent", "getprop", cmd) { RootExec.run(cmd, 15_000) })
            }
            "logcat" -> logcat(args)
            "set_setting" -> setSetting(args)
            "linux" -> linux(args)
            // l10n:keep agent tool error
            else -> ToolOutput("没有这个工具：$name", isError = true)
        }
    }

    private fun timeoutOf(args: Map<String, Any?>): Long = (args["timeout_ms"] as? Number)?.toLong() ?: RootExec.DEFAULT_TIMEOUT_MS

    private fun shell(args: Map<String, Any?>): ToolOutput {
        val command = args["command"] as? String ?: return ToolOutput("缺少 command", isError = true)
        val cwd = (args["cwd"] as? String)?.takeIf { it.isNotBlank() }
        val sid = (args["session_id"] as? String)?.takeIf { it.isNotBlank() }
        if (sid != null) {
            val s = RootExec.session(sid) ?: return ToolOutput("会话 $sid 不存在或已结束，请先 session open", isError = true)
            val full = if (cwd != null) "cd ${RootExec.quote(cwd)} && $command" else command
            return text(guarded("agent", "shell", command) { s.runCommand(full, timeoutOf(args)) })
        }
        return text(guarded("agent", "shell", command) { RootExec.run(command, timeoutOf(args), cwd) })
    }

    private fun session(args: Map<String, Any?>): ToolOutput = when (args["action"] as? String) {
        "open" -> {
            val (s, r) = RootExec.openSession((args["cwd"] as? String)?.takeIf { it.isNotBlank() })
            if (s == null) ToolOutput(renderResult(r), isError = true) else ToolOutput("session_id=${s.id}（root shell pid ${s.shellPid}）。之后在 shell 里传 session_id 复用；用完 session close。")
        }
        "close" -> {
            val id = args["session_id"] as? String ?: ""
            ToolOutput(if (RootExec.closeSession(id)) "已关闭 $id" else "没有会话 $id", isError = false)
        }
        "list" -> ToolOutput(io.github.ccch1mneyyy.dsh_app.phone.Json.write(RootExec.listSessions()))
        else -> ToolOutput("action 应为 open | close | list", isError = true)
    }

    private fun job(args: Map<String, Any?>): ToolOutput {
        val id = args["job_id"] as? String
        return when (args["action"] as? String) {
            "start" -> {
                val command = args["command"] as? String ?: return ToolOutput("start 需要 command", isError = true)
                var job: RootExec.Job? = null
                val o = guarded("agent", "job", command) {
                    job = RootExec.startJob(command, (args["cwd"] as? String)?.takeIf { it.isNotBlank() })
                    RootExec.Result(RootExec.State.OK, 0, "", "", false, 0)
                }
                val j = job
                if (j == null) text(o) else ToolOutput("已在后台启动 job_id=${j.id}。用 job status / output 查看，job kill 结束。")
            }
            "status" -> RootExec.job(id ?: "")?.let { ToolOutput(io.github.ccch1mneyyy.dsh_app.phone.Json.write(it.toMap())) } ?: ToolOutput("没有任务 $id", isError = true)
            "output" -> RootExec.job(id ?: "")?.let {
                val (out, dropped) = it.output((args["tail_chars"] as? Number)?.toInt() ?: 8000)
                ToolOutput("[${it.state}${it.exitCode?.let { c -> " exit=$c" } ?: ""}${if (dropped > 0) "，更早的 $dropped 字符已丢弃" else ""}]\n$out")
            } ?: ToolOutput("没有任务 $id", isError = true)
            "kill" -> RootExec.job(id ?: "")?.let { it.kill(); ToolOutput("已结束 ${it.id}") } ?: ToolOutput("没有任务 $id", isError = true)
            "list" -> ToolOutput(io.github.ccch1mneyyy.dsh_app.phone.Json.write(RootExec.listJobs()))
            else -> ToolOutput("action 应为 start | status | output | kill | list", isError = true)
        }
    }

    private fun logcat(args: Map<String, Any?>): ToolOutput {
        val lines = ((args["lines"] as? Number)?.toInt() ?: 200).coerceIn(1, 3000)
        val pri = (args["priority"] as? String)?.takeIf { it in setOf("V", "D", "I", "W", "E", "F") } ?: "V"
        val tag = (args["tag"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        if (tag != null && !Regex("^[A-Za-z0-9_.$-]{1,64}$").matches(tag)) return ToolOutput("tag 不合法", isError = true)
        val buffer = (args["buffer"] as? String)?.takeIf { it in setOf("main", "system", "crash", "events", "radio", "all") } ?: "main"
        val grep = (args["grep"] as? String)?.takeIf { it.isNotEmpty() }
        val filter = if (tag != null) "-s ${RootExec.quote("$tag:$pri")}" else RootExec.quote("*:$pri")
        val cmd = "logcat -d -b $buffer -t ${if (grep != null) 3000 else lines} $filter"
        val o = guarded("agent", "logcat", cmd) { RootExec.run(cmd, 30_000, maxChars = 400_000) }
        val r = o.result ?: return text(o)
        if (grep == null || r.state != RootExec.State.OK) return text(o)
        val kept = r.stdout.lineSequence().filter { it.contains(grep, ignoreCase = true) }.toList().takeLast(lines)
        return ToolOutput("logcat（含 \"$grep\" 的最近 ${kept.size} 行）\n" + kept.joinToString("\n").take(RootExec.MAX_OUTPUT))
    }

    private fun setSetting(args: Map<String, Any?>): ToolOutput {
        val ns = (args["namespace"] as? String)?.takeIf { it in setOf("system", "secure", "global") } ?: return ToolOutput("namespace 应为 system/secure/global", isError = true)
        val key = (args["key"] as? String)?.trim().orEmpty()
        if (!Regex("^[A-Za-z0-9_.:-]{1,128}$").matches(key)) return ToolOutput("key 不合法", isError = true)
        val value = args["value"]?.toString()
        val cmd = if (value == null) "settings delete $ns ${RootExec.quote(key)}" else "settings put $ns ${RootExec.quote(key)} ${RootExec.quote(value)}"
        return text(guarded("agent", "set_setting", cmd, forcedLevel = RootPolicy.Level.DANGEROUS) { RootExec.run(cmd, 15_000) })
    }

    // ── Linux environment (v5.0) ──

    private val linuxStatusVerdict = RootPolicy.Verdict(RootPolicy.Level.READ_ONLY, "Linux 环境状态")

    /** Owner-initiated env operations from the settings page (install, wipe, QQ stack): audited, no approval card. */
    fun linuxOp(args: List<String>, timeoutMs: Long, extraEnv: Map<String, String> = emptyMap()): RootExec.Result {
        val cmd = RonyLinux.command(RonyLinux.script(app).path, args, RonyLinux.env(prefs, extraEnv))
        return guarded("manual", "linux", "rony-env " + args.joinToString(" "), preset = RootPolicy.Verdict(RootPolicy.Level.READ_ONLY, "设置页操作")) {
            RootExec.run(cmd, timeoutMs, maxChars = 200_000)
        }.result ?: RootExec.Result(RootExec.State.ERROR, -1, "", "", false, 0, "Root 总开关是关的")
    }

    private fun linux(args: Map<String, Any?>): ToolOutput {
        val script = RonyLinux.script(app).path
        val env = RonyLinux.env(prefs)
        return when (args["action"] as? String ?: "exec") {
            "status" -> text(guarded("agent", "linux", "rony-env status", preset = linuxStatusVerdict) {
                RootExec.run(RonyLinux.command(script, listOf("status"), env) + "; " + RonyLinux.command(script, listOf("preflight"), env), 60_000)
            })
            "exec" -> {
                val command = args["command"] as? String ?: return ToolOutput("exec 需要 command", isError = true)
                val cwd = (args["cwd"] as? String)?.takeIf { it.isNotBlank() } ?: "/root"
                text(guarded("agent", "linux", command) { RootExec.run(RonyLinux.command(script, listOf("exec", command, cwd), env), timeoutOf(args)) })
            }
            "job" -> {
                val command = args["command"] as? String ?: return ToolOutput("job 需要 command", isError = true)
                val cwd = (args["cwd"] as? String)?.takeIf { it.isNotBlank() } ?: "/root"
                var job: RootExec.Job? = null
                val o = guarded("agent", "linux", command) {
                    job = RootExec.startJob(RonyLinux.command(script, listOf("exec", command, cwd), env), null)
                    RootExec.Result(RootExec.State.OK, 0, "", "", false, 0)
                }
                val j = job
                if (j == null) text(o) else ToolOutput("已在 Linux 环境后台启动 job_id=${j.id}。用 mcp__root__job status / output 查看，kill 结束。")
            }
            else -> ToolOutput("action 应为 status | exec | job", isError = true)
        }
    }

    // ── Rendering ──

    private fun render(m: Map<String, Any?>): String = m.entries.filter { it.value != null }.joinToString("\n") { "${it.key}: ${it.value}" }

    private fun renderResult(r: RootExec.Result): String = buildString {
        append("state=${r.state.wire} exit=${r.exitCode} ${r.durationMs}ms")
        if (r.truncated) append("（输出已截断）")
        r.detail?.let { append("\n说明：").append(it) }
        if (r.stdout.isNotEmpty()) append("\n--- stdout ---\n").append(r.stdout.trimEnd())
        if (r.stderr.isNotEmpty()) append("\n--- stderr ---\n").append(r.stderr.trimEnd())
        if (r.stdout.isEmpty() && r.stderr.isEmpty() && r.state == RootExec.State.OK) append("\n（无输出）")
    }

    // l10n:keep agent tool result
    private fun text(o: Outcome): ToolOutput = when (o.decision) {
        "refused" -> ToolOutput("Root 总开关是关的，命令没有执行。", isError = true)
        "blocked" -> ToolOutput("已拦截（${o.reason}）：这类命令永远不执行，审批开关关了也一样。请换别的做法或告诉用户。", isError = true)
        "denied" -> ToolOutput("用户拒绝了这条 Root 命令（${o.reason}）。不要换个写法绕过；换做法或先问用户。", isError = true)
        "denied_timeout" -> ToolOutput("审批 90 秒没人响应，按拒绝处理，命令没有执行。可以请用户留意通知后再试。", isError = true)
        else -> {
            val r = o.result!!
            val prefix = when (o.decision) {
                "approved" -> "[用户已批准] "
                "approved_10min", "approved_window" -> "[10 分钟免审批窗口内] "
                "unapproved_switch_off" -> "[审批开关已关，直接执行] "
                else -> ""
            }
            ToolOutput(prefix + renderResult(r), isError = r.state != RootExec.State.OK && r.state != RootExec.State.TIMEOUT)
        }
    }
}
