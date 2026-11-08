package io.github.ccch1mneyyy.dsh_app.hostroot

import android.content.Context
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

/** MethodChannel `dsh/hostroot`. Does not probe su from a status read. */
class HostRootPlugin : FlutterPlugin, MethodChannel.MethodCallHandler, EventChannel.StreamHandler {
    private lateinit var methods: MethodChannel
    private lateinit var events: EventChannel
    private lateinit var app: Context
    private var sink: EventChannel.EventSink? = null

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        app = binding.applicationContext
        methods = MethodChannel(binding.binaryMessenger, "dsh/hostroot")
        methods.setMethodCallHandler(this)
        events = EventChannel(binding.binaryMessenger, "dsh/hostroot/events")
        events.setStreamHandler(this)
        HostRootControl.onApproval = { event ->
            val target = sink
            if (target != null) {
                android.os.Handler(android.os.Looper.getMainLooper()).post { target.success(event) }
            }
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        methods.setMethodCallHandler(null)
        events.setStreamHandler(null)
        if (HostRootControl.onApproval != null) HostRootControl.onApproval = null
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        sink = events
    }

    override fun onCancel(arguments: Any?) {
        sink = null
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "status" -> result.success(HostRootControl.status(app))
            "setRootEnabled" -> {
                val on = call.argument<Boolean>("on") == true
                val error = HostRootControl.setRootEnabled(app, on)
                result.success(mapOf("ok" to (error == null), "error" to error))
            }
            "setChrootEnabled" -> {
                val on = call.argument<Boolean>("on") == true
                val error = HostRootControl.setChrootEnabled(app, on, call.argument<String>("runtime"))
                result.success(mapOf("ok" to (error == null), "error" to error))
            }
            "setPersona" -> {
                HostRootControl.setPersona(app, call.argument<String>("text") ?: "")
                result.success(null)
            }
            "setAutoAllow" -> {
                HostRootControl.setAutoAllow(app, call.argument<Boolean>("on") == true)
                result.success(mapOf("ok" to true))
            }
            "readChannelPrompts" -> result.success(HostRootControl.readChannelPrompts(app))
            "writeChannelPrompts" -> {
                HostRootControl.writeChannelPrompts(app, call.argument<String>("json") ?: "")
                result.success(mapOf("ok" to true))
            }
            "readAudit" -> result.success(HostRootControl.readAudit(app))
            "clearAudit" -> {
                HostRootControl.clearAudit(app)
                result.success(mapOf("ok" to true))
            }
            "setReadonlySkip" -> {
                HostRootControl.setReadonlySkip(app, call.argument<Boolean>("on") == true)
                result.success(mapOf("ok" to true))
            }
            "clearSessionMemory" -> {
                HostRootControl.clearSessionMemory()
                result.success(mapOf("ok" to true))
            }
            "resolveApproval" -> {
                val id = call.argument<String>("id").orEmpty()
                val accept = call.argument<Boolean>("accept") == true
                val session = call.argument<Boolean>("session") == true
                result.success(HostRootControl.resolveApproval(id, accept, session))
            }
            "applyBattery" -> {
                val id = call.argument<String>("id").orEmpty()
                result.success(HostRootControl.applyBattery(app, id))
            }
            "migrationPlan" -> {
                val plan = MigrationPreflight.plan()
                result.success(
                    mapOf(
                        "keep" to plan.keep,
                        "drop" to plan.drop,
                        "scheduleDowngrade" to plan.scheduleDowngrade,
                    ),
                )
            }
            else -> result.notImplemented()
        }
    }
}
