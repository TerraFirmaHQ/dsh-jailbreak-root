package io.github.ccch1mneyyy.dsh_app.hostroot

import io.github.ccch1mneyyy.dsh_app.phone.ToolOutput
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Host tests of the product path. The runner and process starter are fakes. No su. */
class HostRootSpecHostTest {
    @After
    fun restore() {
        RootExec.starter = RootExec.Starter { argv -> ProcessBuilder(argv).start() }
        RootExec.closeAll()
        HostRootControl.onApproval = null
        HostRootControl.clearSessionMemory()
    }

    @Test
    fun `session remember is only offered for a readonly misclassification`() {
        assertNull(CommandClassifier.sessionKey("rm -f /data/x"))
        assertNull(CommandClassifier.sessionKey("foobar --weird"))
        assertTrue(CommandClassifier.classifyCommand("id &").readonly)

        var writePreview = ""
        RootTools.call(
            "shell",
            mapOf("command" to "setprop debug.t 1"),
            { true },
            RootTools.Approver { _, preview, _ -> writePreview = preview; false },
            null,
        ) { _, _, _ -> error("ran") }
        assertFalse(writePreview.contains("@@session=1"))

        var unknownPreview = ""
        RootTools.call(
            "shell",
            mapOf("command" to "foobar"),
            { true },
            RootTools.Approver { _, preview, _ -> unknownPreview = preview; false },
            null,
        ) { _, _, _ -> error("ran") }
        assertFalse(unknownPreview.contains("@@session=1"))

        var readPreview = ""
        RootTools.call(
            "shell",
            mapOf("command" to "id &"),
            { true },
            RootTools.Approver { _, preview, _ -> readPreview = preview; false },
            null,
        ) { _, _, _ -> error("ran") }
        assertFalse(readPreview.contains("@@session=1"))
        assertTrue(readPreview.contains("id &"))
    }

    @Test
    fun `remembered session does not authorize a write and clear drops it`() {
        HostRootControl.onApproval = { event ->
            Thread {
                HostRootControl.resolveApproval(
                    event["id"] as String,
                    true,
                    event["session"] == true,
                )
            }.start()
        }
        assertTrue(HostRootControl.requestApproval("id", "preview", false))
        HostRootControl.clearSessionMemory()
        assertFalse(HostRootControl.hasSessionMemory("id"))

        assertTrue(HostRootControl.requestApproval("rm -f /data/x", "preview\n@@session=1", false))
        assertFalse(HostRootControl.hasSessionMemory("rm"))
        var asked = false
        RootTools.call(
            "shell",
            mapOf("command" to "rm -f /data/x"),
            { true },
            RootTools.Approver { _, _, _ -> asked = true; false },
            null,
            sessionAllows = { true },
        ) { _, _, _ -> error("ran") }
        assertTrue(asked)
    }

    @Test
    fun `timeout result names stage error and next without mixing stderr into stdout`() {
        val out = RootTools.present(
            RootExec.Result(
                RootExec.State.TIMEOUT,
                -1,
                "partial-out",
                "partial-err",
                true,
                180_000,
                "/tmp/spool-timeout",
                "超时 180000ms，已结束该命令的进程树",
            ),
        )
        assertTrue(out.isError)
        assertTrue(out.text.contains("\"stage\":\"timeout\""))
        assertTrue(out.text.contains("\"error\":\"超时 180000ms，已结束该命令的进程树\""))
        assertTrue(out.text.contains("background=true"))
        assertTrue(out.text.contains("\"stdout\":\"partial-out\""))
        assertTrue(out.text.contains("\"stderr\":\"partial-err\""))
        assertFalse(out.text.contains("stderr:\n"))
    }

    @Test
    fun `read file pages at 256KB by default and refuses a limit above 1MB`() {
        val seen = mutableListOf<String>()
        val runner = { command: String, _: Long, _: String? ->
            seen += command
            val stdout = if (command.startsWith("stat")) "3000000\n" else "chunk"
            RootExec.Result(RootExec.State.OK, 0, stdout, "", false, 1, null)
        }
        val paged = RootTools.call(
            "read_file",
            mapOf("path" to "/data/local/tmp/big"),
            { true },
            RootTools.Approver { _, _, _ -> false },
            null,
            runner = runner,
        )
        assertEquals(0, seen.count { it.contains("审批") })
        assertTrue(seen[0].startsWith("stat "))
        assertTrue(seen[1].contains("bs=1 skip=0 count=262144"))
        assertTrue(seen[1].contains("base64"))
        assertTrue(paged.text.contains("\"needChunk\":true"))
        assertTrue(paged.text.contains("\"totalSize\":3000000"))
        assertFalse(paged.isError)

        seen.clear()
        RootTools.call(
            "read_file",
            mapOf("path" to "/data/local/tmp/big", "offset" to 100, "limit" to 2 * 1024 * 1024),
            { true },
            RootTools.Approver { _, _, _ -> false },
            null,
            runner = runner,
        )
        assertTrue(seen.last().contains("bs=1 skip=100 count=1048576"))
    }

    @Test
    fun `stdout over 64KB is summarized and stderr stays in its own field`() {
        val big = "line\n".repeat(20_000)
        assertTrue(big.length > 64 * 1024)
        val out = RootTools.present(
            RootExec.Result(RootExec.State.OK, 0, big, "only-stderr", false, 1, "/tmp/not-a-phone/spool"),
        )
        assertFalse(out.isError)
        assertTrue(out.text.contains("摘要"))
        assertTrue(out.text.contains("\"stderr\":\"only-stderr\""))
        assertTrue(out.text.contains("\"spoolPath\":\"/tmp/not-a-phone/spool\""))
        assertFalse(out.text.contains(big))
    }

    @Test
    fun `spool files older than 90 seconds are deleted and newer ones stay`() {
        val dir = java.nio.file.Files.createTempDirectory("spool-age").toFile()
        val now = 1_700_000_000_000L
        val old = File(dir, "old.log").apply {
            writeText("command-output-should-not-remain")
            setLastModified(now - 120_000)
        }
        val young = File(dir, "young.log").apply {
            writeText("keep")
            setLastModified(now - 10_000)
        }
        HostRootControl.releaseOldSpools(dir, now)
        assertFalse(old.exists())
        assertTrue(young.exists())
    }

    @Test
    fun `finished jobs leave the table 90 seconds later`() {
        RootExec.starter = RootExec.Starter { ExitProcess() }
        val job = RootExec.startJob("id", 2_000, null, null)!!
        val deadline = System.currentTimeMillis() + 3_000
        while (!job.done && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertTrue(job.done)
        assertEquals(job, RootExec.job(job.id))
        RootExec.releaseIdle(job.finishedAt + 91_000_000_000L)
        assertNull(RootExec.job(job.id))
    }

    @Test
    fun `background jobs are capped at two and can be queried and cancelled`() {
        RootExec.starter = RootExec.Starter { HoldProcess() }
        val first = RootExec.startJob("id", 30_000, null, null)
        val second = RootExec.startJob("uptime", 30_000, null, null)
        assertTrue(first != null && second != null)
        assertNull(RootExec.startJob("date", 30_000, null, null))
        val listed = RootTools.call(
            "job",
            mapOf("id" to first!!.id),
            { true },
            RootTools.Approver { _, _, _ -> false },
            null,
        )
        assertTrue(listed.text.contains("\"state\":\"running\""))
        val cancelled = RootTools.call(
            "job",
            mapOf("id" to first.id, "cancel" to true),
            { true },
            RootTools.Approver { _, _, _ -> false },
            null,
        )
        assertTrue(cancelled.text.contains("\"cancelled\":true"))
        assertTrue(RootExec.cancel(second!!.id))
    }

    @Test
    fun `audit line keeps the decision and drops command output`() {
        val line = HostRootControl.auditLine(
            "shell",
            mapOf("command" to "id"),
            ToolOutput("SECRET-STDOUT should not be copied", isError = false),
            time = 10,
        )!!
        assertTrue(line.contains("\"time\":10"))
        assertTrue(line.contains("\"cmd\":"))
        assertTrue(line.contains("\"verdict\":"))
        assertTrue(line.contains("\"reason\":"))
        assertTrue(line.contains("\"choice\":\"allow\""))
        assertFalse(line.contains("SECRET-STDOUT"))
        assertTrue(line.contains("\"exitCode\":"))
        assertFalse(line.contains("\"stdout\":"))
        val denied = HostRootControl.auditLine(
            "shell",
            mapOf("command" to "rm -f /data/x"),
            ToolOutput(RootTools.DENIED + "：没有执行。", isError = true),
            time = 11,
        )!!
        assertTrue(denied.contains("\"choice\":\"deny\""))
        assertNull(HostRootControl.auditLine("job", emptyMap(), ToolOutput("x"), 1))
    }

    @Test
    fun `auto allow skips the dialog and root off still does not call su`() {
        var asked = false
        var ran = false
        val out = RootTools.call(
            "shell",
            mapOf("command" to "setprop debug.t 1"),
            { true },
            RootTools.Approver { _, _, _ -> asked = true; false },
            null,
            autoAllow = { true },
        ) { _, _, _ ->
            ran = true
            RootExec.Result(RootExec.State.OK, 0, "", "", false, 1, null)
        }
        assertFalse(asked)
        assertTrue(ran)
        assertTrue(out.text.contains("\"approval\":\"auto\""))
        var called = false
        val off = RootTools.call(
            "shell",
            mapOf("command" to "id"),
            { false },
            RootTools.Approver { _, _, _ -> true },
            null,
            autoAllow = { true },
        ) { _, _, _ ->
            called = true
            error("su")
        }
        assertFalse(called)
        assertTrue(off.text.contains(RootTools.DISABLED))
    }

    @Test
    fun `utf8 pages reassemble chinese and emoji and move past a broken offset`() {
        val text = "你好🙂"
        val bytes = text.toByteArray(Charsets.UTF_8)
        var offset = 0
        val rebuilt = StringBuilder()
        while (offset < bytes.size) {
            val slice = RootFiles.utf8Slice(bytes.copyOfRange(offset, bytes.size).copyOf(4.coerceAtMost(bytes.size - offset)))
            assertTrue(slice.consumed > 0)
            rebuilt.append(slice.text)
            offset += slice.consumed
        }
        assertEquals(text, rebuilt.toString())
        val mid = RootFiles.utf8Slice(bytes.copyOfRange(1, bytes.size))
        assertFalse(mid.text.contains("\uFFFD") && mid.consumed == 0)
        assertTrue(mid.consumed >= 1)
        val bad = RootFiles.utf8Slice(byteArrayOf(0xFF.toByte(), 'A'.code.toByte()))
        assertTrue(bad.text.contains('\uFFFD'))
        assertEquals(2, bad.consumed)
    }

    @Test
    fun `engine start source has no chroot launch script`() {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        var found: File? = null
        var left = 6
        while (dir != null && left-- > 0 && found == null) {
            val candidate = File(dir, "app/android/app/src/main/kotlin/io/github/ccch1mneyyy/dsh_app/runtime/DshProcess.kt")
            if (candidate.isFile) found = candidate
            dir = dir.parentFile
        }
        val source = found?.readText() ?: error("DshProcess.kt")
        assertFalse(source.contains("chroot-launch.sh"))
        assertFalse(source.contains("launch method: root-chroot"))
    }

    private class HoldProcess : Process() {
        private val open = AtomicBoolean(true)
        override fun getOutputStream() = ByteArrayOutputStream()
        override fun getInputStream(): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
        override fun getErrorStream(): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
        override fun waitFor(): Int {
            while (open.get()) Thread.sleep(20)
            return 0
        }
        override fun exitValue(): Int {
            if (open.get()) throw IllegalThreadStateException()
            return 0
        }
        override fun destroy() {
            open.set(false)
        }
    }

    private class ExitProcess : Process() {
        override fun getOutputStream() = ByteArrayOutputStream()
        override fun getInputStream(): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
        override fun getErrorStream(): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
        override fun waitFor(): Int = 0
        override fun exitValue(): Int = 0
        override fun destroy() {}
    }
}
