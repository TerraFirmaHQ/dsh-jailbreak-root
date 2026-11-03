package io.github.ccch1mneyyy.dsh_app.rony

import android.content.Context
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest

/**
 * Rony fork: the owner's 破甲 prompt (DeepSeek-4.1-Flash-破甲提示词.md, bundled as the Flutter asset
 * `assets/engine/pojia/prompt.md`) as an always-on system-prompt section.
 *
 * Same pattern as [io.github.ccch1mneyyy.dsh_app.runtime.AppAuthGuest]: at every engine start the
 * prompt and a tiny DSH plugin (`index.mjs`) are copied from the APK into [GUEST_DIR], and an
 * overlay that inserts the plugin by absolute path is passed with `--patch`. The plugin registers
 * the prompt as a global section ordered before the harness identity and every persona/tool
 * section, so it is the first text of the system prompt for every session from the first launch.
 * Default on ([RonyPrefs.pojiaEnabled]); never in safe mode. A plugin that fails to load leaves DSH
 * running without it.
 */
object RonyPojia {
    const val GUEST_DIR = "/opt/dsh-app/rony/pojia"
    const val PATCH = "$GUEST_DIR/pojia.patch.yml"
    const val ENTRY = "$GUEST_DIR/index.mjs"

    /** Where Flutter packs `app/assets/engine/pojia/` inside the APK's assets. */
    const val ASSET_DIR = "flutter_assets/assets/engine/pojia"
    const val PROMPT = "prompt.md"
    val MODULES = listOf("index.mjs", PROMPT)

    /** Shown on the settings page; the source file in the owner's Drive. */
    const val SOURCE_NAME = "DeepSeek-4.1-Flash-破甲提示词.md"
    const val SOURCE_VERSION = "4.1"

    // l10n:keep agent config comment
    val PATCH_YAML = """# dsh-app (Rony fork): always-on 破甲 prompt. Rewritten by the app at every engine start and passed
# with --patch while 设置 → Root 与 MCP（Rony）→ 破甲提示词 is on (never in safe mode). Edits do not survive.
- insert:
    - id: rony-pojia
      name: $ENTRY
"""

    fun file(rootfs: File) = File(rootfs, PATCH.removePrefix("/"))

    fun launchArg(arg: String): String? = RonyGuest.launchArg(arg, PATCH)

    /** Copies the plugin and the prompt, then the overlay. False (and no overlay) when anything failed. */
    fun writeFiles(context: Context, rootfs: File): Boolean =
        writeFiles(rootfs) { name -> context.assets.open("$ASSET_DIR/$name").use { it.readBytes() } }

    internal fun writeFiles(rootfs: File, read: (String) -> ByteArray): Boolean {
        val patch = file(rootfs)
        return try {
            val dir = File(rootfs, GUEST_DIR.removePrefix("/"))
            if (Files.isSymbolicLink(dir.toPath()) || (dir.exists() && !dir.isDirectory)) dir.delete()
            if (!dir.isDirectory && !dir.mkdirs()) throw IOException("could not create ${dir.path}")
            for (name in MODULES) {
                val bytes = read(name)
                if (bytes.isEmpty()) throw IOException("$name is empty")
                writeIfChanged(File(dir, name), bytes)
            }
            writeIfChanged(patch, PATCH_YAML.toByteArray())
            true
        } catch (_: Exception) {
            patch.delete()
            false
        }
    }

    fun clear(rootfs: File) {
        runCatching { file(rootfs).delete() }
    }

    /** Size and sha256 of the bundled prompt, for the settings page. */
    fun info(context: Context): Map<String, Any?> = try {
        val bytes = context.assets.open("$ASSET_DIR/$PROMPT").use { it.readBytes() }
        mapOf("source" to SOURCE_NAME, "version" to SOURCE_VERSION, "bytes" to bytes.size, "chars" to String(bytes, Charsets.UTF_8).length, "sha256" to sha256(bytes))
    } catch (e: Exception) {
        mapOf("source" to SOURCE_NAME, "version" to SOURCE_VERSION, "error" to (e.message ?: e.javaClass.simpleName))
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    internal fun writeIfChanged(target: File, bytes: ByteArray) {
        if (Files.isRegularFile(target.toPath(), LinkOption.NOFOLLOW_LINKS) && target.readBytes().contentEquals(bytes)) return
        val tmp = File(target.parentFile, ".${target.name}.rony-${System.nanoTime()}.tmp")
        try {
            tmp.writeBytes(bytes)
            tmp.setReadable(true, false)
            if (!tmp.renameTo(target)) throw IOException("could not replace ${target.path}")
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }
}
