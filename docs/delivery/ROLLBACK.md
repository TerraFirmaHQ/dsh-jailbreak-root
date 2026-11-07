# 回滚

1. 不要卸载，不要清数据。包名仍是 `io.github.ronyfork.dsh_app`。覆盖安装回上一包即可，聊天和设置在应用数据目录里。
2. 手机上正在跑的是 versionCode **11071344**。本分支后来编过、但没有作为最终包送出的包不要混用。这次新包 versionCode 高于 **11071614**。回退时用盒子上原来的签名重签，再覆盖安装。
3. 代码回退：本分支上这次提交之前的 `f6eef83` 仍是上一版设置页和挂载日志。`git checkout f6eef83` 后按 `docs/delivery/BUILD.md` 的同一条命令重编。不要 force-push `main`。
4. root 开关、只读免确认、人设字段都在应用私有 prefs `dsh_hostroot`。卸载才丢。覆盖安装保留。关掉 root 不会去 su。
5. 审计文件 `files/root_audit.jsonl` 只在本机。回退旧包不会读它，也不会上传。要删只能用户自己删应用数据，本回滚不要求删。
6. 引擎 rootfs 的升级策略没改：仍保留 `/root`，不保留 `/usr/local` 和 `/opt`。回退应用包不会把已经迁走的 `/usr/local` 变回来。
