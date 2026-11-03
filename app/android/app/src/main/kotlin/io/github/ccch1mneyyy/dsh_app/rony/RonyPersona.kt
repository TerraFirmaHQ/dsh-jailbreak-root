package io.github.ccch1mneyyy.dsh_app.rony

import android.content.Context
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * Rony fork (v5.1): 人设自定义 + 活人感. The persona plugin (asset `assets/engine/persona/`) plus the
 * owner's persona text are written into [GUEST_DIR] and mounted with `--patch` ONLY while
 * [RonyPrefs.humanlike] is on. Off: the overlay file is deleted, so the engine starts without the
 * section (破甲 + stock DSH only). The in-app split-send/typing delay (Dart) and the proactive
 * check-in ([RonyProactive]) read the same switch.
 */
object RonyPersona {
    const val GUEST_DIR = "/opt/dsh-app/rony/persona"
    const val PATCH = "$GUEST_DIR/persona.patch.yml"
    const val ENTRY = "$GUEST_DIR/index.mjs"
    const val ASSET_DIR = "flutter_assets/assets/engine/persona"
    val MODULES = listOf("index.mjs", "default.md", "rules.md")

    // l10n:keep agent config comment
    val PATCH_YAML = """# dsh-app (Rony fork): 人设自定义 + 活人感. Rewritten by the app at every engine start and passed
# with --patch only while 活人感 is on (never in safe mode). Edits do not survive.
- insert:
    - id: rony-persona
      name: $ENTRY
"""

    fun file(rootfs: File) = File(rootfs, PATCH.removePrefix("/"))

    fun launchArg(arg: String): String? = RonyGuest.launchArg(arg, PATCH)

    fun writeFiles(context: Context, rootfs: File, personaText: String): Boolean =
        writeFiles(rootfs, personaText) { name -> context.assets.open("$ASSET_DIR/$name").use { it.readBytes() } }

    internal fun writeFiles(rootfs: File, personaText: String, read: (String) -> ByteArray): Boolean {
        val patch = file(rootfs)
        return try {
            val dir = File(rootfs, GUEST_DIR.removePrefix("/"))
            if (Files.isSymbolicLink(dir.toPath()) || (dir.exists() && !dir.isDirectory)) dir.delete()
            if (!dir.isDirectory && !dir.mkdirs()) throw IOException("could not create ${dir.path}")
            for (name in MODULES) {
                val bytes = read(name)
                if (bytes.isEmpty()) throw IOException("$name is empty")
                RonyPojia.writeIfChanged(File(dir, name), bytes)
            }
            // Empty persona.md = the bundled default voice.
            RonyPojia.writeIfChanged(File(dir, "persona.md"), personaText.trim().toByteArray(Charsets.UTF_8).let { if (it.isEmpty()) "\n".toByteArray() else it })
            RonyPojia.writeIfChanged(patch, PATCH_YAML.toByteArray())
            true
        } catch (_: Exception) {
            patch.delete()
            false
        }
    }

    /** 活人感 off / safe mode: no overlay, and the owner's text is not left in the guest either. */
    fun clear(rootfs: File) {
        runCatching { file(rootfs).delete() }
        runCatching { File(rootfs, GUEST_DIR.removePrefix("/") + "/persona.md").delete() }
    }

    fun defaultText(context: Context): String =
        runCatching { context.assets.open("$ASSET_DIR/default.md").use { String(it.readBytes(), Charsets.UTF_8).trim() } }.getOrDefault("")
}
