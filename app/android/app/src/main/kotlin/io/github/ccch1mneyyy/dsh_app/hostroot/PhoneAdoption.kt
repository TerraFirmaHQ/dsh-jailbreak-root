package io.github.ccch1mneyyy.dsh_app.hostroot

/**
 * What phone control can do after a chroot engine is adopted.
 *
 * The adopted process already has its overlays. A later start cannot add the
 * phone patch. Reconnect only when that process was launched with phone
 * control; otherwise say so and leave the channel off.
 */
object PhoneAdoption {
    data class Decision(val reconnect: Boolean, val notice: Boolean)

    const val NOTICE =
        "adopted engine: phone control is unavailable in an adopted engine; restart the engine to use it"

    fun decide(phoneRequested: Boolean, engineHadPhone: Boolean): Decision = when {
        !phoneRequested -> Decision(reconnect = false, notice = false)
        engineHadPhone -> Decision(reconnect = true, notice = false)
        else -> Decision(reconnect = false, notice = true)
    }
}
