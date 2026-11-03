package io.github.ccch1mneyyy.dsh_app.rony

import android.content.Context
import io.github.ccch1mneyyy.dsh_app.phone.McpServer
import io.github.ccch1mneyyy.dsh_app.phone.PhoneBridge
import java.io.File
import java.util.concurrent.Executors

/**
 * Rony ace7: process-wide owner of the root tool server and the person's MCP servers around the
 * engine's lifecycle. Independent of 手机操控. QQ/SnowLuma/NapCat/Aurabot intentionally omitted.
 */
class RonyControl private constructor(private val app: Context) {
    val prefs = RonyPrefs(app)
    val core = RootCore(app, prefs)
    private val server = McpServer(RootToolCatalog.tools, core::call, "2")
    private val bridge = PhoneBridge(server) { line -> logger(line.replace("phone bridge", "root bridge")) }
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "rony-control").apply { isDaemon = true } }

    @Volatile private var logger: (String) -> Unit = {}
    @Volatile private var rootfs: File? = null

    @Volatile var rootMounted = false
        private set
    @Volatile var mcpMounted: List<String> = emptyList()
        private set
    @Volatile var pojiaMounted = false
        private set
    @Volatile var personaMounted = false
        private set
    @Volatile var linuxTask: Map<String, Any?> = mapOf("phase" to "idle")

    fun prepareLaunch(rootfs: File, safe: Boolean, log: (String) -> Unit) {
        logger = log
        this.rootfs = rootfs
        stopBridge()
        rootMounted = false
        mcpMounted = emptyList()
        pojiaMounted = false
        personaMounted = false
        if (safe) {
            RonyPersona.clear(rootfs)
            RonyPojia.clear(rootfs)
            RonyGuest.clearRoot(rootfs)
            RonyGuest.writeMcp(rootfs, emptyList())
            return
        }
        // Root mode OFF = never start the bridge, never call su / autoRequest.
        if (prefs.rootEnabled) {
            try {
                val ep = bridge.start(withTcp = app.getSharedPreferences("dsh_phone", Context.MODE_PRIVATE).getBoolean("tcpFallback", false))
                RonyGuest.writeRoot(rootfs, ep)
                rootMounted = true
                log("[dsh-app] rony: root tools mounted as mcp__${RonyGuest.ROOT_SERVER}__*")
                if (!prefs.autoRequested) worker.execute { runCatching { core.autoRequestOnce() } }
            } catch (e: Exception) {
                log("[dsh-app] rony: root setup failed (${e.message}); starting without root tools")
                stopBridge()
                RonyGuest.clearRoot(rootfs)
            }
        } else {
            RonyGuest.clearRoot(rootfs)
        }
        mcpMounted = RonyGuest.writeMcp(rootfs, prefs.mcpServers())
        if (mcpMounted.isNotEmpty()) log("[dsh-app] rony: MCP servers mounted: ${mcpMounted.joinToString()}")
        // 破甲: locked-on for ace7 (prefs.pojiaEnabled stays true; UI cannot edit the prompt).
        if (prefs.pojiaEnabled) {
            pojiaMounted = RonyPojia.writeFiles(app, rootfs)
            log(if (pojiaMounted) "[dsh-app] rony: 破甲 prompt mounted (first system-prompt section)" else "[dsh-app] rony: 破甲 prompt files not written; starting without it")
        } else {
            RonyPojia.clear(rootfs)
        }
        // 人设偏好: mount only when the owner typed something (default blank = no overlay).
        val persona = prefs.personaText.trim()
        if (persona.isNotEmpty()) {
            personaMounted = RonyPersona.writeFiles(app, rootfs, persona)
            log(if (personaMounted) "[dsh-app] rony: 人设偏好 mounted" else "[dsh-app] rony: persona files not written; starting without it")
        } else {
            RonyPersona.clear(rootfs)
        }
    }

    /** Download + verify + unpack Debian (optional Linux enhance; default unused). */
    fun installLinux(): Boolean {
        if (linuxTask["phase"] in setOf("download", "unpack", "tools")) return false
        linuxTask = mapOf("phase" to "download", "bytes" to 0L, "total" to RonyLinux.TARBALL_BYTES)
        worker.execute {
            try {
                val tar = RonyLinux.download(java.io.File(app.cacheDir, "rony-linux")) { got, total -> linuxTask = mapOf("phase" to "download", "bytes" to got, "total" to total) }
                linuxTask = mapOf("phase" to "unpack")
                val r = core.linuxOp(listOf("install", tar.path, RonyLinux.TARBALL_SHA256), 900_000)
                tar.delete()
                linuxTask = if (r.state == RootExec.State.OK && r.exitCode == 0) mapOf("phase" to "done") else mapOf("phase" to "error", "error" to (r.stderr.ifBlank { r.detail ?: r.stdout }).takeLast(800))
            } catch (e: Exception) {
                linuxTask = mapOf("phase" to "error", "error" to (e.message ?: e.javaClass.simpleName))
            }
        }
        return true
    }

    fun installLinuxTools(): String? {
        val script = RonyLinux.script(app).path
        val cmd = RonyLinux.command(script, listOf("exec", "apt-get update && apt-get install -y --no-install-recommends ${RonyLinux.CORE_PACKAGES}"), RonyLinux.env(prefs))
        var id: String? = null
        core.guarded("manual", "linux", "rony-env tools", preset = RootPolicy.Verdict(RootPolicy.Level.READ_ONLY, "设置页操作")) {
            id = RootExec.startJob(cmd, null).id
            RootExec.Result(RootExec.State.OK, 0, "", "", false, 0)
        }
        return id
    }

    fun launched(rootPatched: Boolean) {
        if (rootMounted && !rootPatched) {
            logger("[dsh-app] rony: launch command has no `dsh web` for the root overlay; root tools are off")
            rootMounted = false
            stopBridge()
        }
    }

    fun onEngineStopped() {
        stopBridge()
        worker.execute { runCatching { RootExec.closeAll() } }
    }

    private fun stopBridge() {
        bridge.stop()
        rootfs?.let { File(it, RonyGuest.ROOT_CONFIG.removePrefix("/")).delete() }
    }

    fun rootfsOrNull(): File? = rootfs

    val bridgeListening: Boolean get() = bridge.listening
    val bridgeConnections: Int get() = bridge.authenticatedConnections

    fun agentsSection(): String =
        (if (rootMounted) RonyGuest.ROOT_AGENTS_SECTION + RonyLinux.AGENTS_SECTION else "") + RonyGuest.mcpAgentsSection(mcpMounted)

    companion object {
        @Volatile private var instance: RonyControl? = null

        fun get(context: Context): RonyControl =
            instance ?: synchronized(this) { instance ?: RonyControl(context.applicationContext).also { instance = it } }

        fun peek(): RonyControl? = instance

        fun extraPatches(rootfs: File): List<String> = listOf(RonyPersona.PATCH).filter { File(rootfs, it.removePrefix("/")).isFile }
    }
}
