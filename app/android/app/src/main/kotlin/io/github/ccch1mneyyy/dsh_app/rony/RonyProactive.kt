package io.github.ccch1mneyyy.dsh_app.rony

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.ccch1mneyyy.dsh_app.MainActivity
import io.github.ccch1mneyyy.dsh_app.R
import java.util.Calendar

/**
 * Rony fork (v5.1): 活人感的「主动找你」. An inexact, Doze-friendly alarm every ~3 h; when 活人感 is
 * on, the owner has been away from the in-app chat for a while and it is daytime, one short
 * check-in notification (at most one a day). 活人感 off: the alarm is cancelled and [decide] says
 * no, so nothing is ever posted. Tapping opens the app.
 */
object RonyProactive {
    const val CHANNEL = "rony_proactive"
    private const val REQ = 0x50A1
    const val INTERVAL_MS = 3L * 60 * 60 * 1000
    /** Away at least this long before a check-in. */
    const val QUIET_MS = 8L * 60 * 60 * 1000
    /** At most one check-in per this window. */
    const val GAP_MS = 20L * 60 * 60 * 1000

    /** The whole rule, pure for tests. [hour] is local 0–23. */
    fun decide(humanlike: Boolean, now: Long, lastUser: Long, lastProactive: Long, hour: Int): Boolean =
        humanlike && lastUser > 0 && now - lastUser >= QUIET_MS && now - lastProactive >= GAP_MS && hour in 9..22

    /** A short, time-aware line (no model call: the engine may be stopped). */
    fun message(hour: Int, seed: Long): String {
        val pool = when (hour) {
            in 9..11 -> listOf("早呀，今天打算干点啥？", "醒了没～有事随时叫我。", "上午好，要不要我帮你把今天的事理一理？")
            in 12..13 -> listOf("午饭吃了吗？", "中午了，歇会儿再忙。")
            in 14..17 -> listOf("下午好，忙完了来找我聊聊？", "有啥卡住的事，丢给我试试。")
            else -> listOf("晚上好，今天过得怎么样？", "有空来聊两句？", "今天辛苦啦，有事随时叫我。")
        }
        return pool[(seed % pool.size).toInt().let { if (it < 0) -it else it }]
    }

    /** Arm or cancel the alarm to match the switch. */
    fun sync(context: Context, prefs: RonyPrefs) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = PendingIntent.getBroadcast(context, REQ, Intent(context, RonyProactiveReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        am.cancel(pi)
        if (prefs.humanlike) {
            am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME, android.os.SystemClock.elapsedRealtime() + INTERVAL_MS, INTERVAL_MS, pi)
        }
    }

    fun check(context: Context) {
        val prefs = RonyPrefs(context)
        val now = System.currentTimeMillis()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (!decide(prefs.humanlike, now, prefs.lastUserMessageAt, prefs.lastProactiveAt, hour)) return
        prefs.lastProactiveAt = now
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "主动找你（活人感）", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "活人感开着时，很久没聊会偶尔来打个招呼（每天最多一次）"
            })
        }
        val open = PendingIntent.getActivity(context, REQ, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(context, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(context)
        val name = prefs.personaText.lineSequence().firstOrNull()?.let { Regex("你叫\\s*([^，,。\\s]{1,12})").find(it)?.groupValues?.get(1) } ?: "Rony"
        nm.notify(REQ, b.setSmallIcon(R.drawable.ic_stat_dsh).setContentTitle(name).setContentText(message(hour, now / 3_600_000)).setContentIntent(open).setAutoCancel(true).build())
    }
}

class RonyProactiveReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runCatching { RonyProactive.check(context.applicationContext) }
    }
}
