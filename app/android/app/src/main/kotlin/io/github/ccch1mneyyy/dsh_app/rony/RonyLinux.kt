package io.github.ccch1mneyyy.dsh_app.rony

import android.content.Context
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Rony fork (v5.0): the root Linux environment. Debian Trixie arm64 (the pinned proot-distro
 * tarball, sha256-checked) unpacked to [ROOT] and entered with `unshare -m` + `chroot` through the
 * bundled `rony-env.sh` (asset `assets/engine/linux/`). Mounts live in a private namespace and die
 * with the command. Without root (or when the kernel refuses unshare) the backend is `proot`: the
 * engine's own Ubuntu rootfs (the bash tool), which is always there.
 */
object RonyLinux {
    const val ROOT = "/data/local/rony/debian"
    const val ASSET = "flutter_assets/assets/engine/linux/rony-env.sh"

    const val TARBALL_NAME = "debian-trixie-aarch64-pd-v4.29.0.tar.xz"
    const val TARBALL_SHA256 = "3834a11cbc6496935760bdc20cca7e2c25724d0cd8f5e4926da8fd5ca1857918"
    const val TARBALL_BYTES = 35_409_704L
    private const val OFFICIAL = "https://github.com/termux/proot-distro/releases/download/v4.29.0/$TARBALL_NAME"
    /** Official URL last; the checksum makes any mirror safe. */
    val TARBALL_URLS = listOf("https://ghfast.top/$OFFICIAL", "https://gh-proxy.com/$OFFICIAL", OFFICIAL)

    /** 常用工具 (one apt run, TUNA mirror). */
    const val CORE_PACKAGES = "ca-certificates curl wget git busybox sqlite3 file unzip zip xz-utils tmux procps psmisc " +
        "openssh-client python3 python3-pip python3-venv nodejs npm build-essential jq less nano vim-tiny iproute2 iputils-ping dnsutils"

    fun script(context: Context): File {
        val dir = File(context.filesDir, "rony").apply { mkdirs() }
        val f = File(dir, "rony-env.sh")
        // ace7: only rony-env.sh (QQ stack intentionally not shipped).
        val bytes = context.assets.open(ASSET).use { it.readBytes() }
        if (!f.isFile || !f.readBytes().contentEquals(bytes)) f.writeBytes(bytes)
        f.setReadable(true, false)
        f.setExecutable(true, false)
        dir.setExecutable(true, false)
        context.filesDir.setExecutable(true, false)
        return f
    }

    /** `sh <script> <args…>` with the environment the script reads. Pure, so it is unit-tested. */
    fun command(script: String, args: List<String>, env: Map<String, String> = emptyMap()): String = buildString {
        for ((k, v) in env) if (v.isNotEmpty()) append(k).append('=').append(RootExec.quote(v)).append(' ')
        append("sh ").append(RootExec.quote(script))
        for (a in args) append(' ').append(RootExec.quote(a))
    }

    fun env(prefs: RonyPrefs, extra: Map<String, String> = emptyMap()): Map<String, String> =
        linkedMapOf("RONY_ROOT" to ROOT, "RONY_PROXY" to prefs.linuxProxy) + extra

    /** key=value lines (the script's output format) as a map. */
    fun parse(out: String): Map<String, String> = out.lineSequence().mapNotNull { l ->
        val i = l.indexOf('='); if (i <= 0 || l.substring(0, i).contains(' ')) null else l.substring(0, i).trim() to l.substring(i + 1).trim()
    }.toMap()

    /** Download the pinned tarball into [dir], verified; tries each mirror. [progress] gets (bytes, total). */
    fun download(dir: File, progress: (Long, Long) -> Unit = { _, _ -> }): File {
        dir.mkdirs()
        val target = File(dir, TARBALL_NAME)
        if (target.isFile && target.length() == TARBALL_BYTES && sha256(target) == TARBALL_SHA256) return target
        var last: Exception? = null
        for (u in TARBALL_URLS) {
            try {
                val tmp = File(dir, "$TARBALL_NAME.part")
                val c = (URL(u).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000; readTimeout = 30_000; instanceFollowRedirects = true
                }
                c.inputStream.use { input ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024); var n: Int; var got = 0L
                        while (input.read(buf).also { n = it } >= 0) { out.write(buf, 0, n); got += n; progress(got, TARBALL_BYTES) }
                    }
                }
                val sum = sha256(tmp)
                if (sum != TARBALL_SHA256) { tmp.delete(); throw IOException("sha256 mismatch from $u") }
                if (!tmp.renameTo(target)) throw IOException("rename failed")
                target.setReadable(true, false)
                return target
            } catch (e: Exception) {
                last = e
            }
        }
        throw IOException("download failed: ${last?.message}")
    }

    fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { i -> val b = ByteArray(64 * 1024); var n: Int; while (i.read(b).also { n = it } >= 0) md.update(b, 0, n) }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    // l10n:keep agent config comment
    const val AGENTS_SECTION = """

## Rony Linux 环境（可选，默认关）

官方引擎自带 proot Linux。chroot+unshare 增强在设置里有独立开关，ace7 第一版仅保留骨架/安装入口；默认不启用，不影响隐藏环境。
"""
}
