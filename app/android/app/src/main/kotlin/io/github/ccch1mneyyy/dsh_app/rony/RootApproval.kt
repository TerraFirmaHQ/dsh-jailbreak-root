package io.github.ccch1mneyyy.dsh_app.rony

import android.app.Activity
import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.TypefaceSpan
import io.github.ccch1mneyyy.dsh_app.R
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Rony fork: the approval card for dangerous root commands. Shown two ways at once, whichever the
 * person reaches first wins: a dialog activity (started directly; Android lets it through while
 * the app is in front or holds the 悬浮窗 permission) and a high-priority notification with
 * 允许一次 / 10 分钟内都允许 / 拒绝 buttons. No answer within [TIMEOUT_MS] is a denial.
 */
object RootApproval {
    enum class Decision(val wire: String) { ALLOW_ONCE("allow_once"), ALLOW_10MIN("allow_10min"), DENY("deny"), TIMEOUT("timeout") }

    const val TIMEOUT_MS = 90_000L
    const val TRUST_MS = 10 * 60_000L
    private const val CHANNEL = "rony-root-approval"
    const val EXTRA_ID = "rony_approval_id"
    const val EXTRA_DECISION = "rony_approval_decision"

    class Pending(val id: Int, val title: String, val command: String, val reason: String) {
        val latch = CountDownLatch(1)
        @Volatile var decision: Decision = Decision.TIMEOUT
    }

    private val pending = ConcurrentHashMap<Int, Pending>()
    private val seq = AtomicInteger(7300)
    @Volatile private var trustedUntil = 0L

    fun trusted(): Boolean = SystemClock.elapsedRealtime() < trustedUntil
    fun clearTrust() { trustedUntil = 0 }

    fun get(id: Int): Pending? = pending[id]

    fun resolve(context: Context, id: Int, decision: Decision) {
        val p = pending[id] ?: return
        if (p.latch.count == 0L) return
        p.decision = decision
        if (decision == Decision.ALLOW_10MIN) trustedUntil = SystemClock.elapsedRealtime() + TRUST_MS
        p.latch.countDown()
        runCatching { (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(id) }
    }

    /** Blocks the calling (tool) thread until the person answers or [TIMEOUT_MS] passes. */
    fun request(context: Context, title: String, command: String, reason: String): Decision {
        val app = context.applicationContext
        val p = Pending(seq.incrementAndGet(), title, command, reason)
        pending[p.id] = p
        try {
            notify(app, p)
            runCatching {
                app.startActivity(
                    Intent(app, RootApprovalActivity::class.java).putExtra(EXTRA_ID, p.id)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY),
                )
            }
            p.latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            return p.decision
        } finally {
            pending.remove(p.id)
            runCatching { (app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(p.id) }
        }
    }

    private fun notify(app: Context, p: Pending) {
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Root 危险命令审批", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "AI 要以 Root 执行危险命令时在这里请你批准"
            })
        }
        fun action(decision: Decision, req: Int): PendingIntent = PendingIntent.getBroadcast(
            app, p.id * 10 + req,
            Intent(app, RootApprovalReceiver::class.java).putExtra(EXTRA_ID, p.id).putExtra(EXTRA_DECISION, decision.wire),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val open = PendingIntent.getActivity(
            app, p.id * 10,
            Intent(app, RootApprovalActivity::class.java).putExtra(EXTRA_ID, p.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(app, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(app).setPriority(Notification.PRIORITY_MAX)
        val n = b.setSmallIcon(R.drawable.ic_stat_dsh)
            .setContentTitle(p.title)
            .setContentText(p.command.take(200))
            .setStyle(Notification.BigTextStyle().bigText("${p.command.take(1500)}\n\n原因：${p.reason}"))
            .setCategory(Notification.CATEGORY_CALL)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .setAutoCancel(false)
            .setOngoing(true)
            .setTimeoutAfter(TIMEOUT_MS)
            .addAction(Notification.Action.Builder(null, "允许一次", action(Decision.ALLOW_ONCE, 1)).build())
            .addAction(Notification.Action.Builder(null, "10 分钟内都允许", action(Decision.ALLOW_10MIN, 2)).build())
            .addAction(Notification.Action.Builder(null, "拒绝", action(Decision.DENY, 3)).build())
            .build()
        runCatching { nm.notify(p.id, n) }
    }

    fun decisionOf(wire: String?): Decision = Decision.values().firstOrNull { it.wire == wire } ?: Decision.DENY
}

class RootApprovalReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(RootApproval.EXTRA_ID, -1)
        RootApproval.resolve(context, id, RootApproval.decisionOf(intent.getStringExtra(RootApproval.EXTRA_DECISION)))
    }
}

/** The dialog form of the card. Back / outside = 拒绝. */
class RootApprovalActivity : Activity() {
    private var answered = false
    private var dialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        val id = intent.getIntExtra(RootApproval.EXTRA_ID, -1)
        val p = RootApproval.get(id)
        if (p == null || p.latch.count == 0L) {
            finish()
            return
        }
        val msg = SpannableStringBuilder()
        val start = msg.length
        msg.append(p.command.take(3000))
        msg.setSpan(TypefaceSpan("monospace"), start, msg.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        msg.append("\n\n")
        val r = msg.length
        msg.append("原因：${p.reason}\n只批准这一条；刷机、格式化、读密码类命令永远不会出现在这里。")
        msg.setSpan(ForegroundColorSpan(Color.rgb(0xC6, 0x28, 0x28)), r, msg.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        fun answer(d: RootApproval.Decision) {
            answered = true
            RootApproval.resolve(this, id, d)
            finish()
        }
        dialog = AlertDialog.Builder(this)
            .setTitle(p.title)
            .setMessage(msg)
            .setPositiveButton("允许一次") { _, _ -> answer(RootApproval.Decision.ALLOW_ONCE) }
            .setNeutralButton("10 分钟内都允许") { _, _ -> answer(RootApproval.Decision.ALLOW_10MIN) }
            .setNegativeButton("拒绝") { _, _ -> answer(RootApproval.Decision.DENY) }
            .setOnCancelListener { answer(RootApproval.Decision.DENY) }
            .setOnDismissListener { if (!answered) finish() }
            .create()
        dialog?.setCanceledOnTouchOutside(false)
        dialog?.show()
    }

    override fun onDestroy() {
        dialog?.setOnDismissListener(null)
        dialog?.dismiss()
        super.onDestroy()
    }
}
