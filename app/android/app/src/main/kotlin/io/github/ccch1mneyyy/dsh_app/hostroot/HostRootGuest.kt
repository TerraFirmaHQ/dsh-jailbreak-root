package io.github.ccch1mneyyy.dsh_app.hostroot

import android.content.Context
import io.github.ccch1mneyyy.dsh_app.phone.PhoneGuest
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest

/**
 * Files the engine loads for the locked prompt, the optional persona, and the
 * host-root MCP server. Writing these does not call su and does not change
 * mounts, SELinux, or modules.
 */
object HostRootGuest {
    const val PROMPT_DIR = "/opt/dsh-app/hostroot/prompt"
    const val PROMPT_PATCH = "$PROMPT_DIR/prompt.patch.yml"
    const val PERSONA_DIR = "/opt/dsh-app/hostroot/persona"
    const val PERSONA_PATCH = "$PERSONA_DIR/persona.patch.yml"
    const val ROOT_DIR = "/opt/dsh-app/hostroot/root"
    const val ROOT_PATCH = "$ROOT_DIR/root.patch.yml"
    const val ROOT_CONFIG = "/var/lib/dsh-app/hostroot/root-bridge.json"

    const val PROMPT_ASSET = "flutter_assets/assets/engine/hostprompt"
    const val EXPECTED_PROMPT_SHA256 = "4fbd3a104fcd16065800e4e730a67f7781698300eec33af8dc67cfd4f328c118"

    val AGENTS_SECTION = """
        |
        |## 主机 Root
        |
        |- 用 `mcp__root__*` 操作安卓主机。`mcp__root__shell` 以 uid 0 在手机上执行，不是沙箱，也不是 PRoot 里看到的假 root。PRoot 里的 bash 碰不到安卓的 /data。
        |- 读文件用 `read_file`（包括 /data/adb）。任意 shell 和写入都要用户看过预览才执行；文本看起来像只读也不等于隔离。对 KSU、Zygisk、HMA、模块、SELinux、挂载、resetprop 的修改会标明。用户取消就不执行，不要换一种写法绕过。
        |- root 开关关闭时这些工具不可用，不要尝试 su。
        |""".trimMargin()

    private const val WEB_PATCH_ARG = "--patch /etc/dsh-app/dsh-web.patch.yml"
    private const val DSH_WEB = "/usr/local/bin/dsh web"

    /**
     * [allowProfile] is only for the locked prompt. Safe mode's command contains
     * `--profile`, and that session still gets the prompt as its first segment.
     * Root and persona overlays stay off in safe mode (they pass false).
     */
    fun launchArg(arg: String, patch: String, allowProfile: Boolean = false): String? {
        val web = arg.indexOf("$DSH_WEB ")
        if (web < 0 || arg.indexOf("$DSH_WEB ", web + 1) >= 0) return null
        if (!allowProfile && arg.contains("--profile")) return null
        val anchor = arg.indexOf(WEB_PATCH_ARG, web)
        val at = if (anchor >= 0 && arg.indexOf(WEB_PATCH_ARG, anchor + 1) < 0) anchor + WEB_PATCH_ARG.length else web + DSH_WEB.length
        return arg.substring(0, at) + " --patch $patch" + arg.substring(at)
    }

    val PROMPT_YAML = """
        |# dsh Root: locked built-in prompt. Rewritten at every engine start. Not used in safe mode.
        |- insert:
        |    - id: dsh-builtin-prompt
        |      name: $PROMPT_DIR/index.mjs
        |""".trimMargin()

    val PERSONA_YAML = """
        |# dsh Root: optional persona. Omitted when the text is blank.
        |- insert:
        |    - id: dsh-persona
        |      name: $PERSONA_DIR/persona.mjs
        |""".trimMargin()

    val ROOT_YAML = """
        |# dsh Root: host root MCP. Passed only while the root switch is on. Never in safe mode.
        |- insert:
        |    - id: dsh-host-root
        |      name: '@deepseek-ai/dsh-mcp-client'
        |      config:
        |        serverName: root
        |        transport: stdio
        |        command: !!js process.execPath
        |        args:
        |          - $ROOT_DIR/relay.cjs
        |          - $ROOT_CONFIG
        |        toolCallTimeoutMs: 330000
        |        failOnStartupError: false
        |""".trimMargin()

    fun promptPatch(rootfs: File) = File(rootfs, PROMPT_PATCH.removePrefix("/"))
    fun personaPatch(rootfs: File) = File(rootfs, PERSONA_PATCH.removePrefix("/"))
    fun rootPatch(rootfs: File) = File(rootfs, ROOT_PATCH.removePrefix("/"))

    /**
     * Copies the locked prompt. Returns false and leaves no patch when the
     * asset is missing or its bytes are empty. Does not invent a replacement.
     */
    fun writePrompt(context: Context, rootfs: File): Boolean =
        writePrompt(rootfs) { name -> context.assets.open("$PROMPT_ASSET/$name").use { it.readBytes() } }

    internal fun writePrompt(rootfs: File, read: (String) -> ByteArray): Boolean {
        val patch = promptPatch(rootfs)
        return try {
            val prompt = read("prompt.md")
            val plugin = read("index.mjs")
            if (prompt.isEmpty() || plugin.isEmpty()) throw IOException("prompt asset missing")
            if (sha256(prompt) != EXPECTED_PROMPT_SHA256) throw IOException("prompt bytes changed")
            val dir = realDir(File(rootfs, PROMPT_DIR.removePrefix("/")))
            writeIfChanged(File(dir, "prompt.md"), prompt)
            writeIfChanged(File(dir, "index.mjs"), plugin)
            writeIfChanged(patch, PROMPT_YAML.toByteArray())
            true
        } catch (_: Exception) {
            runCatching { patch.delete() }
            false
        }
    }

    /** Blank [persona] removes the overlay. No default persona is substituted. */
    fun writePersona(rootfs: File, persona: String, plugin: ByteArray): Boolean {
        val patch = personaPatch(rootfs)
        val text = persona.trim()
        if (text.isEmpty() || plugin.isEmpty()) {
            runCatching { patch.delete() }
            return false
        }
        return try {
            val dir = realDir(File(rootfs, PERSONA_DIR.removePrefix("/")))
            writeIfChanged(File(dir, "persona.mjs"), plugin)
            writeIfChanged(File(dir, "persona.md"), text.toByteArray())
            writeIfChanged(patch, PERSONA_YAML.toByteArray())
            true
        } catch (_: Exception) {
            runCatching { patch.delete() }
            false
        }
    }

    fun writeRoot(rootfs: File, endpoint: PhoneGuest.Endpoint): Boolean {
        val patch = rootPatch(rootfs)
        return try {
            val dir = realDir(File(rootfs, ROOT_DIR.removePrefix("/")))
            writeIfChanged(File(dir, "relay.cjs"), PhoneGuest.RELAY_JS.toByteArray())
            writeIfChanged(File(dir, "root.patch.yml"), ROOT_YAML.toByteArray())
            val state = realDir(File(rootfs, "/var/lib/dsh-app/hostroot".removePrefix("/")))
            val config = File(state, "root-bridge.json")
            writeIfChanged(config, PhoneGuest.config(endpoint).toByteArray())
            config.setReadable(false, false)
            config.setReadable(true, true)
            true
        } catch (_: Exception) {
            runCatching { patch.delete() }
            false
        }
    }

    fun clearRoot(rootfs: File) {
        runCatching { rootPatch(rootfs).delete() }
        runCatching { File(rootfs, ROOT_CONFIG.removePrefix("/")).delete() }
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun realDir(dir: File): File {
        if (Files.isSymbolicLink(dir.toPath()) || (dir.exists() && !dir.isDirectory)) dir.delete()
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("could not create ${dir.path}")
        return dir
    }

    private fun writeIfChanged(target: File, bytes: ByteArray) {
        if (Files.isRegularFile(target.toPath(), LinkOption.NOFOLLOW_LINKS) && target.readBytes().contentEquals(bytes)) return
        val tmp = File(target.parentFile, ".${target.name}.${System.nanoTime()}.tmp")
        try {
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(target)) throw IOException("could not replace ${target.path}")
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }
}
