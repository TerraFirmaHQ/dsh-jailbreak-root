package io.github.ccch1mneyyy.dsh_app.runtime

import io.github.ccch1mneyyy.dsh_app.hostroot.PhoneAdoption
import java.io.File

/**
 * Decides whether a detached chroot engine can be adopted.
 * Pure: the caller supplies liveness and health, so tests never call su.
 */
object ChrootSession {
    sealed interface Outcome {
        data object StartFresh : Outcome
        data class Adopt(val record: EngineHandoff.Record, val phone: PhoneAdoption.Decision) : Outcome
        data class Block(val reason: String, val healthRetry: Boolean) : Outcome
    }

    fun consider(
        record: EngineHandoff.Record?,
        rootfs: File,
        myUid: Int,
        phoneRequested: Boolean,
        liveness: (Int) -> AdoptedEngine.Liveness,
        health: AdoptedEngine.HealthCheck,
    ): Outcome {
        if (record == null) return Outcome.StartFresh
        val checked = AdoptedEngine.check(record, rootfs, myUid, liveness, health)
        if (checked.adopted && checked.record != null) {
            return Outcome.Adopt(checked.record, PhoneAdoption.decide(phoneRequested, checked.record.phone))
        }
        if (checked.record != null) return Outcome.Block(checked.reason ?: AdoptedEngine.UNREACHABLE, checked.healthRetry)
        return Outcome.StartFresh
    }

    /**
     * Proc first. [readSu] is used only when proc cannot be read and the caller
     * already decided chroot is enabled. A null [readSu] never calls su.
     */
    fun liveness(pid: Int, readProc: (String) -> String?, readSu: ((String) -> String?)? = null): AdoptedEngine.Liveness {
        val first = livenessFromProc(pid, readProc)
        if (first !is AdoptedEngine.Liveness.Unknown || readSu == null) return first
        return livenessFromProc(pid, readSu)
    }

    /** /proc reader. Unknown when the pid exists but status cannot be read. */
    fun livenessFromProc(pid: Int, read: (String) -> String?): AdoptedEngine.Liveness {
        if (pid <= 0) return AdoptedEngine.Liveness.Absent
        val status = read("/proc/$pid/status") ?: return if (read("/proc/$pid") == null) {
            AdoptedEngine.Liveness.Absent
        } else {
            AdoptedEngine.Liveness.Unknown
        }
        val uid = status.lineSequence().firstOrNull { it.startsWith("Uid:") }
            ?.split(Regex("\\s+"))?.getOrNull(1)?.toIntOrNull()
            ?: return AdoptedEngine.Liveness.Unknown
        val command = read("/proc/$pid/cmdline")?.replace('\u0000', ' ')?.trim().orEmpty()
        if (command.isEmpty()) return AdoptedEngine.Liveness.Unknown
        val cwd = read("/proc/$pid/cwd")
        return AdoptedEngine.Liveness.Alive(uid, command, cwd)
    }
}
