package io.github.ccch1mneyyy.dsh_app.hostroot

/**
 * Explicit battery exemptions. Nothing here runs at startup, and nothing here
 * touches refresh rate or CPU governors.
 *
 * Success requires a read, then the write, then a read that shows the new
 * value. A write whose readback still shows the old value is a failure.
 */
object BatteryTuning {
    data class Action(
        val id: String,
        val read: String,
        val apply: String,
        val isApplied: (String) -> Boolean,
    )

    data class Outcome(
        val id: String,
        val ok: Boolean,
        val before: String,
        val after: String,
        val detail: String,
    )

    fun actions(packageName: String): List<Action> = listOf(
        Action(
            id = "batteryExempt",
            read = "dumpsys deviceidle whitelist | grep -c ${sh(packageName)}",
            apply = "dumpsys deviceidle whitelist +${packageName}",
            isApplied = { out -> out.lines().any { it.trim().toIntOrNull()?.let { n -> n > 0 } == true } },
        ),
        Action(
            id = "standbyBucket",
            read = "am get-standby-bucket --user 0 ${sh(packageName)}",
            apply = "am set-standby-bucket --user 0 $packageName active",
            isApplied = { out -> out.lines().any { it.trim().toIntOrNull() == 10 } },
        ),
    )

    fun ids(): Set<String> = setOf("batteryExempt", "standbyBucket")

    /**
     * [run] executes one host command and returns its stdout. The caller must
     * already be inside an approved root session; this function does not spawn su.
     */
    fun apply(action: Action, run: (String) -> String): Outcome {
        val before = run(action.read)
        if (action.isApplied(before)) {
            return Outcome(action.id, true, before, before, "already")
        }
        run(action.apply)
        val after = run(action.read)
        val ok = action.isApplied(after)
        return Outcome(action.id, ok, before, after, if (ok) "applied" else "readback mismatch")
    }

    private fun sh(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
