package io.github.ccch1mneyyy.dsh_app.hostroot

import java.util.Base64

/**
 * Structured host-root file operations.
 *
 * The path is one quoted argument. File bytes are base64, so they are never
 * parsed as shell: a heredoc delimiter, quote, or `$(...)` inside the file
 * cannot end the command or run. This is not a sandbox. It only keeps the
 * payload out of the shell grammar.
 */
object RootFiles {
    const val MAX_READ = 256 * 1024
    const val MAX_CHUNK = 1024 * 1024

    fun rejectPath(path: String): String? = when {
        path.isEmpty() -> "empty"
        path.any { it == '\u0000' || it == '\n' || it == '\r' } -> "control character"
        !path.startsWith("/") -> "relative"
        else -> null
    }

    /** Fixed read. `dd if=` plus a quoted path; no model-supplied shell. */
    fun sizeCommand(path: String): String {
        rejectPath(path)?.let { error(it) }
        return "stat -c %s ${RootExec.quote(path)}"
    }

    fun readCommand(path: String, offset: Long = 0, limit: Int = MAX_READ): String {
        rejectPath(path)?.let { error(it) }
        val count = limit.coerceIn(1, MAX_CHUNK)
        val skip = offset.coerceAtLeast(0)
        return if (skip == 0L && count == MAX_READ) {
            val blocks = (MAX_READ + 4095) / 4096
            "dd if=${RootExec.quote(path)} bs=4096 count=$blocks status=none"
        } else {
            "dd if=${RootExec.quote(path)} bs=1 skip=$skip count=$count status=none"
        }
    }

    /**
     * Writes [content] exactly. The shell only sees base64 inside single quotes.
     * Base64 (basic) has no quotes, newlines, or `$`.
     */
    fun writeCommand(path: String, content: ByteArray): String {
        rejectPath(path)?.let { error(it) }
        val b64 = Base64.getEncoder().encodeToString(content)
        return "printf %s ${RootExec.quote(b64)} | base64 -d > ${RootExec.quote(path)}"
    }

    /** The base64 payload embedded by [writeCommand], or null if the shape changed. */
    fun payload(script: String): ByteArray? {
        val marker = "printf %s '"
        val start = script.indexOf(marker)
        if (start < 0) return null
        val from = start + marker.length
        val end = script.indexOf("' | base64 -d > ", from)
        if (end < 0) return null
        val b64 = script.substring(from, end)
        if (b64.any { it == '\'' || it == '\n' || it == '$' || it == '`' }) return null
        return Base64.getDecoder().decode(b64)
    }
}
