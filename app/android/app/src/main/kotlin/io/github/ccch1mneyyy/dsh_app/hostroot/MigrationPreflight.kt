package io.github.ccch1mneyyy.dsh_app.hostroot

import java.io.File

/**
 * What an upgrade of this runtime does to the Linux tree, recorded before the
 * installer swaps rootfs. The installer itself still keeps `/root` (home.carry).
 * This preflight adds a note and a bounded copy of `root/.dsh` so a failed
 * swap can be inspected. It does not call su.
 */
object MigrationPreflight {
    const val SCHEDULE_DOWNGRADE =
        "DSH 0.2.2-rc.1 writes scheduled tasks in the new engine format. " +
            "After this runtime is installed, those tasks are not guaranteed to load on an older engine. " +
            "Downgrade is not a supported way to keep them."

    data class Plan(
        val keep: List<String>,
        val drop: List<String>,
        val scheduleDowngrade: String,
    )

    data class Report(
        val plan: Plan,
        val backedUp: Boolean,
        val listing: String,
        val error: String? = null,
    )

    fun plan(): Plan = Plan(
        keep = listOf("/root"),
        drop = listOf("/usr/local", "/opt", "apt packages installed outside /root"),
        scheduleDowngrade = SCHEDULE_DOWNGRADE,
    )

    /** Writes NOTES.txt and, when present and small enough, a copy of root/.dsh. */
    fun capture(rootfs: File, dest: File, maxBytes: Long = 64L * 1024 * 1024): Report {
        val plan = plan()
        dest.mkdirs()
        val listing = buildString {
            appendLine("keep: ${plan.keep.joinToString(", ")}")
            appendLine("drop: ${plan.drop.joinToString(", ")}")
            appendLine(plan.scheduleDowngrade)
            for (rel in listOf("root", "usr/local", "opt")) {
                val dir = File(rootfs, rel)
                appendLine("--- $rel exists=${dir.isDirectory}")
                if (dir.isDirectory) {
                    dir.list()?.sorted()?.take(200)?.forEach { appendLine("$rel/$it") }
                }
            }
        }
        val notes = File(dest, "NOTES.txt")
        return try {
            notes.writeText(listing)
            val home = File(rootfs, "root/.dsh")
            val copied = if (home.isDirectory && dirBytes(home) <= maxBytes) {
                copyTree(home, File(dest, "root-dsh"))
                true
            } else {
                false
            }
            Report(plan, copied, listing)
        } catch (e: Exception) {
            Report(plan, false, listing, e.message ?: e.javaClass.simpleName)
        }
    }

    private fun dirBytes(dir: File): Long {
        var n = 0L
        dir.walkTopDown().forEach { if (it.isFile) n += it.length() }
        return n
    }

    private fun copyTree(from: File, to: File) {
        from.walkTopDown().forEach { src ->
            val dest = File(to, src.relativeTo(from).path)
            if (src.isDirectory) dest.mkdirs() else {
                dest.parentFile?.mkdirs()
                src.copyTo(dest, overwrite = true)
            }
        }
    }
}
