package io.github.ccch1mneyyy.dsh_app.rony

import io.github.ccch1mneyyy.dsh_app.runtime.startRootHelper
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Rony fork: runs things as root through the phone's own `su` (Magisk, KernelSU, APatch…).
 * Executes only; [RootCore] decides whether a command may run.
 *
 * Each run feeds a small script to `su` over stdin (nothing in the process arguments). The script
 * first checks `id -u` and prints a random per-run marker (plus the root shell's pid) on stderr,
 * so "su refused" and "the command itself failed" can never be confused: no marker means su never
 * gave us root. The command runs with stdin from /dev/null, so a command that reads stdin can't
 * swallow the rest of the script. On timeout the root shell's whole process tree is killed by a
 * second root call (the app's uid can't signal root processes itself).
 */
object RootExec {
    enum class State(val wire: String) { OK("ok"), DENIED("denied"), NO_SU("no_su"), TIMEOUT("timeout"), ERROR("error") }

    class Result(
        val state: State,
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val truncated: Boolean,
        val durationMs: Long,
        val detail: String? = null,
    ) {
        fun toMap(): Map<String, Any?> = linkedMapOf(
            "state" to state.wire, "exitCode" to exitCode, "stdout" to stdout, "stderr" to stderr,
            "truncated" to truncated, "durationMs" to durationMs, "detail" to detail,
        )
    }

    /** Where su usually lives; KernelSU also answers `/system/bin/su` for granted apps without a file there. */
    val SU_PATHS = listOf(
        "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "/debug_ramdisk/su",
        "/data/adb/ksu/bin/su", "/data/adb/ap/bin/su", "/system_ext/bin/su", "/vendor/bin/su", "/product/bin/su",
    )

    const val DEFAULT_TIMEOUT_MS = 30_000L
    const val MAX_TIMEOUT_MS = 180_000L
    const val MAX_OUTPUT = 48_000

    private val random = SecureRandom()

    fun marker(): String {
        val b = ByteArray(8)
        random.nextBytes(b)
        return "__RONY_" + b.joinToString("") { "%02x".format(it) }
    }

    /** A su binary we can see, or null (KernelSU may still answer at /system/bin/su). */
    fun visibleSu(): String? {
        for (p in SU_PATHS) if (runCatching { File(p).let { it.exists() && it.canExecute() } }.getOrDefault(false)) return p
        val path = System.getenv("PATH").orEmpty().split(':').filter { it.isNotBlank() }
        for (dir in path) {
            val f = File(dir, "su")
            if (runCatching { f.exists() && f.canExecute() }.getOrDefault(false)) return f.path
        }
        return null
    }

    fun suCommand(): String = visibleSu() ?: "su"

    fun quote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    /** The script fed to su; the command sees stdin from /dev/null. */
    fun envelope(marker: String, command: String, cwd: String?): String = buildString {
        append("if [ \"\$(id -u)\" != \"0\" ]; then echo \"${marker}_NOTROOT\" >&2; exit 126; fi\n")
        append("echo \"${marker}_OK \$\$\" >&2\n")
        append("export PATH=\"/sbin:/system/sbin:/system/bin:/system/xbin:/odm/bin:/vendor/bin:/vendor/xbin:/data/adb/ksu/bin:/data/adb/ap/bin:/data/adb/magisk:\$PATH\"\n")
        if (!cwd.isNullOrBlank()) append("cd ${quote(cwd)} || exit 125\n")
        append("{\n").append(command).append("\n} </dev/null\n")
    }

    private class Capture(private val max: Int) {
        val sb = StringBuilder()
        @Volatile var truncated = false
        fun add(chars: CharArray, n: Int) {
            synchronized(sb) {
                val room = max - sb.length
                if (room <= 0) { truncated = true; return }
                if (n > room) { sb.append(chars, 0, room); truncated = true } else sb.append(chars, 0, n)
            }
        }
        fun text(): String = synchronized(sb) { sb.toString() }
    }

    private fun drain(input: InputStream, cap: Capture, name: String, onChunk: ((String) -> Unit)? = null) = thread(name = name, isDaemon = true) {
        runCatching {
            input.bufferedReader(Charsets.UTF_8).use { r ->
                val buf = CharArray(4096)
                while (true) {
                    val n = r.read(buf)
                    if (n < 0) break
                    cap.add(buf, n)
                    onChunk?.invoke(String(buf, 0, n))
                }
            }
        }
    }

    /** Runs [command] as root, blocking up to [timeoutMs]. */
    fun run(command: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS, cwd: String? = null, maxChars: Int = MAX_OUTPUT): Result {
        val started = System.nanoTime()
        fun ms() = (System.nanoTime() - started) / 1_000_000
        val timeout = timeoutMs.coerceIn(500, MAX_TIMEOUT_MS)
        val marker = marker()
        val process = try {
            startRootHelper(listOf(suCommand()), false)
        } catch (e: IOException) {
            return Result(State.NO_SU, -1, "", "", false, ms(), "找不到 su：这台手机没有 Root，或 Root 管理器（KernelSU/Magisk/APatch）还没把本应用加入超级用户名单（${e.message}）")
        }
        val out = Capture(maxChars)
        val err = Capture(maxChars + 256)
        val t1 = drain(process.inputStream, out, "rony-root-out")
        val t2 = drain(process.errorStream, err, "rony-root-err")
        try {
            process.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(envelope(marker, command, cwd)) }
        } catch (_: IOException) {
            // su exited before reading (denied at once): judged by the marker below.
        }
        val finished = process.waitFor(timeout, TimeUnit.MILLISECONDS)
        if (!finished) {
            val pid = rootPid(err.text(), marker)
            if (pid != null) killTree(pid)
            process.destroy()
            if (!process.waitFor(1500, TimeUnit.MILLISECONDS)) process.destroyForcibly()
        }
        t1.join(1500)
        t2.join(1500)
        val rawErr = err.text()
        val ok = rawErr.contains("${marker}_OK")
        val stderr = rawErr.lines().filterNot { it.startsWith(marker) }.joinToString("\n").trimEnd()
        val truncated = out.truncated || err.truncated
        if (!finished) {
            return if (ok) Result(State.TIMEOUT, -1, out.text(), stderr, truncated, ms(), "超时 ${timeout}ms，已结束该命令的进程树")
            else Result(State.TIMEOUT, -1, "", stderr, truncated, ms(), "等待 su 授权超时：请在 Root 管理器里允许本应用后重试")
        }
        val code = process.exitValue()
        if (!ok) {
            val why = when {
                rawErr.contains("${marker}_NOTROOT") -> "su 运行了但没有拿到 uid 0"
                else -> "su 拒绝了（未授权、授权被撤销，或在 Root 管理器里点了拒绝）"
            }
            return Result(State.DENIED, code, "", stderr, truncated, ms(), why)
        }
        return Result(State.OK, code, out.text(), stderr, truncated, ms())
    }

    private fun rootPid(stderr: String, marker: String): Int? =
        stderr.lineSequence().firstOrNull { it.startsWith("${marker}_OK ") }?.substringAfter(' ')?.trim()?.toIntOrNull()

    /** Kills [pid] and all its descendants, as root (best effort, 5 s). */
    fun killTree(pid: Int) {
        val script = "k(){ for c in \$(cat /proc/\$1/task/*/children 2>/dev/null); do k \$c; done; kill -9 \$1 2>/dev/null; }; " +
            "for c in \$(cat /proc/$pid/task/*/children 2>/dev/null); do k \$c; done; pkill -9 -P $pid 2>/dev/null; true"
        runCatching {
            val p = startRootHelper(listOf(suCommand(), "-c", script), true)
            p.outputStream.close()
            thread(isDaemon = true) { runCatching { p.inputStream.readBytes() } }
            if (!p.waitFor(5, TimeUnit.SECONDS)) p.destroyForcibly()
        }
    }

    /** Kills the descendants of [pid] but leaves [pid] itself (a session's shell). */
    fun killChildren(pid: Int) {
        val script = "k(){ for c in \$(cat /proc/\$1/task/*/children 2>/dev/null); do k \$c; done; kill -9 \$1 2>/dev/null; }; " +
            "for c in \$(cat /proc/$pid/task/*/children 2>/dev/null); do k \$c; done; pkill -9 -P $pid 2>/dev/null; true"
        runCatching {
            val p = startRootHelper(listOf(suCommand(), "-c", script), true)
            p.outputStream.close()
            thread(isDaemon = true) { runCatching { p.inputStream.readBytes() } }
            if (!p.waitFor(5, TimeUnit.SECONDS)) p.destroyForcibly()
        }
    }

    // ── Persistent sessions (cwd and environment survive between commands) ─────────────

    class Session(val id: String, private val process: Process, val shellPid: Int, private val lines: LinkedBlockingQueue<String>) {
        val createdAt = System.currentTimeMillis()
        @Volatile var lastUsed = createdAt
        private val lock = Any()
        val alive: Boolean get() = process.isAlive

        fun runCommand(command: String, timeoutMs: Long, maxChars: Int = MAX_OUTPUT): Result { synchronized(lock) {
            val started = System.nanoTime()
            fun ms() = (System.nanoTime() - started) / 1_000_000
            lastUsed = System.currentTimeMillis()
            if (!process.isAlive) return Result(State.ERROR, -1, "", "", false, 0, "会话已结束，请重新 open")
            lines.clear()
            val m = marker()
            try {
                val w = process.outputStream
                w.write("{\n$command\n} </dev/null 2>&1; echo \"${m}_END \$?\"\n".toByteArray(Charsets.UTF_8))
                w.flush()
            } catch (e: IOException) {
                return Result(State.ERROR, -1, "", "", false, ms(), "会话已断开：${e.message}")
            }
            val sb = StringBuilder()
            var truncated = false
            val deadline = System.nanoTime() + timeoutMs.coerceIn(500, MAX_TIMEOUT_MS) * 1_000_000
            var killed = false
            while (true) {
                val left = (deadline - System.nanoTime()) / 1_000_000
                if (left <= 0 && !killed) {
                    killChildren(shellPid)
                    killed = true
                }
                val line = lines.poll(if (killed) 3000 else left.coerceAtLeast(1), TimeUnit.MILLISECONDS)
                if (line == null) {
                    if (!process.isAlive) return Result(State.ERROR, -1, sb.toString(), "", truncated, ms(), "会话的 root shell 退出了（命令里有 exit？）")
                    if (killed) return Result(State.TIMEOUT, -1, sb.toString(), "", truncated, ms(), "超时，已结束子进程，但会话无响应")
                    continue
                }
                if (line.startsWith("${m}_END ")) {
                    val code = line.substringAfter(' ').trim().toIntOrNull() ?: -1
                    return if (killed) Result(State.TIMEOUT, code, sb.toString(), "", truncated, ms(), "超时，已结束子进程；会话仍可用")
                    else Result(State.OK, code, sb.toString(), "", truncated, ms())
                }
                if (sb.length < maxChars) sb.append(line).append('\n') else truncated = true
            }
        } }

        fun close() {
            runCatching { process.outputStream.write("exit\n".toByteArray()); process.outputStream.flush() }
            if (!runCatching { process.waitFor(1, TimeUnit.SECONDS) }.getOrDefault(false)) {
                killTree(shellPid)
                process.destroyForcibly()
            }
        }
    }

    private val sessions = ConcurrentHashMap<String, Session>()
    private val sessionSeq = AtomicInteger()
    const val MAX_SESSIONS = 4

    fun openSession(cwd: String?): Pair<Session?, Result> {
        val started = System.nanoTime()
        fun ms() = (System.nanoTime() - started) / 1_000_000
        sessions.values.filter { !it.alive }.forEach { sessions.remove(it.id) }
        if (sessions.size >= MAX_SESSIONS) {
            sessions.values.minByOrNull { it.lastUsed }?.let { it.close(); sessions.remove(it.id) }
        }
        val process = try {
            startRootHelper(listOf(suCommand()), true)
        } catch (e: IOException) {
            return null to Result(State.NO_SU, -1, "", "", false, ms(), "找不到 su（${e.message}）")
        }
        val lines = LinkedBlockingQueue<String>()
        thread(name = "rony-root-session", isDaemon = true) {
            runCatching { process.inputStream.bufferedReader(Charsets.UTF_8).forEachLine { lines.offer(it) } }
        }
        val m = marker()
        try {
            val w = process.outputStream
            val head = StringBuilder()
            head.append("if [ \"\$(id -u)\" != \"0\" ]; then echo \"${m}_NOTROOT\"; exit 126; fi\n")
            head.append("export PATH=\"/sbin:/system/sbin:/system/bin:/system/xbin:/odm/bin:/vendor/bin:/vendor/xbin:/data/adb/ksu/bin:/data/adb/ap/bin:/data/adb/magisk:\$PATH\"\n")
            if (!cwd.isNullOrBlank()) head.append("cd ${quote(cwd)} 2>&1\n")
            head.append("echo \"${m}_OK \$\$\"\n")
            w.write(head.toString().toByteArray(Charsets.UTF_8))
            w.flush()
        } catch (_: IOException) {
        }
        val deadline = System.nanoTime() + 30_000L * 1_000_000
        while (System.nanoTime() < deadline) {
            val line = lines.poll(250, TimeUnit.MILLISECONDS)
            if (line == null) {
                if (!process.isAlive) break
                continue
            }
            if (line.startsWith("${m}_OK ")) {
                val pid = line.substringAfter(' ').trim().toIntOrNull() ?: -1
                val id = "s" + sessionSeq.incrementAndGet()
                val s = Session(id, process, pid, lines)
                sessions[id] = s
                return s to Result(State.OK, 0, "session_id=$id", "", false, ms())
            }
            if (line.startsWith("${m}_NOTROOT")) break
        }
        process.destroyForcibly()
        return null to Result(State.DENIED, -1, "", "", false, ms(), "su 拒绝或超时，会话未打开")
    }

    fun session(id: String): Session? = sessions[id]?.takeIf { it.alive } ?: run { sessions.remove(id); null }

    fun closeSession(id: String): Boolean = sessions.remove(id)?.let { it.close(); true } ?: false

    fun listSessions(): List<Map<String, Any?>> = sessions.values.map {
        linkedMapOf("session_id" to it.id, "alive" to it.alive, "pid" to it.shellPid, "createdAt" to it.createdAt, "lastUsed" to it.lastUsed)
    }

    fun closeAll() {
        sessions.values.forEach { runCatching { it.close() } }
        sessions.clear()
        jobs.values.filter { it.running }.forEach { runCatching { it.kill() } }
    }

    // ── Background jobs ─────────────────────────────────────────────────────────────

    class Job(val id: String, val command: String) {
        val startedAt = System.currentTimeMillis()
        @Volatile var endedAt: Long? = null
        @Volatile var exitCode: Int? = null
        @Volatile var state: String = "starting"
        @Volatile var pid: Int? = null
        @Volatile var process: Process? = null
        private val buf = StringBuilder()
        private var dropped = 0L
        val running: Boolean get() = endedAt == null

        fun append(s: String) = synchronized(buf) {
            buf.append(s)
            if (buf.length > JOB_BUFFER) {
                val cut = buf.length - JOB_BUFFER
                buf.delete(0, cut)
                dropped += cut
            }
        }

        fun output(tail: Int): Pair<String, Long> = synchronized(buf) {
            val t = tail.coerceIn(1, JOB_BUFFER)
            (if (buf.length > t) buf.substring(buf.length - t) else buf.toString()) to dropped
        }

        fun kill() {
            pid?.let { killTree(it) }
            process?.destroy()
        }

        fun toMap(): Map<String, Any?> = linkedMapOf(
            "job_id" to id, "state" to state, "exitCode" to exitCode, "pid" to pid, "command" to RootPolicy.redact(command),
            "startedAt" to startedAt, "endedAt" to endedAt,
        )
    }

    const val JOB_BUFFER = 64 * 1024
    private val jobs = ConcurrentHashMap<String, Job>()
    private val jobSeq = AtomicInteger()

    fun startJob(command: String, cwd: String?): Job {
        if (jobs.size >= 20) jobs.values.filter { !it.running }.sortedBy { it.startedAt }.take(jobs.size - 19).forEach { jobs.remove(it.id) }
        val job = Job("j" + jobSeq.incrementAndGet(), command)
        jobs[job.id] = job
        val marker = marker()
        thread(name = "rony-root-job", isDaemon = true) {
            val p = try {
                startRootHelper(listOf(suCommand()), true)
            } catch (e: IOException) {
                job.append("找不到 su：${e.message}\n")
                job.state = "no_su"; job.exitCode = -1; job.endedAt = System.currentTimeMillis()
                return@thread
            }
            job.process = p
            val pending = StringBuilder()
            val reader = thread(name = "rony-root-job-out", isDaemon = true) {
                runCatching {
                    p.inputStream.bufferedReader(Charsets.UTF_8).forEachLine { line ->
                        when {
                            line.startsWith("${marker}_OK ") -> { job.pid = line.substringAfter(' ').trim().toIntOrNull(); job.state = "running" }
                            line.startsWith("${marker}_NOTROOT") -> job.state = "denied"
                            else -> job.append(line + "\n")
                        }
                    }
                }
            }
            runCatching {
                val script = envelope(marker, command, cwd).replace(">&2", "").replace("} </dev/null", "} </dev/null 2>&1")
                p.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(script) }
            }
            val code = runCatching { p.waitFor() }.getOrDefault(-1)
            reader.join(2000)
            pending.clear()
            job.exitCode = code
            if (job.state == "running") job.state = "exited"
            else if (job.state == "starting") job.state = "denied"
            job.endedAt = System.currentTimeMillis()
        }
        return job
    }

    fun job(id: String): Job? = jobs[id]
    fun listJobs(): List<Map<String, Any?>> = jobs.values.sortedByDescending { it.startedAt }.map { it.toMap() }
}
