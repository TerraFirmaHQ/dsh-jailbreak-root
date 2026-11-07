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

    data class Mounts(val prompt: Boolean, val persona: Boolean, val root: Boolean)

    @Volatile private var bridge: PhoneBridge? = null
    val approvals = ApprovalBoard()
    @Volatile var onApproval: ((Map<String, Any?>) -> Unit)? = null

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun rootEnabled(context: Context): Boolean = prefs(context).getBoolean(K_ROOT, false)
    fun chrootEnabled(context: Context): Boolean = prefs(context).getBoolean(K_CHROOT, false)
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
        )
    }

    /** Turning the switch on is the only path that probes su from settings. */
    fun setRootEnabled(context: Context, on: Boolean): String? {
        if (!on) {
            prefs(context).edit().putBoolean(K_ROOT, false).apply()
            approvals.revoke()
            RootExec.revoke()
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
        if (!on) {
            prefs(context).edit().putBoolean(K_CHROOT, false).apply()
            return null
        }
        if (!ChrootLaunch.granted(force = true)) {
            prefs(context).edit().putBoolean(K_CHROOT, false).apply()
            return "NO_SU"
        }
        prefs(context).edit().putBoolean(K_CHROOT, true).remove(K_CHROOT_FAILED).apply()
        return null
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
        val prompt = HostRootGuest.writePrompt(context, rootfs)
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
        val root = if (rootEnabled(context)) startRoot(context, rootfs) else {
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

    fun resolveApproval(id: String, accept: Boolean): Boolean = approvals.resolve(id, accept)

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
        )

    private fun ask(context: Context, command: String, preview: String, protectedChange: Boolean): Boolean {
        if (!rootEnabled(context)) return false
        return requestApproval(command, preview, protectedChange) && rootEnabled(context)
    }

    /**
     * The event the Flutter dialog answers. [resolveApproval] with the same id
     * unblocks the tool. This does not call su.
     */
    internal fun requestApproval(command: String, preview: String, protectedChange: Boolean): Boolean {
        val id = approvals.begin(command, preview, protectedChange)
        onApproval?.invoke(
            mapOf(
                "type" to "approval",
                "id" to id,
                "preview" to preview,
                "protected" to protectedChange,
                "command" to RootPolicy.redact(command),
            ),
        )
        return approvals.await(id, command)
    }
}
