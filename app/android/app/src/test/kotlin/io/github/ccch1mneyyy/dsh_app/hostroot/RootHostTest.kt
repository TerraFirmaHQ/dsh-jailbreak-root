package io.github.ccch1mneyyy.dsh_app.hostroot

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class RootPolicyTest {
    @Test
    fun `data adb read is not banned`() {
        val v = RootPolicy.classify("cat /data/adb/modules/zygisksu/module.prop")
        assertEquals(RootPolicy.Level.READ, v.level)
        assertEquals(RootPolicy.Level.READ, RootPolicy.classify("ls /data/adb").level)
    }

    @Test
    fun `data adb write is protected and still allowed to be requested`() {
        val v = RootPolicy.classify("echo x > /data/adb/modules/hma/module.prop")
        assertEquals(RootPolicy.Level.PROTECTED, v.level)
        assertTrue(v.needsApproval)
        assertTrue(RootPolicy.preview("echo x > /data/adb/modules/hma/module.prop", v).contains("/data/adb"))
    }

    @Test
    fun `user executables and output flags are not harmless reads`() {
        assertFalse(RootPolicy.classify("/data/local/tmp/cat /data/adb").level == RootPolicy.Level.READ)
        assertFalse(RootPolicy.classify("./ls /data/adb").level == RootPolicy.Level.READ)
        assertFalse(RootPolicy.classify("sort -o /tmp/out /etc/hosts").level == RootPolicy.Level.READ)
        assertFalse(RootPolicy.classify("ls -o /data/adb").level == RootPolicy.Level.READ)
        assertFalse(RootPolicy.classify("logcat -f /data/local/tmp/log").level == RootPolicy.Level.READ)
        assertFalse(RootPolicy.classify("cat --output=/tmp/x /data/adb/a").level == RootPolicy.Level.READ)
    }

    @Test
    fun `state changing dumpsys and cmd are not harmless reads`() {
        assertFalse(RootPolicy.classify("dumpsys battery set level 5").level == RootPolicy.Level.READ)
        assertFalse(RootPolicy.classify("dumpsys deviceidle whitelist +io.github.ronyfork.dsh_app").level == RootPolicy.Level.READ)
        assertFalse(RootPolicy.classify("cmd package install /data/local/tmp/a.apk").level == RootPolicy.Level.READ)
        assertFalse(RootPolicy.classify("cmd battery unplug").level == RootPolicy.Level.READ)
        assertEquals(RootPolicy.Level.READ, RootPolicy.classify("dumpsys battery").level)
        assertEquals(RootPolicy.Level.READ, RootPolicy.classify("cmd package list packages").level)
    }

    @Test
    fun `setenforce is protected and a normal write is only an approval`() {
        assertEquals(RootPolicy.Level.PROTECTED, RootPolicy.classify("setenforce 0").level)
        assertEquals(RootPolicy.Level.APPROVAL, RootPolicy.classify("settings put global stay_on_while_plugged_in 1").level)
    }
}

class RootFilesTest {
    @Test
    fun `write_file keeps hostile bytes out of the shell and round trips them`() {
        val payload = (
            "DSH_EOF\n$(reboot)\n'; rm -rf /; echo owned\n`id`\n"
            ).toByteArray(Charsets.UTF_8) + byteArrayOf(0, 1, 255.toByte())
        val path = "/data/adb/modules/hma/it's a file"
        val script = RootFiles.writeCommand(path, payload)
        assertFalse(script.contains("DSH_EOF"))
        assertFalse(script.contains("$(reboot)"))
        assertFalse(script.contains("rm -rf"))
        assertTrue(script.contains("base64 -d"))
        assertArrayEquals(payload, RootFiles.payload(script))
        assertTrue(script.contains("'/data/adb/modules/hma/it'\\''s a file'"))
    }

    @Test
    fun `read command quotes the path and does not interpolate it`() {
        val script = RootFiles.readCommand("/data/adb/modules/zygisksu/module.prop")
        assertTrue(script.startsWith("dd if='/data/adb/modules/zygisksu/module.prop'"))
        assertNull(RootFiles.rejectPath("/data/adb/x"))
        assertNotNull(RootFiles.rejectPath("relative"))
        assertNotNull(RootFiles.rejectPath("/tmp/a\n;id"))
    }
}

class RootToolsGateTest {
    @After
    fun restore() {
        RootExec.starter = RootExec.Starter { argv -> ProcessBuilder(argv).start() }
    }

    @Test
    fun `root off never calls su`() {
        var called = false
        val out = RootTools.call("shell", mapOf("command" to "id"), { false }, approveAll, null) { _, _, _ ->
            called = true
            error("su")
        }
        assertFalse(called)
        assertTrue(out.isError)
        assertTrue(out.text.contains(RootTools.DISABLED))
    }

    @Test
    fun `arbitrary shell always asks even when the text looks read only`() {
        var asked: String? = null
        var ran: String? = null
        val out = RootTools.call(
            "shell",
            mapOf("command" to "cat /data/adb/modules/hma/module.prop", "approved" to true, "force" to true),
            { true },
            RootTools.Approver { command, _, _ -> asked = command; false },
            null,
        ) { command, _, _ -> ran = command; ok() }
        assertEquals("cat /data/adb/modules/hma/module.prop", asked)
        assertNull(ran)
        assertTrue(out.text.contains(RootTools.DENIED))
    }

    @Test
    fun `structured read of data adb does not ask and write of it does`() {
        var asked = 0
        var ran: String? = null
        val read = RootTools.call(
            "read_file",
            mapOf("path" to "/data/adb/modules/zygisksu/module.prop"),
            { true },
            RootTools.Approver { _, _, _ -> asked += 1; false },
            null,
        ) { command, _, _ -> ran = command; ok() }
        assertEquals(0, asked)
        assertTrue(ran!!.contains("/data/adb/modules/zygisksu/module.prop"))
        assertFalse(read.isError)

        ran = null
        val write = RootTools.call(
            "write_file",
            mapOf("path" to "/data/adb/modules/hma/module.prop", "content" to "DSH_EOF\n$(id)\n"),
            { true },
            RootTools.Approver { _, preview, protected ->
                asked += 1
                assertTrue(protected)
                assertTrue(preview.contains("隐藏") || preview.contains("/data/adb") || preview.isNotEmpty())
                false
            },
            null,
        ) { _, _, _ -> error("executed") }
        assertEquals(1, asked)
        assertTrue(write.isError)
        assertNull(ran)
    }

    @Test
    fun `accepted write uses the base64 script and not the raw payload`() {
        var ran: String? = null
        val hostile = "DSH_EOF\n$(reboot)\n"
        RootTools.call(
            "write_file",
            mapOf("path" to "/sdcard/Download/note.txt", "content" to hostile),
            { true },
            approveAll,
            null,
        ) { command, _, _ ->
            ran = command
            ok()
        }
        val script = ran!!
        assertFalse(script.contains("DSH_EOF"))
        assertFalse(script.contains("$(reboot)"))
        assertArrayEquals(hostile.toByteArray(), RootFiles.payload(script))
    }

    @Test
    fun `revoking root during approval does not run the command`() {
        var enabled = true
        var ran = false
        val out = RootTools.call(
            "shell",
            mapOf("command" to "setenforce 0"),
            { enabled },
            RootTools.Approver { _, _, protected ->
                assertTrue(protected)
                enabled = false
                true
            },
            null,
        ) { _, _, _ -> ran = true; ok() }
        assertFalse(ran)
        assertTrue(out.isError)
    }

    private val approveAll = RootTools.Approver { _, _, _ -> true }

    private fun ok() = RootExec.Result(RootExec.State.OK, 0, "", "", false, 1, null)
}

class ApprovalBoardTest {
    @Test
    fun `concurrent approvals bind the exact command`() {
        val board = ApprovalBoard()
        val a = board.begin("echo one", "preview-one", false)
        val b = board.begin("echo two", "preview-two", true)
        assertEquals("echo one", board.pendingCommand(a))
        assertEquals("echo two", board.pendingCommand(b))
        assertFalse(board.resolve("missing", true))
        val first = thread { assertTrue(board.await(a, "echo one", 2_000)) }
        val wrong = thread { assertFalse(board.await(a, "echo two", 200)) }
        wrong.join(1_000)
        assertTrue(board.resolve(a, true))
        first.join(2_000)
        assertTrue(board.resolve(b, false))
        assertFalse(board.await(b, "echo two", 1_000))
    }

    @Test
    fun `revoke fails a pending approval and a later resolve cannot accept it`() {
        val board = ApprovalBoard()
        val id = board.begin("rm /data/adb/modules/x", "preview", true)
        val waiting = thread { assertFalse(board.await(id, "rm /data/adb/modules/x", 2_000)) }
        board.revoke()
        waiting.join(2_000)
        assertFalse(board.resolve(id, true))
    }
}

class RootExecSafetyTest {
    private val original = RootExec.starter

    @After
    fun restore() {
        RootExec.starter = original
    }

    @Test
    fun `timeout kill uses the marker pid and ignores a later forged pid`() {
        val marker = "__DSH_abc"
        val stderr = "noise\n${marker}_OK 4242\n${marker}_OK 999\n${marker}_OK 0\n"
        assertEquals(4242, RootExec.rootPid(stderr, marker))
        assertNull(RootExec.rootPid("${marker}_OK 0\n", marker))
        assertNull(RootExec.rootPid("${marker}_OK -5\n", marker))
        val script = RootExec.killScript(4242)
        assertTrue(script.contains("4242"))
        assertFalse(script.contains("999"))
    }

    @Test
    fun `revoke while a command is running kills that pid`() {
        val argv = Collections.synchronizedList(mutableListOf<List<String>>())
        RootExec.starter = RootExec.Starter { args ->
            argv.add(args)
            if (args.size == 1 && args[0] == "su") HangProcess() else DoneProcess()
        }
        val worker = thread {
            val result = RootExec.run("sleep 30", 8_000)
            assertEquals("ROOT_REVOKED", result.detail)
        }
        Thread.sleep(500)
        RootExec.revoke()
        worker.join(5_000)
        assertTrue(worker.isAlive.not())
        val kill = argv.firstOrNull { it.getOrNull(1) == "-c" }?.getOrNull(2).orEmpty()
        assertTrue(kill.contains("4242"))
    }

    @Test
    fun `spool appends are thread safe`() {
        val spool = RootExec.Spool(50_000)
        val n = AtomicInteger()
        val threads = (1..8).map {
            thread {
                repeat(200) { spool.add("abcdefghij") }
                n.incrementAndGet()
            }
        }
        threads.forEach { it.join(2_000) }
        assertEquals(8, n.get())
        assertEquals(8 * 200 * 10, spool.text().length)
        assertFalse(spool.truncated)
    }
}

class PromptMountTest {
    @Test
    fun `prompt hash matches the shipped 4_1 file`() {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        var prompt: File? = null
        var left = 6
        while (dir != null && left-- > 0 && prompt == null) {
            prompt = listOf(
                File(dir, "app/assets/engine/hostprompt/prompt.md"),
                File(dir, "assets/engine/hostprompt/prompt.md"),
            ).firstOrNull { it.isFile }
            dir = dir.parentFile
        }
        val found = prompt ?: error("prompt.md not found from ${System.getProperty("user.dir")}")
        val bytes = found.readBytes()
        assertEquals(HostRootGuest.EXPECTED_PROMPT_SHA256, HostRootGuest.sha256(bytes))
        assertEquals(21403, bytes.size)
    }

    @Test
    fun `mount log says whether the patch is on the launch command`() {
        assertEquals(
            "[dsh-app] locked prompt mounted (first system segment)",
            HostRootGuest.mountLog("locked prompt", true, true, "first system segment", "asset missing or hash rejected"),
        )
        assertEquals(
            "[dsh-app] locked prompt not mounted (asset missing or hash rejected)",
            HostRootGuest.mountLog("locked prompt", false, false, "first system segment", "asset missing or hash rejected"),
        )
        assertEquals(
            "[dsh-app] root tools not mounted (switch off or safe mode, su not called)",
            HostRootGuest.mountLog("root tools", false, false, "mcp__root__*", "switch off or safe mode, su not called"),
        )
        assertEquals(
            "[dsh-app] root tools mounted (mcp__root__*)",
            HostRootGuest.mountLog("root tools", true, true, "mcp__root__*", "switch off or safe mode, su not called"),
        )
        assertEquals(
            "[dsh-app] persona not mounted (blank, no section)",
            HostRootGuest.mountLog("persona", false, false, "after the locked prompt", "blank, no section"),
        )
    }

    @Test
    fun `prompt patch is inserted for normal and safe commands and root is not`() {
        val normal = "cd / && /usr/local/bin/dsh web --host 127.0.0.1 --port 3080"
        val safe = "cd / && /usr/local/bin/dsh web --profile safe --from-default-profile web --patch /etc/dsh-app/dsh-web.patch.yml --host 127.0.0.1 --port 3080"
        val normalPatched = HostRootGuest.launchArg(normal, HostRootGuest.PROMPT_PATCH, allowProfile = true)
        val safePatched = HostRootGuest.launchArg(safe, HostRootGuest.PROMPT_PATCH, allowProfile = true)
        assertNotNull(normalPatched)
        assertNotNull(safePatched)
        assertTrue(normalPatched!!.contains("--patch ${HostRootGuest.PROMPT_PATCH}"))
        assertTrue(safePatched!!.contains("--patch ${HostRootGuest.PROMPT_PATCH}"))
        assertNull(HostRootGuest.launchArg(safe, HostRootGuest.ROOT_PATCH, allowProfile = false))
        assertNotNull(HostRootGuest.launchArg(normal, HostRootGuest.ROOT_PATCH, allowProfile = false))
    }

    @Test
    fun `missing or altered prompt is not invented and blank persona adds nothing`() {
        val root = java.nio.file.Files.createTempDirectory("prompt").toFile()
        assertFalse(HostRootGuest.writePrompt(root) { name -> if (name == "prompt.md") ByteArray(0) else "x".toByteArray() })
        assertFalse(HostRootGuest.promptPatch(root).exists())
        assertFalse(HostRootGuest.writePrompt(root) { "not-the-prompt".toByteArray() })
        assertFalse(HostRootGuest.writePersona(root, "   ", "plugin".toByteArray()))
        assertFalse(HostRootGuest.personaPatch(root).exists())
        assertTrue(HostRootGuest.writePersona(root, "only this", "export {}".toByteArray()))
        assertTrue(HostRootGuest.personaPatch(root).exists())
    }
}

class RootApprovalChainTest {
    @After
    fun clearListener() {
        HostRootControl.onApproval = null
    }

    @Test
    fun `simulated su write of data adb waits for the UI event then runs`() {
        val seen = CountDownLatch(1)
        var event: Map<String, Any?>? = null
        HostRootControl.onApproval = {
            event = it
            seen.countDown()
        }
        var ran: String? = null
        val worker = thread {
            val out = RootTools.call(
                "write_file",
                mapOf("path" to "/data/adb/modules/hma/module.prop", "content" to "enabled=1\n"),
                { true },
                RootTools.Approver { command, preview, protected ->
                    HostRootControl.requestApproval(command, preview, protected)
                },
                null,
            ) { command, _, _ ->
                ran = command
                RootExec.Result(RootExec.State.OK, 0, "SIMULATED_NOT_A_PHONE uid=0\n", "", false, 1, null)
            }
            assertFalse(out.isError)
            assertTrue(out.text.contains("SIMULATED_NOT_A_PHONE"))
        }
        assertTrue(seen.await(3, TimeUnit.SECONDS))
        val ev = event!!
        assertEquals("approval", ev["type"])
        assertEquals(true, ev["protected"])
        val preview = ev["preview"] as String
        assertTrue(preview.contains("/data/adb"))
        assertTrue(preview.contains("隐藏") || preview.contains("模块"))
        assertTrue(HostRootControl.resolveApproval(ev["id"] as String, true))
        worker.join(3_000)
        assertFalse(worker.isAlive)
        val script = ran!!
        assertTrue(script.contains("/data/adb/modules/hma/module.prop"))
        assertTrue(script.contains("base64 -d"))
        assertFalse(script.contains("enabled=1"))
    }

    @Test
    fun `ordinary write is not read only and a denied one does not run`() {
        val seen = CountDownLatch(1)
        var event: Map<String, Any?>? = null
        HostRootControl.onApproval = {
            event = it
            seen.countDown()
        }
        var ran = false
        val worker = thread {
            val out = RootTools.call(
                "write_file",
                mapOf("path" to "/sdcard/Download/note.txt", "content" to "hello"),
                { true },
                RootTools.Approver { command, preview, protected ->
                    HostRootControl.requestApproval(command, preview, protected)
                },
                null,
            ) { _, _, _ ->
                ran = true
                error("simulated su should not run")
            }
            assertTrue(out.isError)
            assertTrue(out.text.contains(RootTools.DENIED))
        }
        assertTrue(seen.await(3, TimeUnit.SECONDS))
        val ev = event!!
        assertEquals(false, ev["protected"])
        assertTrue((ev["preview"] as String).contains("uid 0"))
        assertTrue(HostRootControl.resolveApproval(ev["id"] as String, false))
        worker.join(3_000)
        assertFalse(worker.isAlive)
        assertFalse(ran)
    }

    @Test
    fun `root off never reaches approval or the simulated su runner`() {
        var asked = false
        var ran = false
        HostRootControl.onApproval = { asked = true }
        val out = RootTools.call(
            "write_file",
            mapOf("path" to "/data/adb/x", "content" to "nope"),
            { false },
            RootTools.Approver { _, _, _ -> asked = true; true },
            null,
        ) { _, _, _ ->
            ran = true
            error("su")
        }
        assertFalse(asked)
        assertFalse(ran)
        assertTrue(out.text.contains(RootTools.DISABLED))
    }

    @Test
    fun `root overlay is a patch file and clearing it removes the mount`() {
        val root = java.nio.file.Files.createTempDirectory("root-overlay").toFile()
        val endpoint = io.github.ccch1mneyyy.dsh_app.phone.PhoneGuest.Endpoint(null, 9, "simulated-token")
        assertTrue(HostRootGuest.writeRoot(root, endpoint))
        val patch = HostRootGuest.rootPatch(root)
        val text = patch.readText()
        assertTrue(text.contains("serverName: root"))
        assertTrue(text.contains("@deepseek-ai/dsh-mcp-client"))
        HostRootGuest.clearRoot(root)
        assertFalse(patch.exists())
    }
}

class MigrationAndChrootTest {
    @Test
    fun `preflight records root keep and usr local opt drop and schedule downgrade`() {
        val rootfs = java.nio.file.Files.createTempDirectory("rootfs").toFile()
        File(rootfs, "root/.dsh").mkdirs()
        File(rootfs, "root/.dsh/note").writeText("kept")
        File(rootfs, "usr/local/bin").mkdirs()
        File(rootfs, "opt/dsh").mkdirs()
        val dest = java.nio.file.Files.createTempDirectory("mig").toFile()
        val report = MigrationPreflight.capture(rootfs, dest)
        assertTrue(report.plan.keep.contains("/root"))
        assertTrue(report.plan.drop.contains("/usr/local"))
        assertTrue(report.plan.drop.contains("/opt"))
        assertTrue(report.plan.scheduleDowngrade.contains("0.2.2"))
        assertTrue(report.backedUp)
        assertTrue(File(dest, "NOTES.txt").readText().contains("/usr/local"))
        assertEquals("kept", File(dest, "root-dsh/note").readText())
    }

    @Test
    fun `chroot stays off unless requested granted and not safe`() {
        val root = File("/tmp/does-not-need-to-exist")
        assertFalse(io.github.ccch1mneyyy.dsh_app.runtime.ChrootLaunch.select(root, false, false, false, true).root)
        assertFalse(io.github.ccch1mneyyy.dsh_app.runtime.ChrootLaunch.select(root, true, true, false, true).root)
        assertFalse(io.github.ccch1mneyyy.dsh_app.runtime.ChrootLaunch.select(root, true, false, false, false).root)
    }

    @Test
    fun `adoption requires identity and phone reconnect follows the record`() {
        val rootfs = java.nio.file.Files.createTempDirectory("adopt").toFile()
        val record = io.github.ccch1mneyyy.dsh_app.runtime.EngineHandoff.Record(
            pid = 9, port = 3080, token = "t", rootfs = rootfs.canonicalPath, phone = false,
        )
        val fresh = io.github.ccch1mneyyy.dsh_app.runtime.ChrootSession.consider(
            null, rootfs, 1, true, { io.github.ccch1mneyyy.dsh_app.runtime.AdoptedEngine.Liveness.Absent },
            io.github.ccch1mneyyy.dsh_app.runtime.AdoptedEngine.HealthCheck { _, _ -> true },
        )
        assertTrue(fresh is io.github.ccch1mneyyy.dsh_app.runtime.ChrootSession.Outcome.StartFresh)
        val adopted = io.github.ccch1mneyyy.dsh_app.runtime.ChrootSession.consider(
            record, rootfs, 7, true,
            {
                io.github.ccch1mneyyy.dsh_app.runtime.AdoptedEngine.Liveness.Alive(
                    7, "/opt/node/bin/node", rootfs.canonicalPath + "/root",
                )
            },
            io.github.ccch1mneyyy.dsh_app.runtime.AdoptedEngine.HealthCheck { _, _ -> true },
        ) as io.github.ccch1mneyyy.dsh_app.runtime.ChrootSession.Outcome.Adopt
        assertTrue(adopted.phone.notice)
        assertFalse(adopted.phone.reconnect)
        val withPhone = io.github.ccch1mneyyy.dsh_app.runtime.ChrootSession.consider(
            record.copy(phone = true), rootfs, 7, true,
            {
                io.github.ccch1mneyyy.dsh_app.runtime.AdoptedEngine.Liveness.Alive(
                    7, "/opt/node/bin/node", rootfs.canonicalPath + "/root",
                )
            },
            io.github.ccch1mneyyy.dsh_app.runtime.AdoptedEngine.HealthCheck { _, _ -> true },
        ) as io.github.ccch1mneyyy.dsh_app.runtime.ChrootSession.Outcome.Adopt
        assertTrue(withPhone.phone.reconnect)
        assertFalse(withPhone.phone.notice)
    }

    @Test
    fun `proc unknown does not call su unless a fallback reader is supplied`() {
        var su = 0
        val missing = io.github.ccch1mneyyy.dsh_app.runtime.ChrootSession.liveness(4, { null }, null)
        assertTrue(missing is io.github.ccch1mneyyy.dsh_app.runtime.AdoptedEngine.Liveness.Absent)
        assertEquals(0, su)
        val unknown = io.github.ccch1mneyyy.dsh_app.runtime.ChrootSession.liveness(4, { path ->
            if (path == "/proc/4") "" else null
        }) { path ->
            su += 1
            when {
                path.endsWith("/status") -> "Uid:\t7\t7\t7\t7\n"
                path.endsWith("/cmdline") -> "/opt/node\u0000"
                path.endsWith("/cwd") -> "/data/root"
                else -> null
            }
        }
        assertTrue(unknown is io.github.ccch1mneyyy.dsh_app.runtime.AdoptedEngine.Liveness.Alive)
        assertTrue(su > 0)
    }
}

/** su that prints a fixed marker pid on stderr and does not exit until destroyed. */
private class HangProcess : Process() {
    private val stdin = ByteArrayOutputStream()
    private val stderrIn = java.io.PipedInputStream()
    private val stderrOut = java.io.PipedOutputStream(stderrIn)
    private val armed = AtomicBoolean(false)
    private val dead = AtomicBoolean(false)

    override fun getOutputStream(): OutputStream = stdin
    override fun getInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))
    override fun getErrorStream(): InputStream = stderrIn
    override fun waitFor(): Int = 0
    override fun exitValue(): Int = 0
    override fun destroy() { dead.set(true) }
    override fun destroyForcibly(): Process { dead.set(true); return this }
    override fun isAlive(): Boolean = !dead.get()

    override fun waitFor(timeout: Long, unit: TimeUnit): Boolean {
        if (armed.compareAndSet(false, true)) {
            val text = stdin.toString(Charsets.UTF_8.name())
            val marker = Regex("__DSH_[0-9a-f]+_OK").find(text)?.value?.removeSuffix("_OK")
            if (marker != null) stderrOut.write("${marker}_OK 4242\n".toByteArray())
            stderrOut.flush()
        }
        val slice = minOf(unit.toMillis(timeout), 50L)
        Thread.sleep(slice)
        return false
    }
}

private class DoneProcess : Process() {
    override fun getOutputStream(): OutputStream = ByteArrayOutputStream()
    override fun getInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))
    override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))
    override fun waitFor(): Int = 0
    override fun exitValue(): Int = 0
    override fun destroy() {}
    override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = true
}
