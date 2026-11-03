package io.github.ccch1mneyyy.dsh_app.rony

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RonyPojiaTest {
    /** sha256 of DeepSeek-4.1-Flash-破甲提示词.md as downloaded from the owner's Drive. */
    private val expectedSha = "4fbd3a104fcd16065800e4e730a67f7781698300eec33af8dc67cfd4f328c118"

    /** The Flutter asset dir, found from the Gradle test working directory (app/android/app). */
    private fun assetDir(): File = listOf("../../assets/engine/pojia", "../assets/engine/pojia", "assets/engine/pojia", "app/assets/engine/pojia")
        .map { File(it) }.firstOrNull { File(it, RonyPojia.PROMPT).isFile }
        ?: error("assets/engine/pojia not found from ${File(".").absolutePath}")

    @Test fun bundledPromptIsTheDownloadedFile() {
        val bytes = File(assetDir(), RonyPojia.PROMPT).readBytes()
        assertTrue(bytes.size > 1000)
        assertEquals(expectedSha, RonyPojia.sha256(bytes))
        assertTrue(File(assetDir(), "index.mjs").readText().contains("order: SECTION_ORDER"))
    }

    @Test fun pubspecBundlesTheAssetDir() {
        val pubspec = File(assetDir(), "../../../pubspec.yaml").readText()
        assertTrue(pubspec.contains("- assets/engine/pojia/"))
    }

    @Test fun writesPluginPromptAndOverlay() {
        val rootfs = Files.createTempDirectory("pojia").toFile()
        try {
            val dir = assetDir()
            assertTrue(RonyPojia.writeFiles(rootfs) { name -> File(dir, name).readBytes() })
            val guest = File(rootfs, RonyPojia.GUEST_DIR.removePrefix("/"))
            assertArrayEquals(File(dir, RonyPojia.PROMPT).readBytes(), File(guest, RonyPojia.PROMPT).readBytes())
            assertEquals(expectedSha, RonyPojia.sha256(File(guest, RonyPojia.PROMPT).readBytes()))
            val yaml = RonyPojia.file(rootfs).readText()
            assertTrue(yaml, yaml.contains("name: /opt/dsh-app/rony/pojia/index.mjs"))
            RonyPojia.clear(rootfs)
            assertFalse(RonyPojia.file(rootfs).exists())
            // An empty or missing asset leaves no overlay (a missing --patch file would stop DSH).
            assertFalse(RonyPojia.writeFiles(rootfs) { ByteArray(0) })
            assertFalse(RonyPojia.file(rootfs).exists())
        } finally {
            rootfs.deleteRecursively()
        }
    }

    @Test fun launchArgAddsOverlayToDshWeb() {
        val arg = "exec /usr/local/bin/dsh web --patch /etc/dsh-app/dsh-web.patch.yml --patch /opt/dsh-app/rony/root.patch.yml --host 127.0.0.1"
        val out = RonyPojia.launchArg(arg)!!
        assertTrue(out, out.contains("--patch /opt/dsh-app/rony/pojia/pojia.patch.yml"))
        assertTrue(out.contains("--patch /opt/dsh-app/rony/root.patch.yml"))
        assertEquals(null, RonyPojia.launchArg("exec /usr/local/bin/dsh web --profile rescue"))
    }
}

class RonyRefreshDefaultTest {
    @Test fun refreshDefaultsToAuto() {
        assertEquals("auto", RonyPrefs.DEFAULT_REFRESH)
        assertTrue(RonyPrefs.REFRESH_MODES.containsAll(listOf("auto", "max", "system")))
        assertEquals("system", RefreshRate.effective("auto"))
        assertEquals("max", RefreshRate.effective("max"))
        assertEquals(700L, RefreshRate.IDLE_MS)
    }
}
