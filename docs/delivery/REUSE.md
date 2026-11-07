# 复用清单

检索日期：2026-11-07。star、最近提交、License 来自 GitHub API，不是估计。

官方最新正式版仍是 `ccch1mneyyy/dsh-Go` **v1.0.12**（2026-11-06T19:13:50Z，`prerelease: false`）。本树基线剥开的提交是 `d5ad25c5bc4c66ecacee76b3c5378db45e1db3fa`。

**eta：** 在本仓库 pins、package 依赖和引擎清单里没有唯一的 eta 包。GitHub 上有多个同名插件（`dsh-session-turn-eta`、`dsh-balance-eta`、`dsh-opencode-go-eta` 等），不能确定哪一个是需求里的 eta。状态：**待确认 / 未完成**。没有挑一个同名仓库冒充。

## 方向与三问

### a) dsh-go fork / root

| 仓库 | star | 最近提交 | License | 适配 |
|---|---|---|---|---|
| ccch1mneyyy/dsh-Go | 13361 | 2026-11-07 `pushed_at` | AGPL-3.0 | 5（这就是基线） |
| nianyuechan/DSH-GO-ROOT | 0 | `6bc548d96c0b62902d3636eb2854024092f64c35` 2026-11-05 | AGPL-3.0 | 2 |
| ghjicb/dsh-Go | 18 | `52891a22a149551e2353607cfc22fbdb1af62bd3` 2026-11-05 | AGPL-3.0 | 3 |

1. nianyuechan 解决的是可选 chroot（旧 R 路径）。ghjicb 的 su、spool、超时思路对应 D5/R2。
2. 两个都是 AGPL。**不复制**。nianyuechan 的挂载 chroot 与本机「无 Shamiko、禁挂载」冲突，这条路径已在 `ChrootLaunch.select` 禁用。ghjicb 只保留了此前已经写在 `RootExec` 注释里的思路，没有再贴源码。
3. 无复制，无版权头转移。

### b) MCP root / adb

| 仓库 | star | 最近提交 | License | 适配 |
|---|---|---|---|---|
| Saul-Soul/dsh-go-root-bridge | 0 | `b608564cad70ecd6b4b1957f952bcdbf1b0d4890` 2026-11-06 | MIT | 1 |
| JMINN0/dsh-go-plugin-adb | 0 | `7b2fc2e61e0495539d7a809a6365648a2b1840bf` 2026-11-06 | MIT | 1 |
| turin-dev/rish-mcp | 28 | `ffd1cf62fd7c19202a2b56000220508774727bde` 2026-10-31 | MIT | 2 |
| niki914/zafiro | 341 | `c9b5b6a0d7f6e629e3d5983b32def4aa3eca25c3` 2026-11-02 | MIT | 2 |

1. Saul-Soul 自称内核提权状态和自愈式全盘文件桥，靠近 D4，但是全盘自愈。JMINN0 是无 root 的 adb。rish-mcp / zafiro 是另一套手机 MCP。
2. **不采纳。** 自愈全盘和挂载被需求禁止。无 root adb 解决的不是主机 uid 0。其余仓库的工具面和本应用的 `mcp__root__*` 不是同一条调用链，整仓搬进来会带上无关协议。
3. 无复制。

### c) 命令白名单

`android shell command allowlist` 与 `shell policy allowlist language:Kotlin` 两次 GitHub 仓库搜索没有返回可用条目。没有找到可以整段复制、又只做「命令名精确匹配」的 Kotlin 文件。`CommandClassifier.kt` 是按需求书白名单在本树里写的，不是从某个仓库贴过来的。

### d) 审批 UX

| 仓库 | star | 最近提交 | License | 适配 |
|---|---|---|---|---|
| PerryLink/dsh-permission-rules | 135 | `78d207e7194635b136e51b94c9e722370afa52d3` 2026-11-06 | Apache-2.0 | 2 |
| Aedelon/claude-code-blueprint | 120 | pushed 2026-03-04 | NOASSERTION | 1 |

1. PerryLink 做 allow/deny/ask 和 shell 出站代理，靠近 D2/R3。
2. **不采纳。** 它的规则可以 `allow` 工具，包含写入；还带本地代理。需求禁止写入自动批准，也禁止再加一条常驻代理。只借用了「先问再执行、拒绝就不跑」这个已经在本树审批板里的行为，没有复制 YAML 规则或代理。
3. 无复制。

### e) KernelSU 模块

| 仓库 | star | 最近提交 | License | 适配 |
|---|---|---|---|---|
| tiann/KernelSU | 20229 | `730f644615969cfd4a84b45d7f62a667b7b95a50` 2026-11-07 | GPL-3.0 | 1 |
| MMRLApp/template-repository | 41 | pushed 2025-12-09 | GPL-3.0 | 1 |
| SchweGELBin/KernelSU-Module-Template | 9 | pushed 2023-10-08 | MIT | 1 |
| barsikus007/ksu-webui-module-template | 7 | pushed 2026-10-05 | MIT | 1 |

1. 这些是模块模板。需求禁止改 KSU / Zygisk / HMA。
2. **不采纳。** GPL 不抄。MIT 模板会往 `/data/adb/modules` 写模块，和「只读列目录、不改模块」相反。
3. 无复制。`check_root` 只用固定的 `ls -1 /data/adb/modules` 读目录名。

## 落到本树的表

| 来源仓库 | commit | License | 复用的文件/函数 | 落到本项目 | 改动幅度 |
|---|---|---|---|---|---|
| ccch1mneyyy/dsh-Go | d5ad25c5bc4c66ecacee76b3c5378db45e1db3fa | AGPL-3.0 | 整个 v1.0.12 应用树 | 本仓库基线 | 基线，不是新粘贴 |
| ghjicb/dsh-Go | 52891a22a149551e2353607cfc22fbdb1af62bd3 | AGPL-3.0 | 无源码。`RootExec` 文件头写明 su、spool、超时是思路 | `RootExec.kt` 第 20–22 行注释 | 未新增复制 |
| nianyuechan/DSH-GO-ROOT | 6bc548d9… | AGPL-3.0 | 无源码。旧的可选 chroot 思路已禁用 | `ChrootLaunch.select` 恒为不启用 | 移除挂载路径 |
| 其余上表 | 见上 | MIT / Apache / GPL | 无 | 无 | 不采纳 |
