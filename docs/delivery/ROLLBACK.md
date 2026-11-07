# 回滚

1. 不要卸载，不要清数据。包名仍是 `io.github.ronyfork.dsh_app`。覆盖安装回上一包即可，聊天和设置在应用数据目录里。
2. 这轮交付的 versionCode 是 **11080152**。11080129 没有接通选中渠道。手机上的 **11071905** 可以覆盖装回去。回退时用盒子上原来的签名重签，再覆盖安装。不要卸载。渠道提示词在应用私有 `channel_prompts.json`，旧包不读它。
3. 代码回退：本分支上这次提交之前的 `f6eef83` 仍是上一版设置页和挂载日志。`git checkout f6eef83` 后按 `docs/delivery/BUILD.md` 的同一条命令重编。不要 force-push `main`。
4. root 开关、只读免确认、自动允许（`autoAllowShell`，默认关，不从旧键迁移）、人设字段都在应用私有 prefs `dsh_hostroot`。卸载才丢。覆盖安装保留。关掉 root 不会去 su，自动允许开着也不改变这一点。
5. 审计文件 `files/root_audit.jsonl` 只在本机。回退旧包不会读它，也不会上传。要删只能用户自己删应用数据，本回滚不要求删。
6. 引擎 rootfs 的升级策略没改：仍保留 `/root`，不保留 `/usr/local` 和 `/opt`。回退应用包不会把已经迁走的 `/usr/local` 变回来。
