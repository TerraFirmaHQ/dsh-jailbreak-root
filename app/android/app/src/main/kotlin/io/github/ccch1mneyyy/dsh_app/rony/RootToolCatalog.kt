package io.github.ccch1mneyyy.dsh_app.rony

/**
 * Rony fork: the tools of MCP server `root` (the model sees `mcp__root__<name>`). A separate server
 * so the upstream PhoneToolCatalog (and its pinned hash / prompt cache) stays untouched.
 */
// l10n:keep agent tool schema
object RootToolCatalog {
    private fun obj(props: Map<String, Any?>, required: List<String> = emptyList()): Map<String, Any?> = linkedMapOf(
        "type" to "object",
        "properties" to props,
        "required" to required,
        "additionalProperties" to false,
    )

    private fun str(desc: String, enum: List<String>? = null): Map<String, Any?> =
        linkedMapOf<String, Any?>("type" to "string", "description" to desc).also { if (enum != null) it["enum"] = enum }

    private fun int(desc: String, min: Int, max: Int): Map<String, Any?> = linkedMapOf("type" to "integer", "description" to desc, "minimum" to min, "maximum" to max)

    private fun tool(name: String, desc: String, schema: Map<String, Any?>, readOnly: Boolean): Map<String, Any?> = linkedMapOf(
        "name" to name,
        "description" to desc,
        "inputSchema" to schema,
        "annotations" to linkedMapOf("readOnlyHint" to readOnly, "destructiveHint" to !readOnly, "openWorldHint" to false),
    )

    val tools: List<Map<String, Any?>> = listOf(
        tool(
            "shell",
            "在这台安卓手机本机以真 Root（uid 0，su）执行 shell 命令，返回退出码、stdout、stderr。" +
                "这不是 Linux 沙箱：能读写 /data、改系统设置、管理应用。只读命令直接执行；写入/安装/改设置/带 ; && > 等连接或重定向的命令" +
                "在用户开着审批时会弹窗请用户批准；刷机、格式化、写块设备、读密码/令牌/密钥类文件永远被拦截。" +
                "给 session_id 则在持久会话里执行（cwd 和环境变量保留）。",
            obj(
                linkedMapOf(
                    "command" to str("完整的 shell 命令（/system/bin/sh 语法，可多行）"),
                    "cwd" to str("工作目录，可选"),
                    "timeout_ms" to int("超时毫秒，默认 30000，最大 180000；超时会结束该命令的整个进程树", 500, 180_000),
                    "session_id" to str("可选：session 工具 open 返回的会话号"),
                ),
                listOf("command"),
            ),
            readOnly = false,
        ),
        tool(
            "check_root",
            "检查并（在需要时）请求 Root 授权：返回 granted/denied/no_su/timeout、su 路径、Root 管理器类型和版本。第一次用 Root 或遇到权限错误时先调它。",
            obj(linkedMapOf("request" to linkedMapOf("type" to "boolean", "description" to "true：即使之前被拒绝也再请求一次（会在 Root 管理器弹授权框）"))),
            readOnly = true,
        ),
        tool(
            "session",
            "管理持久 Root 会话：open 返回 session_id（之后传给 shell 复用 cwd/环境变量），close 关闭，list 列出。最多 4 个。",
            obj(linkedMapOf("action" to str("open | close | list", listOf("open", "close", "list")), "session_id" to str("close 时必填"), "cwd" to str("open 时的初始目录，可选")), listOf("action")),
            readOnly = false,
        ),
        tool(
            "job",
            "后台 Root 任务（长时间运行的命令，如抓日志、编译、下载）：start 启动并立即返回 job_id；status 看状态；output 取最后的输出；kill 结束；list 列出。输出最多保留最后 64KB。",
            obj(
                linkedMapOf(
                    "action" to str("start | status | output | kill | list", listOf("start", "status", "output", "kill", "list")),
                    "command" to str("start 时必填"),
                    "cwd" to str("start 时的工作目录，可选"),
                    "job_id" to str("status/output/kill 时必填"),
                    "tail_chars" to int("output 返回最后多少字符，默认 8000", 1, 65_536),
                ),
                listOf("action"),
            ),
            readOnly = false,
        ),
        tool(
            "getprop",
            "读取系统属性（Root 下执行 getprop）。不给 name 则返回全部（会很长）。",
            obj(linkedMapOf("name" to str("属性名，如 ro.product.model，可选"))),
            readOnly = true,
        ),
        tool(
            "logcat",
            "读取系统日志（logcat -d，Root 下能看到所有应用）。",
            obj(
                linkedMapOf(
                    "lines" to int("最近多少行，默认 200，最多 3000", 1, 3000),
                    "priority" to str("最低级别", listOf("V", "D", "I", "W", "E", "F")),
                    "tag" to str("只看这个 tag，可选"),
                    "grep" to str("只保留包含该文字的行（不区分大小写），可选"),
                    "buffer" to str("日志缓冲区，默认 main", listOf("main", "system", "crash", "events", "radio", "all")),
                ),
            ),
            readOnly = true,
        ),
        tool(
            "set_setting",
            "改系统设置（settings put/delete，Root 执行）。属于危险操作，开着审批时需要用户批准。value 省略则删除该键。",
            obj(
                linkedMapOf(
                    "namespace" to str("system | secure | global", listOf("system", "secure", "global")),
                    "key" to str("设置键名"),
                    "value" to str("新值；省略则 delete"),
                ),
                listOf("namespace", "key"),
            ),
            readOnly = false,
        ),
        tool(
            "linux",
            "在手机上的 Debian 13 arm64 Linux 环境里以真 root 执行命令（unshare 私有挂载命名空间 + chroot，不是 proot；bash -lc 语法）。" +
                "apt/python3/git/编译/服务都行；/sdcard 可用，Android /system 只读挂在 /android/system。" +
                "action=status 看是否已安装和本机是否支持；exec 同步执行（默认超时 30 秒）；job 后台长任务（用 job 工具看输出）。" +
                "命令同样经过危险命令审批与拦截规则。",
            obj(
                linkedMapOf(
                    "action" to str("status | exec | job，默认 exec", listOf("status", "exec", "job")),
                    "command" to str("exec/job 时必填：bash 命令"),
                    "cwd" to str("工作目录（环境内路径），默认 /root"),
                    "timeout_ms" to int("exec 超时毫秒，默认 30000，最大 180000", 500, 180_000),
                ),
            ),
            readOnly = false,
        ),
    )
}
