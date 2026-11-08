package io.github.ccch1mneyyy.dsh_app.hostroot

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One approval slot per request. Resolving an id releases only that command.
 * [revoke] rejects every slot still waiting and does not accept a later resolve.
 */
class ApprovalBoard {
    data class Request(val id: String, val command: String, val preview: String, val protectedChange: Boolean)

    private class Slot(val request: Request) {
        val accepted = AtomicBoolean(false)
        val latch = CountDownLatch(1)
        @Volatile var revoked = false
    }

    private val pending = ConcurrentHashMap<String, Slot>()
    @Volatile var onRequest: ((Request) -> Unit)? = null

    fun begin(command: String, preview: String, protectedChange: Boolean): String {
        val id = RootExec.marker()
        val slot = Slot(Request(id, command, preview, protectedChange))
        pending[id] = slot
        onRequest?.invoke(slot.request)
        return id
    }

    /** Blocks until [resolve] or [revoke]. False unless this exact command was accepted. */
    fun await(id: String, command: String, timeoutMs: Long = 90_000): Boolean {
        val slot = pending[id] ?: return false
        if (slot.request.command != command) return false
        val answered = slot.latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        pending.remove(id, slot)
        return answered && !slot.revoked && slot.accepted.get() && slot.request.command == command
    }

    fun resolve(id: String, accept: Boolean): Boolean {
        val slot = pending[id] ?: return false
        if (slot.revoked) return false
        slot.accepted.set(accept)
        slot.latch.countDown()
        return true
    }

    fun revoke() {
        pending.values.forEach { slot ->
            slot.revoked = true
            slot.accepted.set(false)
            slot.latch.countDown()
        }
    }

    fun pendingCommand(id: String): String? = pending[id]?.request?.command
}
