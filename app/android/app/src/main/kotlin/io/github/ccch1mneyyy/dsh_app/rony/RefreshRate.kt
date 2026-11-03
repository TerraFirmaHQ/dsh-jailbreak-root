package io.github.ccch1mneyyy.dsh_app.rony

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup

/**
 * Rony fork: 刷新率. Upstream only picks the fastest display mode, which several ROMs (ColorOS /
 * OxygenOS, HyperOS, OriginOS) treat as a hint and still drop non-whitelisted apps to 60/90 Hz.
 *
 * - `auto` (default since v4, like official 1.0.6): the `max` request only while something moves
 *   (the app reports sustained frames: scrolling, animations, streaming output over `dsh/rony`
 *   `motion`), handed back to the system [IDLE_MS] after the last one. Idle = `system`.
 * - `max`: always the fastest mode at the current resolution + `preferredRefreshRate`, the Flutter
 *   SurfaceView's own frame rate (Android 11+, re-applied when the surface is recreated) and the
 *   "high" frame-rate category on the views (Android 15+). Costs battery/heat; 8.3 ms frame budget.
 * - `system`: ask for nothing.
 *
 * The phone's own refresh-rate setting, power saving and thermal limits still cap all of this.
 */
object RefreshRate {
    private const val TAG_KEY = 0x52_4f_4e_59 // "RONY"

    /** `auto`: how long after the last motion report the boost is handed back (official 1.0.6: 0.7 s). */
    const val IDLE_MS = 700L

    private val main by lazy { Handler(Looper.getMainLooper()) }
    @Volatile var boosted = false
        private set
    private var target: java.lang.ref.WeakReference<Activity>? = null
    private val drop = Runnable {
        boosted = false
        target?.get()?.takeIf { !it.isFinishing }?.let { runCatching { applyInner(it, "system") } }
    }

    /** What `auto` asks for right now. */
    fun effective(mode: String): String = if (mode == "auto") (if (boosted) "max" else "system") else mode

    fun apply(activity: Activity, mode: String, modeId: Int = 0) {
        if (mode != "auto" || modeId != 0) {
            main.removeCallbacks(drop)
            boosted = false
        }
        // Ace7: an explicit Display.Mode id (e.g. 165Hz) wins over auto/max/system.
        if (modeId != 0) {
            runCatching { applyModeId(activity, modeId) }
            return
        }
        runCatching { applyInner(activity, effective(mode)) }
    }

    /** Pin a specific Display.Mode id from [Display.getSupportedModes]. */
    private fun applyModeId(activity: Activity, modeId: Int) {
        @Suppress("DEPRECATION")
        val display = activity.windowManager.defaultDisplay
        val mode = display.supportedModes.firstOrNull { it.modeId == modeId } ?: return
        val window = activity.window
        val attrs = window.attributes
        if (attrs.preferredDisplayModeId != mode.modeId || attrs.preferredRefreshRate != mode.refreshRate) {
            window.attributes = attrs.apply { preferredDisplayModeId = mode.modeId; preferredRefreshRate = mode.refreshRate }
        }
        surfaces(window.decorView) { setSurfaceRate(it, mode.refreshRate) }
        if (Build.VERSION.SDK_INT >= 35) views(window.decorView) { it.setRequestedFrameRate(View.REQUESTED_FRAME_RATE_CATEGORY_HIGH) }
    }

    /** `auto` only (main thread): something is moving; boost now, drop [IDLE_MS] after the last report. */
    fun motion(activity: Activity, mode: String) {
        if (mode != "auto") return
        target = java.lang.ref.WeakReference(activity)
        main.removeCallbacks(drop)
        if (!boosted) {
            boosted = true
            runCatching { applyInner(activity, "max") }
        }
        main.postDelayed(drop, IDLE_MS)
    }

    /** Highest refresh rate the screen supports at its current resolution. */
    fun fastest(activity: Activity): Float? = runCatching {
        @Suppress("DEPRECATION")
        val display = activity.windowManager.defaultDisplay
        val now = display.mode
        display.supportedModes.filter { it.physicalWidth == now.physicalWidth && it.physicalHeight == now.physicalHeight }
            .maxOfOrNull { it.refreshRate }
    }.getOrNull()

    fun info(activity: Activity?): Map<String, Any?> {
        if (activity == null) return emptyMap()
        return runCatching {
            @Suppress("DEPRECATION")
            val display = activity.windowManager.defaultDisplay
            linkedMapOf(
                "current" to display.refreshRate.toDouble(),
                "max" to (fastest(activity)?.toDouble()),
                "modes" to display.supportedModes.map { it.refreshRate.toDouble() }.distinct().sorted(),
                "modeDetails" to display.supportedModes.map {
                    linkedMapOf(
                        "modeId" to it.modeId,
                        "width" to it.physicalWidth,
                        "height" to it.physicalHeight,
                        "refreshRate" to it.refreshRate.toDouble(),
                        "current" to (it.modeId == display.mode.modeId),
                    )
                }.sortedByDescending { (it["refreshRate"] as Number).toDouble() },
                "preferredDisplayModeId" to activity.window.attributes.preferredDisplayModeId,
            )
        }.getOrDefault(emptyMap())
    }

    private fun applyInner(activity: Activity, mode: String) {
        @Suppress("DEPRECATION")
        val display = activity.windowManager.defaultDisplay
        val now = display.mode
        val fastest = display.supportedModes
            .filter { it.physicalWidth == now.physicalWidth && it.physicalHeight == now.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return
        val window = activity.window
        val attrs = window.attributes
        when (mode) {
            "system" -> {
                if (attrs.preferredDisplayModeId != 0 || attrs.preferredRefreshRate != 0f) {
                    window.attributes = attrs.apply { preferredDisplayModeId = 0; preferredRefreshRate = 0f }
                }
                surfaces(window.decorView) { setSurfaceRate(it, 0f) }
                if (Build.VERSION.SDK_INT >= 35) views(window.decorView) { it.setRequestedFrameRate(View.REQUESTED_FRAME_RATE_CATEGORY_DEFAULT) }
            }
            "auto" -> {
                if (attrs.preferredDisplayModeId != fastest.modeId || attrs.preferredRefreshRate != 0f) {
                    window.attributes = attrs.apply { preferredDisplayModeId = fastest.modeId; preferredRefreshRate = 0f }
                }
                surfaces(window.decorView) { setSurfaceRate(it, 0f) }
                if (Build.VERSION.SDK_INT >= 35) views(window.decorView) { it.setRequestedFrameRate(View.REQUESTED_FRAME_RATE_CATEGORY_DEFAULT) }
            }
            else -> {
                if (attrs.preferredDisplayModeId != fastest.modeId || attrs.preferredRefreshRate != fastest.refreshRate) {
                    window.attributes = attrs.apply { preferredDisplayModeId = fastest.modeId; preferredRefreshRate = fastest.refreshRate }
                }
                surfaces(window.decorView) { setSurfaceRate(it, fastest.refreshRate) }
                if (Build.VERSION.SDK_INT >= 35) views(window.decorView) { it.setRequestedFrameRate(View.REQUESTED_FRAME_RATE_CATEGORY_HIGH) }
            }
        }
    }

    private fun views(v: View, block: (View) -> Unit) {
        block(v)
        if (v is ViewGroup) for (i in 0 until v.childCount) views(v.getChildAt(i), block)
    }

    private fun surfaces(v: View, block: (SurfaceView) -> Unit) {
        if (v is SurfaceView) block(v)
        if (v is ViewGroup) for (i in 0 until v.childCount) surfaces(v.getChildAt(i), block)
    }

    /** Sets (0 = clears) the surface's frame rate now and whenever Flutter recreates the surface. */
    private fun setSurfaceRate(view: SurfaceView, rate: Float) {
        if (Build.VERSION.SDK_INT < 30) return
        view.setTag(TAG_KEY, rate)
        fun set(surface: Surface?) {
            if (surface == null || !surface.isValid) return
            runCatching {
                val r = (view.getTag(TAG_KEY) as? Float) ?: 0f
                if (Build.VERSION.SDK_INT >= 31) {
                    surface.setFrameRate(r, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT, Surface.CHANGE_FRAME_RATE_ALWAYS)
                } else {
                    surface.setFrameRate(r, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
                }
            }
        }
        set(view.holder.surface)
        if (view.getTag(TAG_KEY + 1) == null) {
            view.setTag(TAG_KEY + 1, true)
            view.holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) = set(holder.surface)
                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = set(holder.surface)
                override fun surfaceDestroyed(holder: SurfaceHolder) {}
            })
        }
    }
}
