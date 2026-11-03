package io.github.ccch1mneyyy.dsh_app.rony

import io.github.ccch1mneyyy.dsh_app.rony.RootPolicy.Level.BLOCKED
import io.github.ccch1mneyyy.dsh_app.rony.RootPolicy.Level.DANGEROUS
import io.github.ccch1mneyyy.dsh_app.rony.RootPolicy.Level.READ_ONLY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootPolicyTest {
    private fun level(cmd: String) = RootPolicy.classify(cmd).level

    /** The table in RONY-FORK-README.md. */
    @Test fun readmeTable() {
        val table = mapOf(
            "id" to READ_ONLY,
            "pm list packages" to READ_ONLY,
            "settings get global airplane_mode_on" to READ_ONLY,
            "pm install /sdcard/x.apk" to DANGEROUS,
            "settings put global adb_enabled 1" to DANGEROUS,
            "ls /sdcard && cat /proc/cpuinfo" to DANGEROUS,
            "rm -rf /" to BLOCKED,
            "dd if=/dev/zero of=/dev/block/sda" to BLOCKED,
            "cat /data/data/com.x/shared_prefs/token.xml" to BLOCKED,
        )
        for ((cmd, want) in table) assertEquals(cmd, want, level(cmd))
    }

    @Test fun readOnlyPipelinesAndRootUse() {
        for (cmd in listOf(
            "dumpsys battery | grep level",
            "getprop ro.product.model",
            "ls -la /data/adb",
            "cat /proc/meminfo | head -5",
            "cmd package list packages -3",
            "logcat -d -t 100",
            "ps -A | grep com.tencent",
            "ip addr",
            "su -v",
            "df -h 2>/dev/null",
            "cat /data/system/packages.list",
            "dumpsys activity top 2>&1 | head -50",
            "top -n 1 -b",
        )) assertEquals(cmd, READ_ONLY, level(cmd))
    }

    @Test fun dangerous() {
        for (cmd in listOf(
            "echo 1 > /sys/class/leds/x/brightness",
            "pm disable-user com.x",
            "am force-stop com.x",
            "cmd package install-existing com.x",
            "dumpsys battery set level 5",
            "ip route add default via 1.2.3.4",
            "wm size 1080x2400",
            "chmod 644 /data/local/tmp/x",
            "cp /sdcard/a /data/local/tmp/a",
            "ls `whoami`",
            "ls \$(id -u)",
            "ls | sh",
            "find /data -name x -delete",
            "logcat -c",
            "env rm x",
            "reboot",
            "svc wifi disable",
            "su -c id",
            "sleep 1; id",
            "top",
            "content insert --uri content://settings/system --bind name:s:x --bind value:s:1",
        )) assertEquals(cmd, DANGEROUS, level(cmd))
    }

    @Test fun blocked() {
        for (cmd in listOf(
            "rm -rf /*",
            "rm -fr /system",
            "rm -r -f /data",
            "mkfs.ext4 /dev/x",
            "reboot bootloader",
            "reboot recovery",
            "fastboot flash boot boot.img",
            "cat /dev/block/by-name/boot > /sdcard/boot.img",
            "echo x > /dev/kmsg",
            "ksud boot-patch -b boot.img",
            "magisk --install-module x.zip",
            "cat /data/misc/keystore/x",
            "cat /data/system/locksettings.db",
            "cat /data/misc/wifi/WifiConfigStore.xml",
            "grep password /data/data/x/shared_prefs/a.xml",
            "",
        )) assertEquals(cmd, BLOCKED, level(cmd))
    }

    @Test fun noFalseBlocks() {
        // "add" contains "dd", "format" alone is not flashing, /dev/null is fine.
        assertEquals(DANGEROUS, level("ip route add 10.0.0.0/8 dev wlan0"))
        assertEquals(READ_ONLY, level("ls /dev/null 2>/dev/null"))
        assertEquals(DANGEROUS, level("echo hi > /dev/null"))
        assertEquals(READ_ONLY, level("dumpsys display | grep -i refresh"))
    }

    @Test fun redaction() {
        val r = RootPolicy.redact("curl -H token=abc123 password=hunter2 x")
        assertFalse(r.contains("abc123"))
        assertFalse(r.contains("hunter2"))
        assertTrue(RootPolicy.redact("x".repeat(400)).length <= 301)
    }

    @Test fun `Ace7 hide-env paths are blocked`() {
        assertEquals(RootPolicy.Level.BLOCKED, RootPolicy.classify("ls /data/adb/ksu").level)
        assertEquals(RootPolicy.Level.BLOCKED, RootPolicy.classify("setenforce 0").level)
        assertEquals(RootPolicy.Level.BLOCKED, RootPolicy.classify("resetprop ro.boot.vbmeta.digest x").level)
        assertEquals(RootPolicy.Level.BLOCKED, RootPolicy.classify("ksud module install /sdcard/x.zip").level)
        // version probes still allowed
        assertEquals(RootPolicy.Level.READ_ONLY, RootPolicy.classify("su -v").level)
        assertEquals(RootPolicy.Level.READ_ONLY, RootPolicy.classify("id").level)
    }
}
