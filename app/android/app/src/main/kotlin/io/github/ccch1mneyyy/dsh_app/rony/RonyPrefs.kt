package io.github.ccch1mneyyy.dsh_app.rony

import android.content.Context
import android.content.SharedPreferences
import io.github.ccch1mneyyy.dsh_app.phone.Json

/** Rony fork settings, all in app-private SharedPreferences `rony`. Kotlin owns them; Dart reads them over `dsh/rony`. */
class RonyPrefs(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** Root 总开关. Default off: no root tool is mounted until the person turns it on. */
    var rootEnabled: Boolean
        get() = prefs.getBoolean(K_ROOT, false)
        set(v) = prefs.edit().putBoolean(K_ROOT, v).apply()

    /** 危险命令审批. Default on. */
    var approval: Boolean
        get() = prefs.getBoolean(K_APPROVAL, true)
        set(v) = prefs.edit().putBoolean(K_APPROVAL, v).apply()

    /** The one automatic su request (made when root is first switched on) already happened. */
    var autoRequested: Boolean
        get() = prefs.getBoolean(K_AUTO, false)
        set(v) = prefs.edit().putBoolean(K_AUTO, v).commit().let { }

    /** Last known root state (`granted`, `denied`, `no_su`, `timeout`, `unknown`) for the settings page. */
    var lastRootState: String
        get() = prefs.getString(K_STATE, "unknown") ?: "unknown"
        set(v) = prefs.edit().putString(K_STATE, v).apply()

    var lastRootDetail: String?
        get() = prefs.getString(K_DETAIL, null)
        set(v) = prefs.edit().putString(K_DETAIL, v).apply()

    var lastCheckedAt: Long
        get() = prefs.getLong(K_CHECKED, 0)
        set(v) = prefs.edit().putLong(K_CHECKED, v).apply()

    /** 破甲提示词 (bundled always-on system prompt). Default on: active from the first launch. */
    var pojiaEnabled: Boolean
        get() = prefs.getBoolean(K_POJIA, true)
        set(v) = prefs.edit().putBoolean(K_POJIA, v).apply()

    /** 刷新率: `auto` (default: high only while moving, back 0.7 s after), `max` (always highest), `system` (ask for nothing). */
    var refreshMode: String
        get() = prefs.getString(K_REFRESH, DEFAULT_REFRESH)?.takeIf { it in REFRESH_MODES } ?: DEFAULT_REFRESH
        set(v) = prefs.edit().putString(K_REFRESH, v.takeIf { it in REFRESH_MODES } ?: DEFAULT_REFRESH).apply()

    /** Ace7: pin a specific Display.Mode id (165Hz etc). 0 = no pin, use [refreshMode]. */
    var refreshModeId: Int
        get() = prefs.getInt(K_REFRESH_ID, 0)
        set(v) = prefs.edit().putInt(K_REFRESH_ID, v.coerceAtLeast(0)).apply()

    /** Skeleton: chroot+unshare -m Linux enhance. Default off; ace7 v1 does not wire the path yet. */
    var chrootEnhance: Boolean
        get() = prefs.getBoolean(K_CHROOT, false)
        set(v) = prefs.edit().putBoolean(K_CHROOT, v).apply()

    // ── v5.0 Linux 环境 ──

    /** http proxy passed into the chroot (e.g. http://127.0.0.1:7890 for Clash); empty = none. */
    var linuxProxy: String
        get() = prefs.getString(K_LINUX_PROXY, "") ?: ""
        set(v) = prefs.edit().putString(K_LINUX_PROXY, v.trim()).apply()

    // ── v5.1 人设自定义 / 活人感 ──

    /** 活人感: ONE real switch, default on. Off strips persona overlay, split-send, typing delay, proactive. */
    var humanlike: Boolean
        get() = prefs.getBoolean(K_HUMANLIKE, false)
        set(v) = prefs.edit().putBoolean(K_HUMANLIKE, v).apply()

    /** The owner's custom persona text (empty = the built-in default voice). */
    var personaText: String
        get() = prefs.getString(K_PERSONA, "") ?: ""
        set(v) = prefs.edit().putString(K_PERSONA, v.take(PERSONA_MAX)).apply()

    /** When the owner last sent something in the app (for the proactive check-in). */
    var lastUserMessageAt: Long
        get() = prefs.getLong(K_LAST_USER, 0)
        set(v) = prefs.edit().putLong(K_LAST_USER, v).apply()

    var lastProactiveAt: Long
        get() = prefs.getLong(K_LAST_PROACTIVE, 0)
        set(v) = prefs.edit().putLong(K_LAST_PROACTIVE, v).apply()

    // ── v5.2 QQ ──

    /**
     * `auto` (default: SnowLuma with root, NapCat without), `snowluma`, `napcat` (本机),
     * `external` (外部 OneBot). See [RonyQQ.effectiveMode].
     */
    var qqMode: String
        get() = prefs.getString(K_QQ_MODE, "auto")?.takeIf { it in QQ_MODES } ?: "auto"
        set(v) = prefs.edit().putString(K_QQ_MODE, v.takeIf { it in QQ_MODES } ?: "auto").apply()

    /** Load the bundled Aurabot (QQ brain) and NekoWhale stickers plugins. Default on (Aurabot itself starts disabled + dry-run). */
    var aurabotEnabled: Boolean
        get() = prefs.getBoolean(K_AURABOT, true)
        set(v) = prefs.edit().putBoolean(K_AURABOT, v).apply()

    var stickersEnabled: Boolean
        get() = prefs.getBoolean(K_STICKERS, true)
        set(v) = prefs.edit().putBoolean(K_STICKERS, v).apply()

    /** The whole QQ config as one JSON object (ports, URLs, token, QQ numbers, autostart …), see [QqConfig]. */
    var qqConfigJson: String
        get() = prefs.getString(K_QQ_CONFIG, "{}") ?: "{}"
        set(v) = prefs.edit().putString(K_QQ_CONFIG, v).apply()

    /** User MCP servers, tokens included (app-private storage, never sent to Dart in full). */
    fun mcpServers(): List<McpServerEntry> = try {
        (Json.parse(prefs.getString(K_MCP, "[]") ?: "[]") as? List<*>)?.mapNotNull { McpServerEntry.fromMap(it as? Map<*, *>) } ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    fun setMcpServers(list: List<McpServerEntry>) {
        prefs.edit().putString(K_MCP, Json.write(list.map { it.toMap(withToken = true) })).apply()
    }

    // ── Audit log ──

    @Synchronized
    fun audit(entry: Map<String, Any?>) {
        val list = auditLog().toMutableList()
        list.add(0, entry)
        prefs.edit().putString(K_AUDIT, Json.write(list.take(AUDIT_MAX))).apply()
    }

    fun auditLog(): List<Map<String, Any?>> = try {
        @Suppress("UNCHECKED_CAST")
        (Json.parse(prefs.getString(K_AUDIT, "[]") ?: "[]") as? List<Map<String, Any?>>) ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    fun clearAudit() = prefs.edit().remove(K_AUDIT).apply()

    companion object {
        const val NAME = "rony"
        private const val K_ROOT = "root_enabled"
        private const val K_APPROVAL = "root_dangerous_approval"
        private const val K_AUTO = "root_auto_requested"
        private const val K_STATE = "root_last_state"
        private const val K_DETAIL = "root_last_detail"
        private const val K_CHECKED = "root_last_checked"
        private const val K_REFRESH = "refresh_mode"
        private const val K_REFRESH_ID = "refresh_mode_id"
        private const val K_CHROOT = "chroot_enhance"
        private const val K_POJIA = "pojia_enabled"
        private const val K_MCP = "mcp_servers"
        private const val K_AUDIT = "root_audit_log"
        private const val K_LINUX_PROXY = "linux_proxy"
        private const val K_HUMANLIKE = "humanlike_enabled"
        private const val K_PERSONA = "persona_text"
        private const val K_LAST_USER = "last_user_message_at"
        private const val K_LAST_PROACTIVE = "last_proactive_at"
        private const val K_QQ_MODE = "qq_mode"
        private const val K_AURABOT = "aurabot_enabled"
        private const val K_STICKERS = "stickers_enabled"
        private const val K_QQ_CONFIG = "qq_config"
        const val PERSONA_MAX = 16_000
        val QQ_MODES = setOf("auto", "snowluma", "napcat", "external")
        const val AUDIT_MAX = 300
        val REFRESH_MODES = setOf("max", "auto", "system")
        const val DEFAULT_REFRESH = "auto"
    }
}

/** One MCP server the person added: HTTP (`url`) or a command run inside the Linux environment (`command`). */
data class McpServerEntry(
    val name: String,
    val url: String?,
    val command: String?,
    val token: String?,
    val enabled: Boolean,
) {
    fun toMap(withToken: Boolean): Map<String, Any?> = linkedMapOf(
        "name" to name, "url" to url, "command" to command, "enabled" to enabled,
        (if (withToken) "token" else "hasToken") to (if (withToken) token else !token.isNullOrEmpty()),
    )

    companion object {
        val NAME_RE = Regex("^[A-Za-z0-9_-]{1,32}$")
        val RESERVED = setOf("phone", "root", "dsh", "mcp")

        fun fromMap(m: Map<*, *>?): McpServerEntry? {
            m ?: return null
            val name = (m["name"] as? String)?.trim().orEmpty()
            if (!NAME_RE.matches(name) || name.lowercase() in RESERVED) return null
            val url = (m["url"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
            val command = (m["command"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
            if (url == null && command == null) return null
            if (url != null && !(url.startsWith("http://") || url.startsWith("https://"))) return null
            return McpServerEntry(name, url, if (url == null) command else null, (m["token"] as? String)?.trim()?.takeIf { it.isNotEmpty() }, m["enabled"] as? Boolean ?: true)
        }
    }
}
