package io.github.ccch1mneyyy.dsh_app.phone

import java.time.ZoneId
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Accessibility down is a status, not a failed shell command. */
class A11yDownHostTest {
    private val core = PhoneCore(object : PhoneEnv {
        override fun enabled() = true
        override fun policies() = PhonePolicies()
        override fun isLocked() = false
        override fun a11yState() = A11yState.ENABLED_NOT_CONNECTED
        override fun backgroundUnavailable() = "background-unavailable"
        override fun zone() = ZoneId.of("Asia/Shanghai")
        override fun notifyFix(kind: String) {}
        override fun sleep(ms: Long) {}
        override fun driver(screen: PhoneScreen): PhoneDriver? = null
        override fun gate(): UserGate? = null
        override fun system() = object : SystemApi {
            override fun apps() = emptyList<AppEntry>()
            override fun isInstalled(pkg: String) = false
            override fun launch(app: AppEntry) = SysResult(false, "no")
            override fun urlTarget(url: String) = error("no")
            override fun openUrl(url: String, target: UrlTarget) = SysResult(false, "no")
            override fun intent(spec: IntentSpec, shareTo: AppEntry?) = SysResult(false, "no")
            override fun notify(title: String, text: String) = SysResult(false, "no")
            override fun clipboardSet(text: String) = SysResult(false, "no")
            override fun clipboardGet() = SysResult(false, "no")
        }
    }, PhoneTasks(), ActionLog())

    @After fun close() = core.worker.shutdown()

    @Test
    fun `a disconnected accessibility service is not a command failure`() {
        val out = core.call(PhoneToolCatalog.SCREEN, emptyMap())
        assertFalse(out.isError)
        assertTrue(out.text.contains("无障碍"))
        assertTrue(out.text.contains("这不是命令失败"))
        assertTrue(out.text.contains("input tap"))
        assertTrue(out.text.contains("input keyevent"))
        assertTrue(out.text.contains("screencap"))
        assertTrue(out.text.contains("am start"))
    }
}
