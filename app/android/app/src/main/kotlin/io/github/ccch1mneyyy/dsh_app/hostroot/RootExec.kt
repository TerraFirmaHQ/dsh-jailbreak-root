package io.github.ccch1mneyyy.dsh_app.hostroot

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Runs one command as host uid 0 through `su`.
 *
 * The command is fed on stdin, not placed in the process arguments. A per-run
 * marker on stderr separates "su refused" from "the command failed". On timeout
 * the recorded root pid's tree is killed by a second, targeted `su -c`.
 * Output is spooled to a file and the tool reply keeps a bounded tail.
 *
 * Ideas adapted from ghjicb/dsh-Go (AGPL-3.0): real `su -c`, output spooling,
 * timeout, and process-tree cleanup. There is no approval bypass in this class;
 * [RootTools] decides whether [run] may be called.
 */
object RootExec {
    enum class State { OK, DENIED, NO_SU, TIMEOUT, ERROR }

    data class Result(
        val state: State,
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val truncated: Boolean,
        val durationMs: Long,
        val spool: String?,
        val detail: String? = null,
    )

    fun interface Starter {
        fun start(argv: List<String>): Process
    }

    @Volatile var starter: Starter = Starter { argv -> ProcessBuilder(argv).start() }

    private val generation = AtomicInteger(0)
    private val livePids = ConcurrentHashMap.newKeySet<Int>()

    /** Drops root and rejects work still running or waiting. */
    fun revoke() {
        generation.incrementAndGet()
        val pids = livePids.toList()
        livePids.clear()
        pids.filter { it > 0 }.forEach { killTree(it) }
    }

    fun generation(): Int = generation.get()

    const val DEFAULT_TIMEOUT_MS = 30_000L
    const val MAX_TIMEOUT_MS = 180_000L
    const val TAIL = 48_000
    const val SPOOL_MAX = 1_048_576

    private val random = SecureRandom()
    private val jobs = ConcurrentHashMap<String, Job>()

    fun marker(): String {
        val b = ByteArray(8)
        random.nextBytes(b)
        return "__DSH_" + b.joinToString("") { "%02x".format(it) }
    }

    fun quote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    /** Script consumed by `su`. The command's stdin is /dev/null. */
    fun envelope(marker: String, command: String, cwd: String?): String = buildString {
        append("if [ \"\$(id -u)\" != \"0\" ]; then echo \"${marker}_NOTROOT\" >&2; exit 126; fi\n")
        append("echo \"${marker}_OK \$\$\" >&2\n")
        append("export PATH=\"/sbin:/system/sbin:/system/bin:/system/xbin:/odm/bin:/vendor/bin:/vendor/xbin:/data/adb/ksu/bin:/data/adb/ap/bin:/data/adb/magisk:\$PATH\"\n")
        if (!cwd.isNullOrBlank()) append("cd ${quote(cwd)} || exit 125\n")
        append("{\n").append(command).append("\n} </dev/null\n")
    }

    fun killScript(pid: Int): String {
        require(pid > 0) { "pid" }
        return "k(){ for c in \$(cat /proc/\$1/task/*/children 2>/dev/null); do k \$c; done; kill -9 \$1 2>/dev/null; }; " +
            "for c in \$(cat /proc/$pid/task/*/children 2>/dev/null); do k \$c; done; pkill -9 -P $pid 2>/dev/null; kill -9 $pid 2>/dev/null; true"
    }

    /** The first marker pid. A later forged line does not replace it. Zero and negative are ignored. */
    fun rootPid(stderr: String, marker: String): Int? {
        val line = stderr.lineSequence().firstOrNull { it.startsWith("${marker}_OK ") } ?: return null
        val pid = line.substringAfter(' ').trim().toIntOrNull() ?: return null
        return pid.takeIf { it > 0 }
    }

    fun run(
        command: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        cwd: String? = null,
        spoolDir: File? = null,
    ): Result {
        val gen = generation.get()
        val started = System.nanoTime()
        fun ms() = (System.nanoTime() - started) / 1_000_000
        val timeout = timeoutMs.coerceIn(500, MAX_TIMEOUT_MS)
        val marker = marker()
        val spool = spoolDir?.let { dir ->
            dir.mkdirs()
            File(dir, "spool-${marker.substringAfter('_')}.log")
        }
        val process = try {
            starter.start(listOf("su"))
        } catch (e: IOException) {
            return Result(State.NO_SU, -1, "", "", false, ms(), null, "找不到 su：${e.message}")
        }
        val out = Spool(SPOOL_MAX)
        val err = Spool(SPOOL_MAX)
        val t1 = drain(process.inputStream, "dsh-root-out") { out.add(it) }
        val t2 = drain(process.errorStream, "dsh-root-err") { err.add(it) }
        try {
            process.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(envelope(marker, command, cwd)) }
        } catch (_: IOException) {
        }
        val deadline = System.nanoTime() + timeout * 1_000_000
        var finished = false
        var revoked = false
        while (System.nanoTime() < deadline) {
            val pid = rootPid(err.text(), marker)
            if (pid != null) livePids.add(pid)
            if (generation.get() != gen) {
                revoked = true
                pid?.let { killTree(it); livePids.remove(it) }
                process.destroyForcibly()
                break
            }
            if (process.waitFor(200, TimeUnit.MILLISECONDS)) {
                finished = true
                break
            }
        }
        if (!finished && !revoked) {
            rootPid(err.text(), marker)?.let { killTree(it); livePids.remove(it) }
            process.destroy()
            if (!process.waitFor(1500, TimeUnit.MILLISECONDS)) process.destroyForcibly()
        }
        t1.join(1500)
        t2.join(1500)
        val rawErr = err.text()
        val ok = rawErr.contains("${marker}_OK")
        val stderr = rawErr.lines().filterNot { it.startsWith(marker) }.joinToString("\n").trimEnd()
        val stdout = out.text()
        val truncated = out.truncated || err.truncated
        rootPid(rawErr, marker)?.let { livePids.remove(it) }
        spool?.writeText(stdout)
        if (revoked) {
            return Result(State.ERROR, -1, tail(stdout), tail(stderr), truncated || stdout.length > TAIL, ms(), spool?.path, "ROOT_REVOKED")
        }
        if (!finished) {
            val state = if (ok) State.TIMEOUT else State.DENIED
            val detail = if (ok) "超时 ${timeout}ms，已结束该命令的进程树" else "等待 su 授权超时"
            return Result(state, -1, if (ok) tail(stdout) else "", tail(stderr), truncated || stdout.length > TAIL, ms(), spool?.path, detail)
        }
        val code = process.exitValue()
        if (!ok) {
            return Result(State.DENIED, code, "", tail(stderr), truncated, ms(), null, "su 没有给出 uid 0")
        }
        return Result(State.OK, code, tail(stdout), tail(stderr), truncated || stdout.length > TAIL, ms(), spool?.path)
    }

    fun killTree(pid: Int) {
        runCatching {
            val p = starter.start(listOf("su", "-c", killScript(pid)))
            p.outputStream.close()
            thread(isDaemon = true) { runCatching { p.inputStream.readBytes() } }
            if (!p.waitFor(5, TimeUnit.SECONDS)) p.destroyForcibly()
        }
    }

    class Job(val id: String, val command: String) {
        @Volatile var result: Result? = null
        @Volatile var done = false
    }

    fun startJob(command: String, timeoutMs: Long, cwd: String?, spoolDir: File?): Job {
        val job = Job(marker(), command)
        jobs[job.id] = job
        thread(name = "dsh-root-job", isDaemon = true) {
            job.result = run(command, timeoutMs, cwd, spoolDir)
            job.done = true
        }
        return job
    }

    fun job(id: String): Job? = jobs[id]

    fun closeAll() {
        jobs.clear()
    }

    private fun tail(s: String) = if (s.length > TAIL) s.takeLast(TAIL) else s

    private fun drain(input: InputStream, name: String, onChunk: (String) -> Unit) = thread(name = name, isDaemon = true) {
        runCatching {
            input.bufferedReader(Charsets.UTF_8).use { r ->
                val buf = CharArray(4096)
                while (true) {
                    val n = r.read(buf)
                    if (n < 0) break
                    onChunk(String(buf, 0, n))
                }
            }
        }
    }

    /** One writer lock so the stdout and stderr pumps can append safely. */
    class Spool(private val max: Int) {
        private val buf = StringBuilder()
        @Volatile var truncated = false
            private set

        fun add(chunk: String) {
            synchronized(buf) {
                val room = max - buf.length
                if (room <= 0) { truncated = true; return }
                if (chunk.length > room) { buf.append(chunk, 0, room); truncated = true } else buf.append(chunk)
            }
        }

        fun text(): String = synchronized(buf) { buf.toString() }
    }
}
