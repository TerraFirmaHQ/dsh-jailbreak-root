package io.github.ccch1mneyyy.dsh_app.rony

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.github.ccch1mneyyy.dsh_app.MainActivity
import java.util.concurrent.Executors

/**
 * Rony ace7: MethodChannel `dsh/rony` for 设置 → Ace7 / Root 与 MCP.
 * QQ/SnowLuma/NapCat/Aurabot removed. Root probes never run while root mode is off.
 */
class RonyPlugin : FlutterPlugin, MethodChannel.MethodCallHandler {
    private lateinit var channel: MethodChannel
    private lateinit var app: Context
    private lateinit var control: RonyControl
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newCachedThreadPool { r -> Thread(r, "rony-plugin").apply { isDaemon = true } }

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        app = binding.applicationContext
        control = RonyControl.get(app)
        channel = MethodChannel(binding.binaryMessenger, "dsh/rony").also { it.setMethodCallHandler(this) }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    private fun async(result: MethodChannel.Result, block: () -> Any?) {
        io.execute {
            val r = runCatching(block)
            main.post {
                r.fold({ result.success(it) }, { result.error("rony", it.message ?: it.javaClass.simpleName, null) })
            }
        }
    }

    private fun state(): Map<String, Any?> {
        val p = control.prefs
        val activity = MainActivity.current?.get()
        return linkedMapOf(
            "package" to app.packageName,
            "rootEnabled" to p.rootEnabled,
            "approval" to p.approval,
            "autoRequested" to p.autoRequested,
            // status() only reads prefs + File.exists for su — never executes su.
            "root" to control.core.status(),
            "rootMounted" to control.rootMounted,
            "bridgeListening" to control.bridgeListening,
            "bridgeConnections" to control.bridgeConnections,
            "mcpMounted" to control.mcpMounted,
            "mcpServers" to p.mcpServers().map { it.toMap(withToken = false) },
            "refreshMode" to p.refreshMode,
            "refreshBoosted" to RefreshRate.boosted,
            "pojiaEnabled" to p.pojiaEnabled,
            "pojiaMounted" to control.pojiaMounted,
            "pojia" to RonyPojia.info(app),
            "pojiaLocked" to true,
            "refresh" to RefreshRate.info(activity),
            "sdkInt" to Build.VERSION.SDK_INT,
            "linuxTask" to control.linuxTask,
            "linuxRoot" to RonyLinux.ROOT,
            "linuxProxy" to p.linuxProxy,
            "chrootEnhance" to p.chrootEnhance,
            "humanlike" to p.humanlike,
            "personaText" to p.personaText,
            "personaDefault" to "",
            "personaMounted" to control.personaMounted,
            "trustWindow" to RootApproval.trusted(),
            "deviceModel" to Build.MODEL,
            "deviceProduct" to Build.PRODUCT,
            "deviceBrand" to Build.BRAND,
            "ace7" to Ace7Probe.snapshot(activity, p.rootEnabled),
        )
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        val p = control.prefs
        when (call.method) {
            "state" -> async(result) { state() }
            "setRootEnabled" -> async(result) {
                val on = call.argument<Boolean>("on") == true
                p.rootEnabled = on
                // Only when turning ON: one automatic su request (KSU grant prompt).
                val check = if (on) control.core.autoRequestOnce() else null
                state() + mapOf("check" to check)
            }
            "setApproval" -> async(result) {
                val on = call.argument<Boolean>("on") != false
                p.approval = on
                if (on) RootApproval.clearTrust()
                state()
            }
            // 破甲锁死：允许总开关（默认开），不允许改文案；UI 只提供只读展示。
            "setPojiaEnabled" -> async(result) {
                p.pojiaEnabled = call.argument<Boolean>("on") != false
                state()
            }
            "checkRoot" -> async(result) {
                if (!p.rootEnabled) mapOf("state" to "root_mode_off", "granted" to false, "detail" to "Root 模式关着：不会探测 su、不会弹 KSU 授权")
                else control.core.checkRoot()
            }
            "ace7SelfCheck" -> async(result) {
                val activity = MainActivity.current?.get()
                Ace7Probe.selfCheck(activity, p.rootEnabled) {
                    if (p.rootEnabled) control.core.checkRoot() else null
                }
            }
            "linuxStatus" -> async(result) {
                if (!p.rootEnabled) mapOf("state" to "root_mode_off", "detail" to "Root 模式关着", "task" to control.linuxTask)
                else {
                    val st = control.core.linuxOp(listOf("status"), 60_000)
                    val pf = control.core.linuxOp(listOf("preflight"), 60_000)
                    mapOf("status" to RonyLinux.parse(st.stdout), "preflight" to RonyLinux.parse(pf.stdout), "state" to st.state.wire, "detail" to st.detail, "task" to control.linuxTask)
                }
            }
            "linuxInstall" -> async(result) {
                if (!p.rootEnabled) mapOf("error" to "root_mode_off") else { control.installLinux(); state() }
            }
            "linuxTools" -> async(result) {
                if (!p.rootEnabled) mapOf("error" to "root_mode_off") else mapOf("jobId" to control.installLinuxTools())
            }
            "linuxJob" -> async(result) {
                RootExec.job(call.argument<String>("id").orEmpty())?.let { j -> j.toMap() + mapOf("output" to j.output(6000).first) }
            }
            "linuxWipe" -> async(result) {
                if (!p.rootEnabled) mapOf("error" to "root_mode_off") else control.core.linuxOp(listOf("wipe"), 600_000).toMap()
            }
            "setLinuxProxy" -> async(result) { p.linuxProxy = call.argument<String>("proxy").orEmpty(); state() }
            "setChrootEnhance" -> async(result) {
                // Skeleton only in ace7 v1: flag stored, chroot+unshare path not wired yet (see README).
                p.chrootEnhance = call.argument<Boolean>("on") == true
                state()
            }
            "setHumanlike" -> async(result) {
                p.humanlike = call.argument<Boolean>("on") == true
                state()
            }
            "setPersonaText" -> async(result) { p.personaText = call.argument<String>("text").orEmpty(); state() }
            "touchUserMessage" -> {
                p.lastUserMessageAt = System.currentTimeMillis()
                result.success(null)
            }
            "runRoot" -> async(result) {
                if (!p.rootEnabled) mapOf("decision" to "refused", "detail" to "Root 模式关着")
                else control.core.manualRun(call.argument<String>("command").orEmpty())
            }
            "audit" -> async(result) { p.auditLog() }
            "clearAudit" -> async(result) { p.clearAudit(); true }
            "setMcpServers" -> async(result) {
                val old = p.mcpServers().associateBy { it.name }
                val list = (call.argument<List<Any?>>("servers") ?: emptyList()).mapNotNull { raw ->
                    val m = raw as? Map<*, *> ?: return@mapNotNull null
                    val e = McpServerEntry.fromMap(m) ?: return@mapNotNull null
                    if (e.token == null && m["keepToken"] == true) e.copy(token = old[e.name]?.token) else e
                }.distinctBy { it.name }
                p.setMcpServers(list)
                state()
            }
            "mcpToken" -> async(result) { p.mcpServers().firstOrNull { it.name == call.argument<String>("name") }?.token }
            "motion" -> {
                MainActivity.current?.get()?.let { RefreshRate.motion(it, p.refreshMode) }
                result.success(null)
            }
            "setRefreshMode" -> {
                p.refreshMode = call.argument<String>("mode") ?: "auto"
                MainActivity.current?.get()?.preferFastestDisplayMode()
                main.postDelayed({ result.success(state()) }, 300)
            }
            "setRefreshModeId" -> {
                // Ace7: pin a specific Display.Mode id (e.g. 165Hz). 0 = clear pin, fall back to refreshMode.
                p.refreshModeId = call.argument<Int>("modeId") ?: 0
                MainActivity.current?.get()?.preferFastestDisplayMode()
                main.postDelayed({ result.success(state()) }, 300)
            }
            else -> result.notImplemented()
        }
    }
}

/** Ace7 Pro probe: real Display modes (incl. 165Hz if ColorOS exposes them) + root-mode-gated su visibility. */
object Ace7Probe {
    fun snapshot(activity: MainActivity?, rootEnabled: Boolean): Map<String, Any?> {
        val modes = displayModes(activity)
        return linkedMapOf(
            "model" to Build.MODEL,
            "product" to Build.PRODUCT,
            "brand" to Build.BRAND,
            "device" to Build.DEVICE,
            "modes" to modes,
            "maxHz" to (modes.maxOfOrNull { (it["refreshRate"] as? Number)?.toDouble() ?: 0.0 } ?: 0.0),
            "rootMode" to rootEnabled,
            // Never probe su binary visibility? File.exists is fine; still only report when root mode on per owner request.
            "suVisible" to if (rootEnabled) (RootExec.visibleSu() != null) else null,
            "suPath" to if (rootEnabled) RootExec.visibleSu() else null,
            "hideBlacklist" to true,
            "note" to "Ace7Pro 适配：刷新率跟随系统/可钉模式；Root 默认关；隐藏环境硬黑名单始终生效。未实机验证 165Hz 是否对第三方 App 开放。",
        )
    }

    fun selfCheck(activity: MainActivity?, rootEnabled: Boolean, checkRoot: () -> Map<String, Any?>?): Map<String, Any?> {
        val snap = snapshot(activity, rootEnabled)
        val root = if (rootEnabled) checkRoot() else mapOf("state" to "root_mode_off", "granted" to false)
        return snap + mapOf(
            "rootCheck" to root,
            "checkedAt" to System.currentTimeMillis(),
            "checklist" to listOf(
                "刷新率模式列表（看有没有 ~165）",
                "Root 模式开关状态（默认关）",
                "su 可见性（仅 Root 模式开时检测）",
                "隐藏环境黑名单已编译进 RootPolicy",
                "装前装后请用春秋检测 / Key Attestation 对比",
            ),
        )
    }

    private fun displayModes(activity: MainActivity?): List<Map<String, Any?>> {
        activity ?: return emptyList()
        return try {
            @Suppress("DEPRECATION")
            val display = activity.windowManager.defaultDisplay
            val now = display.mode
            display.supportedModes.map { m ->
                linkedMapOf(
                    "modeId" to m.modeId,
                    "width" to m.physicalWidth,
                    "height" to m.physicalHeight,
                    "refreshRate" to m.refreshRate.toDouble(),
                    "current" to (m.modeId == now.modeId),
                )
            }.sortedByDescending { (it["refreshRate"] as Number).toDouble() }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
