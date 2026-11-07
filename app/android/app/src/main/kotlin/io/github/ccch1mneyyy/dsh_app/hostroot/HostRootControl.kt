package io.github.ccch1mneyyy.dsh_app.hostroot

import android.content.Context
import android.content.SharedPreferences
import io.github.ccch1mneyyy.dsh_app.phone.McpServer
import io.github.ccch1mneyyy.dsh_app.phone.PhoneBridge
import io.github.ccch1mneyyy.dsh_app.phone.ToolOutput
import io.github.ccch1mneyyy.dsh_app.runtime.ChrootLaunch
import java.io.File

/**
 * Prefs and the host-root bridge. Default off. [prepare] never calls su.
 * su is probed only when the user turns root or chroot on, and when a tool
 * actually runs after that.
 */
object HostRootControl {
    const val PREFS = "dsh_hostroot"
    const val K_ROOT = "rootEnabled"
    const val K_CHROOT = "chrootEnabled"
    const val K_CHROOT_FAILED = "chrootFailedRuntime"
    const val K_PERSONA = "persona"
    const val K_READONLY_SKIP = "readonlySkip"
    const val K_AUTO_ALLOW = "autoAllowShell"

    data class Mounts(val prompt: Boolean, val persona: Boolean, val root: Boolean)

    @Volatile private var bridge: PhoneBridge? = null
    val approvals = ApprovalBoard()
    @Volatile var onApproval: ((Map<String, Any?>) -> Unit)? = null
    @Volatile var selfCheckSummary: String = "skipped, su not called"
    private val sessionMemory = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val sessionChosen = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun rootEnabled(context: Context): Boolean = prefs(context).getBoolean(K_ROOT, false)
    fun chrootEnabled(context: Context): Boolean {
        if (prefs(context).getBoolean(K_CHROOT, false)) {
            prefs(context).edit().putBoolean(K_CHROOT, false).apply()
        }
        return false
    }
    fun persona(context: Context): String = prefs(context).getString(K_PERSONA, "") ?: ""

    fun status(context: Context): Map<String, Any?> {
        val prompt = runCatching {
            context.assets.open("${HostRootGuest.PROMPT_ASSET}/prompt.md").use { it.readBytes() }
        }
        val bytes = prompt.getOrNull()
        return mapOf(
            "rootEnabled" to rootEnabled(context),
            "chrootEnabled" to chrootEnabled(context),
            "suCached" to ChrootLaunch.cachedGrant(),
            "persona" to persona(context),
            "promptLocked" to true,
            "promptSha256" to bytes?.let { HostRootGuest.sha256(it) },
            "promptBytes" to bytes?.size,
            "promptOk" to (bytes != null && HostRootGuest.sha256(bytes) == HostRootGuest.EXPECTED_PROMPT_SHA256),
            "shellSandboxed" to false,
            "readonlySkip" to readonlySkip(context),
            "autoAllowShell" to autoAllow(context),
            "selfCheck" to selfCheckSummary,
            "chrootMountsDisabled" to true,
        )
    }

    fun readonlySkip(context: Context): Boolean = prefs(context).getBoolean(K_READONLY_SKIP, false)

    fun setReadonlySkip(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(K_READONLY_SKIP, on).apply()
    }

    /** Explicit user switch. Default off. Not migrated from any older key. */
    fun autoAllow(context: Context): Boolean = prefs(context).getBoolean(K_AUTO_ALLOW, false)

    fun setAutoAllow(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(K_AUTO_ALLOW, on).apply()
    }

    @Volatile
    var lastRootfs: File? = null

    fun channelPromptFile(context: Context) = File(context.filesDir, "channel_prompts.json")

    fun readChannelPrompts(context: Context): String =
        runCatching { channelPromptFile(context).readText() }.getOrDefault("")

    fun writeChannelPrompts(context: Context, json: String) {
        channelPromptFile(context).writeText(json)
        val rootfs = lastRootfs
        if (rootfs != null) runCatching { syncChannelPolicy(context, rootfs) }
    }

    private fun syncChannelPolicy(context: Context, rootfs: File) {
        val file = channelPromptFile(context)
        if (!file.isFile) return
        HostRootGuest.writePolicy(rootfs, file.readBytes())
    }

    fun readAudit(context: Context): String =
        runCatching { File(context.filesDir, "root_audit.jsonl").readText() }.getOrDefault("")

    fun clearAudit(context: Context) {
        runCatching { File(context.filesDir, "root_audit.jsonl").delete() }
    }

    /** Diagnostics only. Command text and output stay out. */
    fun auditMeta(context: Context): List<Map<String, Any?>> {
        val file = File(context.filesDir, "root_audit.jsonl")
        if (!file.isFile) return emptyList()
        return file.readLines().takeLast(40).mapNotNull { line ->
            if (!line.startsWith("{")) return@mapNotNull null
            fun field(name: String): String? =
                Regex(""""$name":("(?:\\.|[^"\\])*"|-?\d+|null)""").find(line)?.groupValues?.get(1)?.trim('"')
            val cmd = field("cmd") ?: return@mapNotNull null
            mapOf(
                "cmdHash" to sha256Short(cmd),
                "verdict" to field("verdict"),
                "choice" to field("choice"),
                "exitCode" to field("exitCode"),
                "durationMs" to field("durationMs"),
                "bytes" to field("stdoutBytes"),
                "paging" to field("paging"),
            )
        }
    }

    private fun sha256Short(text: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    fun clearSessionMemory() {
        sessionMemory.clear()
    }

    /** Turning the switch on is the only path that probes su from settings. */
    fun setRootEnabled(context: Context, on: Boolean): String? {
        if (!on) {
            prefs(context).edit().putBoolean(K_ROOT, false).apply()
            approvals.revoke()
            RootExec.revoke()
            sessionMemory.clear()
            selfCheckSummary = "skipped, su not called"
            stopBridge()
            return null
        }
        if (!ChrootLaunch.granted(force = true)) {
            prefs(context).edit().putBoolean(K_ROOT, false).apply()
            return "NO_SU"
        }
        prefs(context).edit().putBoolean(K_ROOT, true).apply()
        return null
    }

    fun setChrootEnabled(context: Context, on: Boolean, runtimeVersion: String?): String? {
        // Mount path removed. Never probe su from this switch.
        prefs(context).edit().putBoolean(K_CHROOT, false).apply()
        return "MOUNTS_DISABLED"
    }

    fun setPersona(context: Context, text: String) {
        prefs(context).edit().putString(K_PERSONA, text).apply()
    }

    fun chrootFailed(context: Context, runtime: String?) {
        if (runtime.isNullOrEmpty()) return
        prefs(context).edit().putString(K_CHROOT_FAILED, runtime).apply()
    }

    fun chrootPreference(context: Context, runtime: String?): ChrootLaunch.Preference =
        ChrootLaunch.Preference(
            saved = if (prefs(context).contains(K_CHROOT)) prefs(context).getBoolean(K_CHROOT, false) else null,
            available = true,
            failedRuntime = prefs(context).getString(K_CHROOT_FAILED, null),
            runtime = runtime,
        )

    /**
     * Engine start. Writes overlays only. Does not mount, setenforce, or call su.
     */
    fun prepare(context: Context, rootfs: File, safe: Boolean): Mounts {
        // The locked prompt is mounted in every session mode, including safe mode.
        // Safe mode still turns host-root tools and the optional persona off.
        lastRootfs = rootfs
        val prompt = HostRootGuest.writePrompt(context, rootfs)
        runCatching { syncChannelPolicy(context, rootfs) }
        if (safe) {
            stopBridge()
            HostRootGuest.clearRoot(rootfs)
            runCatching { HostRootGuest.personaPatch(rootfs).delete() }
            return Mounts(prompt, false, false)
        }
        val personaText = persona(context)
        val plugin = runCatching {
            context.assets.open("${HostRootGuest.PROMPT_ASSET}/persona.mjs").use { it.readBytes() }
        }.getOrDefault(ByteArray(0))
        val personaOn = HostRootGuest.writePersona(rootfs, personaText, plugin)
        val root = if (rootEnabled(context)) {
            selfCheckSummary = RootTools.call(
                "check_root",
                emptyMap(),
                enabled = { true },
                approver = RootTools.Approver { _, _, _ -> false },
                spoolDir = null,
            ).text
            startRoot(context, rootfs)
        } else {
            selfCheckSummary = "skipped, su not called"
            stopBridge()
            HostRootGuest.clearRoot(rootfs)
            false
        }
        return Mounts(prompt, personaOn, root)
    }

    fun stopBridge() {
        bridge?.stop()
        bridge = null
        RootExec.closeAll()
    }

    fun resolveApproval(id: String, accept: Boolean, session: Boolean = false): Boolean {
        if (session && accept) sessionChosen.add(id)
        return approvals.resolve(id, accept)
    }

    fun applyBattery(context: Context, id: String): Map<String, Any?> {
        if (!rootEnabled(context)) return mapOf("ok" to false, "error" to RootTools.DISABLED)
        val action = BatteryTuning.actions(context.packageName).firstOrNull { it.id == id }
            ?: return mapOf("ok" to false, "error" to "unknown")
        val outcome = BatteryTuning.apply(action) { command ->
            val result = RootExec.run(command, 20_000, null, File(context.cacheDir, "hostroot-spool"))
            if (result.state != RootExec.State.OK) result.detail ?: result.stderr else result.stdout
        }
        return mapOf(
            "ok" to outcome.ok,
            "id" to outcome.id,
            "detail" to outcome.detail,
            "before" to outcome.before.take(500),
            "after" to outcome.after.take(500),
        )
    }

    private fun startRoot(context: Context, rootfs: File): Boolean {
        stopBridge()
        val server = McpServer(RootTools.definitions, { name, args -> onTool(context, name, args) }, serverVersion = "dsh-root")
        val next = PhoneBridge(server) { }
        val endpoint = next.start(withTcp = true)
        bridge = next
        return HostRootGuest.writeRoot(rootfs, endpoint)
    }

    private fun onTool(context: Context, name: String, args: Map<String, Any?>): ToolOutput =
        RootTools.call(
            name,
            args,
            enabled = { rootEnabled(context) },
            approver = RootTools.Approver { command, preview, protectedChange ->
                ask(context, command, preview, protectedChange)
            },
            spoolDir = File(context.cacheDir, "hostroot-spool"),
            readonlySkip = { readonlySkip(context) },
            autoAllow = { autoAllow(context) },
            sessionAllows = { key -> sessionMemory.contains(key) },
        ).also { out -> audit(context, name, args, out) }

    private fun ask(context: Context, command: String, preview: String, protectedChange: Boolean): Boolean {
        if (!rootEnabled(context)) return false
        return requestApproval(command, preview, protectedChange) && rootEnabled(context)
    }

    /**
     * The event the Flutter dialog answers. [resolveApproval] with the same id
     * unblocks the tool. This does not call su.
     */
    internal fun requestApproval(command: String, preview: String, protectedChange: Boolean): Boolean {
        val remember = preview.contains("@@session=1")
        val shown = preview.replace("\n@@session=1", "")
        val id = approvals.begin(command, shown, protectedChange)
        val key = if (remember) CommandClassifier.sessionKey(command) else null
        onApproval?.invoke(
            mapOf(
                "type" to "approval",
                "id" to id,
                "preview" to shown,
                "protected" to protectedChange,
                "session" to remember,
                "command" to command,
            ),
        )
        val ok = approvals.await(id, command)
        if (ok && key != null && sessionChosen.remove(id)) sessionMemory.add(key)
        else sessionChosen.remove(id)
        return ok
    }

    internal fun hasSessionMemory(key: String): Boolean = sessionMemory.contains(key)

    private fun audit(context: Context, name: String, args: Map<String, Any?>, out: ToolOutput) {
        val line = auditLine(name, args, out) ?: return
        runCatching {
            val file = File(context.filesDir, "root_audit.jsonl")
            file.appendText(line + "\n")
        }
        releaseOldSpools(File(context.cacheDir, "hostroot-spool"))
    }

    /** JSONL fields are time/cmd/verdict/reason/choice. Command output is not a field. */
    internal fun auditLine(name: String, args: Map<String, Any?>, out: ToolOutput, time: Long = System.currentTimeMillis()): String? {
        if (name != "shell" && name != "write_file" && name != "read_file") return null
        val cmd = RootPolicy.redact((args["command"] as? String) ?: (args["path"] as? String) ?: name)
        val classified = (args["command"] as? String)?.let { CommandClassifier.classifyCommand(it) }
        val choice = when {
            out.text.contains(RootTools.DENIED) -> "deny"
            out.text.contains("\"approval\":\"auto\"") -> "auto"
            out.isError -> "error"
            else -> "allow"
        }
        return buildString {
            append("{\"time\":").append(time)
            append(",\"cmd\":").append(RootTools.jsonString(cmd))
            append(",\"verdict\":").append(RootTools.jsonString(classified?.reason ?: name))
            append(",\"reason\":").append(RootTools.jsonString(classified?.hit?.joinToString(",") ?: ""))
            append(",\"choice\":").append(RootTools.jsonString(choice))
            val exit = Regex(""""exitCode":(-?\d+)""").find(out.text)?.groupValues?.get(1)
            append(",\"exitCode\":").append(exit ?: "null")
            val duration = Regex(""""durationMs":(-?\d+)""").find(out.text)?.groupValues?.get(1)
            if (duration != null) append(",\"durationMs\":").append(duration)
            val bytes = Regex(""""stdoutBytes":(-?\d+)""").find(out.text)?.groupValues?.get(1)
            if (bytes != null) append(",\"stdoutBytes\":").append(bytes)
            if (out.text.contains("\"paging\":true")) append(",\"paging\":true")
            append("}")
        }
    }

    /**
     * Idle release that exists in this process: spool files and finished job
     * records. The engine process has no route for this. See
     * [ENGINE_CACHE_GAP].
     */
    const val ENGINE_CACHE_GAP =
        "no /dsh-go/v1 route drops the DSH session projection cache or the provider prompt cache without deleting the session"

    internal fun releaseOldSpools(dir: File, now: Long = System.currentTimeMillis()) {
        val cutoff = now - 90_000
        dir.listFiles()?.forEach { if (it.isFile && it.lastModified() < cutoff) it.delete() }
    }
}
