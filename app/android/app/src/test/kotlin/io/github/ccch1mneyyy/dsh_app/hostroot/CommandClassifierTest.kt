package io.github.ccch1mneyyy.dsh_app.hostroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CommandClassifierTest {
    @Test
    fun `seventeen cases print the classifier result`() {
        val cases = listOf(
            Case("R1", "id", true, "read"),
            Case("R2", "getprop ro.product.model", true, "read"),
            Case("R3", "getprop a; getprop b; uptime", true, "read"),
            Case("R4", "pm list packages | grep x", true, "read"),
            Case("R5", "iptables -L PG_OUT -n", true, "read"),
            Case("R6", "dumpsys package x | head -20", true, "read"),
            Case("R7", "ps -A -o RSS,NAME 2>/dev/null | sort -rn | head -8", true, "read"),
            Case("R8", "echo ---; ps -A 2>/dev/null | head -5", true, "read"),
            Case("R9", "ls -l /data/adb/modules 2>&1 | head", true, "read"),
            Case("R10", "awk '{print \$1}' /proc/meminfo | head -3", true, "read"),
            Case("W1", "echo x > /data/local/tmp/a", false, "write"),
            Case("W2", "pm disable-user --user 0 x", false, "write"),
            Case("W3", "sh -c \"rm -rf /data/x\"", false, "write"),
            Case("W4", "setprop debug.t 1", false, "write"),
            Case("W5", "iptables -F", false, "write"),
            Case("W6", "sed -i 's/a/b/' /data/x", false, "write"),
            Case("W7", "awk 'BEGIN{system(\"rm -rf /data/x\")}'", false, "write"),
        )
        val lines = cases.map { item ->
            val result = CommandClassifier.classifyCommand(item.command)
            assertEquals(item.id, item.readonly, result.readonly)
            if (item.readonly) {
                assertTrue(result.subcommands.all { it.verdict == "read" })
            } else {
                assertTrue(result.subcommands.any { it.verdict == item.verdict || it.verdict == "unknown" })
            }
            if (item.id == "W1") assertTrue(result.hit.contains("redirect"))
            if (item.id == "W3") assertTrue(result.hit.contains("sh-c+rm"))
            if (item.id == "W6") assertTrue(result.hit.contains("sed-i"))
            if (item.id == "W7") assertTrue(result.hit.contains("awk"))
            line(item.id, item.command, result)
        }
        val text = lines.joinToString("\n") + "\n"
        print(text)
        val dir = File("/workspace/docs/delivery")
        dir.mkdirs()
        File(dir, "D1-17-stdout.txt").writeText(text)
        assertEquals(17, lines.size)
    }

    @Test
    fun `quotes compounds replacement redirects unknowns and dangerous options`() {
        val quoted = CommandClassifier.classifyCommand("cat \"a; rm x\"")
        assertTrue(quoted.readonly)
        assertEquals(1, quoted.subcommands.size)

        val subst = CommandClassifier.classifyCommand("cat \$(id)")
        assertFalse(subst.readonly)
        assertTrue(subst.hit.contains("subst"))

        val unknown = CommandClassifier.classifyCommand("foobar --weird")
        assertFalse(unknown.readonly)
        assertEquals("unknown", unknown.subcommands.single().verdict)

        val find = CommandClassifier.classifyCommand("find /data -name x -delete")
        assertFalse(find.readonly)
        assertTrue(find.hit.contains("find-exec"))

        val phone = CommandClassifier.classifyCommand(
            "ps -A -o PID,RSS,NAME 2>/dev/null | grep -E \"dsh_app|MainThread\" | head -5; echo ---; ps -A -o RSS,NAME 2>/dev/null | sort -rn | head -8",
        )
        assertTrue(phone.readonly)
        assertTrue(phone.subcommands.all { it.verdict == "read" })
        val nullRedirect = CommandClassifier.classifyCommand("ps -A 2>/dev/null")
        assertTrue(nullRedirect.readonly)
        val fd = CommandClassifier.classifyCommand("echo ok &>/dev/null")
        assertTrue(fd.readonly)
        assertEquals(1, fd.subcommands.size)
        val pathRedirect = CommandClassifier.classifyCommand("echo x 2>/sdcard/err")
        assertFalse(pathRedirect.readonly)
        assertFalse(CommandClassifier.classifyCommand("sort -o /tmp/out").readonly)
        assertFalse(CommandClassifier.classifyCommand("date -s 01010000").readonly)
        assertFalse(CommandClassifier.classifyCommand("sed -e 'w /tmp/x'").readonly)
        assertFalse(CommandClassifier.classifyCommand("awk '{print | getline}'").readonly)
        assertNull(CommandClassifier.sessionKey("rm -f /data/x"))
    }

    @Test
    fun `readonly skip runs only a readonly command and never a write`() {
        var asked = 0
        var ran: String? = null
        val runner = { command: String, _: Long, _: String? ->
            ran = command
            RootExec.Result(RootExec.State.OK, 0, "0\n", "", false, 1, null)
        }
        val read = RootTools.call(
            "shell",
            mapOf("command" to "id"),
            { true },
            RootTools.Approver { _, _, _ -> asked += 1; false },
            null,
            readonlySkip = { true },
            runner = runner,
        )
        assertEquals(0, asked)
        assertEquals("id", ran)
        assertFalse(read.isError)
        assertTrue(read.text.contains("\"stdout\""))
        assertFalse(read.text.contains("spool="))

        ran = null
        val write = RootTools.call(
            "shell",
            mapOf("command" to "setprop debug.t 1"),
            { true },
            RootTools.Approver { _, _, _ -> asked += 1; false },
            null,
            readonlySkip = { true },
            runner = runner,
        )
        assertEquals(1, asked)
        assertNull(ran)
        assertTrue(write.text.contains(RootTools.DENIED))
    }

    @Test
    fun `check root does not call su while the switch is off`() {
        var called = false
        val out = RootTools.call(
            "check_root",
            emptyMap(),
            { false },
            RootTools.Approver { _, _, _ -> true },
            null,
        ) { _, _, _ ->
            called = true
            error("su")
        }
        assertFalse(called)
        assertTrue(out.text.contains("\"suCalled\":false"))
        assertTrue(out.text.contains("\"granted\":false"))
        assertFalse(out.isError)
    }

    @Test
    fun `summary keeps a short head and tail`() {
        val text = (1..80).joinToString("\n") { "line $it" }
        val summary = RootTools.summarize(text, "/tmp/spool")
        assertTrue(summary.contains("line 1"))
        assertTrue(summary.contains("line 40"))
        assertTrue(summary.contains("line 80"))
        assertTrue(summary.contains("共 80 行"))
        assertTrue(summary.contains("/tmp/spool"))
        assertTrue(summary.length < text.length)
    }

    private fun line(id: String, command: String, result: CommandClassifier.Result): String {
        val subs = result.subcommands.joinToString(",") { sub ->
            val reason = sub.reason?.let { ",\"reason\":${RootTools.jsonString(it)}" } ?: ""
            "{\"text\":${RootTools.jsonString(sub.text)},\"verdict\":${RootTools.jsonString(sub.verdict)}$reason}"
        }
        val hits = result.hit.joinToString(",") { RootTools.jsonString(it) }
        return "{\"id\":${RootTools.jsonString(id)},\"command\":${RootTools.jsonString(command)},\"readonly\":${result.readonly},\"subcommands\":[$subs],\"hit\":[$hits],\"reason\":${RootTools.jsonString(result.reason)}}"
    }

    private data class Case(val id: String, val command: String, val readonly: Boolean, val verdict: String)
}

class EngineCacheGapTest {
    @Test
    fun `idle spool cleanup is not an engine cache release`() {
        assertTrue(HostRootControl.ENGINE_CACHE_GAP.contains("/dsh-go/v1"))
        assertFalse(HostRootControl.ENGINE_CACHE_GAP.contains("released"))
    }
}

class HostRootPerfTest {
    @Test
    fun `twenty host rounds record memory and token estimate`() {
        val runtime = Runtime.getRuntime()
        System.gc()
        val before = runtime.totalMemory() - runtime.freeMemory()
        val estimates = (1..20).map { round ->
            val blob = (1..4000).joinToString("\n") { "round $round line $it " + "x".repeat(30) }
            val summary = RootTools.summarize(blob, "/tmp/not-a-phone/spool-$round")
            (summary.length + 3) / 4
        }
        System.gc()
        val after = runtime.totalMemory() - runtime.freeMemory()
        val avg = estimates.average()
        val text = buildString {
            append("environment=host-jvm\n")
            append("device=not-ace7\n")
            append("method=20 in-process summarize rounds, 4000 lines each\n")
            append("memoryBeforeBytes=").append(before).append('\n')
            append("memoryAfterBytes=").append(after).append('\n')
            append("deltaBytes=").append(after - before).append('\n')
            append("tokenAlgorithm=charCount/4\n")
            append("avgSummaryTokens=").append(avg).append('\n')
            append("note=this is not an Ace7 measurement and not a 20-round phone tool session\n")
        }
        print(text)
        val dir = File("/workspace/docs/delivery")
        dir.mkdirs()
        File(dir, "PERF-host.txt").writeText(text)
        assertEquals(20, estimates.size)
        assertTrue(avg < 8000)
    }
}
