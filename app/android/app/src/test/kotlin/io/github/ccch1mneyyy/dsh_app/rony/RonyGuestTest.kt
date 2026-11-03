package io.github.ccch1mneyyy.dsh_app.rony

import io.github.ccch1mneyyy.dsh_app.phone.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RonyGuestTest {
    @Test fun launchArgAddsPatchAfterAppOverlay() {
        val arg = "echo \$\$ > /tmp/dsh-web.pid; exec /usr/local/bin/dsh web --patch /etc/dsh-app/dsh-web.patch.yml --host 127.0.0.1 --port 3080"
        val out = RonyGuest.launchArg(arg, RonyGuest.ROOT_PATCH)!!
        assertTrue(out, out.contains("--patch /etc/dsh-app/dsh-web.patch.yml --patch /opt/dsh-app/rony/root.patch.yml --host"))
        val both = RonyGuest.launchArg(out, RonyGuest.MCP_PATCH)!!
        assertTrue(both.contains("--patch /opt/dsh-app/rony/mcp.patch.yml"))
        assertTrue(both.contains("--patch /opt/dsh-app/rony/root.patch.yml"))
        assertNull(RonyGuest.launchArg("exec /usr/local/bin/dsh web --profile rescue", RonyGuest.ROOT_PATCH))
    }

    @Test fun mcpYamlQuotesValuesAsJson() {
        val y = RonyGuest.mcpPatchYaml(
            listOf(
                McpServerEntry("android", "http://127.0.0.1:8765/mcp", null, "t\"o:k", true),
                McpServerEntry("fs", null, "npx -y '@scope/server fs' /root", null, true),
            ),
        )
        assertTrue(y, y.contains("serverName: \"android\""))
        assertTrue(y, y.contains("transport: streamable-http"))
        assertTrue(y, y.contains("Authorization: " + Json.write("Bearer t\"o:k")))
        assertTrue(y, y.contains("command: \"npx\""))
        assertTrue(y, y.contains("args: [\"-y\",\"@scope/server fs\",\"/root\"]"))
    }

    @Test fun serverEntryValidation() {
        assertNull(McpServerEntry.fromMap(mapOf("name" to "root", "url" to "http://x")))
        assertNull(McpServerEntry.fromMap(mapOf("name" to "bad name", "url" to "http://x")))
        assertNull(McpServerEntry.fromMap(mapOf("name" to "x", "url" to "ftp://x")))
        assertEquals("x", McpServerEntry.fromMap(mapOf("name" to "x", "url" to "https://x/mcp"))?.name)
    }

    @Test fun catalogNamesAreStable() {
        assertEquals(listOf("shell", "check_root", "session", "job", "getprop", "logcat", "set_setting", "linux"), RootToolCatalog.tools.map { it["name"] })
        // Serializable by the bridge's JSON writer.
        assertTrue(Json.write(RootToolCatalog.tools).length > 500)
    }
}
