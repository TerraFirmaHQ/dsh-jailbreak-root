# 复用清单

检索时间：2026-11-07。star、License、40 位 commit 都来自当时的 GitHub API。下面的链接可以打开仓库或该次提交。

官方最新正式版仍是 [ccch1mneyyy/dsh-Go](https://github.com/ccch1mneyyy/dsh-Go) **v1.0.12**（2026-11-06T19:13:50Z，不是预发布）。标注 tag 对象是 [`b75880dbde9d559a578ced3e0c8bb309e045c2d3`](https://github.com/ccch1mneyyy/dsh-Go/releases/tag/v1.0.12)。本树用的剥开提交是 [`d5ad25c5bc4c66ecacee76b3c5378db45e1db3fa`](https://github.com/ccch1mneyyy/dsh-Go/commit/d5ad25c5bc4c66ecacee76b3c5378db45e1db3fa)。默认分支在同一天还有更新的提交 [`5600c18bed61a76fdd678082f555822153859737`](https://github.com/ccch1mneyyy/dsh-Go/commit/5600c18bed61a76fdd678082f555822153859737)，没有拿它替换这个正式 tag。

**eta：未完成。** 本仓库 pins、package 依赖和引擎清单里没有唯一的 eta 包。GitHub 上有多个同名插件（`dsh-session-turn-eta`、`dsh-balance-eta`、`dsh-opencode-go-eta` 等）。没有选一个冒充。

## a) dsh-go fork / root

| 仓库 | star | commit | License | 适配 |
|---|---|---|---|---|
| [ccch1mneyyy/dsh-Go](https://github.com/ccch1mneyyy/dsh-Go) | 13361 | [`d5ad25c5bc4c66ecacee76b3c5378db45e1db3fa`](https://github.com/ccch1mneyyy/dsh-Go/commit/d5ad25c5bc4c66ecacee76b3c5378db45e1db3fa) | AGPL-3.0 | 5，这是基线 |
| [nianyuechan/DSH-GO-ROOT](https://github.com/nianyuechan/DSH-GO-ROOT) | 0 | [`6bc548d96c0b62902d3636eb2854024092f64c35`](https://github.com/nianyuechan/DSH-GO-ROOT/commit/6bc548d96c0b62902d3636eb2854024092f64c35) | AGPL-3.0 | 2 |
| [ghjicb/dsh-Go](https://github.com/ghjicb/dsh-Go) | 18 | [`52891a22a149551e2353607cfc22fbdb1af62bd3`](https://github.com/ghjicb/dsh-Go/commit/52891a22a149551e2353607cfc22fbdb1af62bd3) | AGPL-3.0 | 3 |

1. nianyuechan 做的是可选 chroot。ghjicb 的 su、spool、超时思路对应 D5/R2。
2. 都是 AGPL。**不复制。** nianyuechan 的挂载 chroot 和「无 Shamiko、禁挂载」冲突，`ChrootLaunch.select` 已恒为不启用。ghjicb 只留了 `RootExec.kt` 文件头里的思路，没有贴源码。
3. 无复制。

## b) MCP root / adb

| 仓库 | star | commit | License | 适配 |
|---|---|---|---|---|
| [Saul-Soul/dsh-go-root-bridge](https://github.com/Saul-Soul/dsh-go-root-bridge) | 0 | [`b608564cad70ecd6b4b1957f952bcdbf1b0d4890`](https://github.com/Saul-Soul/dsh-go-root-bridge/commit/b608564cad70ecd6b4b1957f952bcdbf1b0d4890) | MIT | 1 |
| [JMINN0/dsh-go-plugin-adb](https://github.com/JMINN0/dsh-go-plugin-adb) | 0 | [`7b2fc2e61e0495539d7a809a6365648a2b1840bf`](https://github.com/JMINN0/dsh-go-plugin-adb/commit/7b2fc2e61e0495539d7a809a6365648a2b1840bf) | MIT | 1 |
| [turin-dev/rish-mcp](https://github.com/turin-dev/rish-mcp) | 28 | [`ffd1cf62fd7c19202a2b56000220508774727bde`](https://github.com/turin-dev/rish-mcp/commit/ffd1cf62fd7c19202a2b56000220508774727bde) | MIT | 2 |
| [niki914/zafiro](https://github.com/niki914/zafiro) | 341 | [`c9b5b6a0d7f6e629e3d5983b32def4aa3eca25c3`](https://github.com/niki914/zafiro/commit/c9b5b6a0d7f6e629e3d5983b32def4aa3eca25c3) | MIT | 2 |

1. Saul-Soul 是内核提权状态加自愈式全盘文件桥。JMINN0 是无 root 的 adb。后两个是另一套手机 MCP。
2. **不采用。** 自愈全盘和挂载被需求禁止。无 root adb 不是主机 uid 0。另外两套协议和 `mcp__root__*` 不是同一条调用链。
3. 无复制。MIT 许可允许复制，但这几份源码解决的不是这条需求，所以没有抄。

## c) 命令白名单 / shell policy

先前只在 Kotlin 仓库里搜，没有结果。这次把范围放到 Bash/shell policy，不限语言。下面两个是 API 返回的真实仓库，README 也读过开头。

| 仓库 | star | commit | License | 适配 |
|---|---|---|---|---|
| [Atomics-hub/shell-command-policy](https://github.com/Atomics-hub/shell-command-policy) | 0 | [`877bf72be08624b57003f5a77ce9abd1018a98f7`](https://github.com/Atomics-hub/shell-command-policy/commit/877bf72be08624b57003f5a77ce9abd1018a98f7) | MIT | 3 |
| [nickderobertis/allowlister](https://github.com/nickderobertis/allowlister) | 3 | [`6828f02ad0ded203e48d762468ed01bfe6f5a163`](https://github.com/nickderobertis/allowlister/commit/6828f02ad0ded203e48d762468ed01bfe6f5a163) | MIT | 3 |

1. 两个都做「命令能不能跑」：前者按 shell 结构枚举子命令和重定向，决定不了就 `ask`；后者用同一份 allow/deny/ask 规则挡住多家 agent 的 shell 和工具。靠近 D1/D2。
2. **不采用，也没有按它们重写。** 本树的 `CommandClassifier.kt` 是按需求书的命令名白名单写的，写的时候还没有把这两个仓库引进来。它们的 `allow` 可以放行命令，和「未知按写入、写入不能自动批准」不是同一套规则。allowlister 还面向 Claude/Cursor 那些宿主，不是这个 App 的 uid 0 审批。
3. 无复制。没有把它们的解析器或规则文件放进本树。

## d) 审批 UX

| 仓库 | star | commit | License | 适配 |
|---|---|---|---|---|
| [PerryLink/dsh-permission-rules](https://github.com/PerryLink/dsh-permission-rules) | 135 | [`78d207e7194635b136e51b94c9e722370afa52d3`](https://github.com/PerryLink/dsh-permission-rules/commit/78d207e7194635b136e51b94c9e722370afa52d3) | Apache-2.0 | 2 |
| [Aedelon/claude-code-blueprint](https://github.com/Aedelon/claude-code-blueprint) | 120 | [`3bb509976e50b5dd2f68bda3213815f41307550b`](https://github.com/Aedelon/claude-code-blueprint/commit/3bb509976e50b5dd2f68bda3213815f41307550b) | NOASSERTION | 1 |

1. PerryLink 是 DSH 上的 allow/deny/ask，外加 shell 出站代理，靠近 D2/R3。claude-code-blueprint 是另一套权限蓝图，License 字段不是标准 SPDX。
2. **不采用。** PerryLink 的规则可以 `allow` 写入，还带本地代理。需求禁止写入自动批准，也禁止再加常驻代理。本树审批仍是「先问再执行」，没有复制它的 YAML 或代理。
3. 无复制。

## e) KernelSU 模块

| 仓库 | star | commit | License | 适配 |
|---|---|---|---|---|
| [tiann/KernelSU](https://github.com/tiann/KernelSU) | 20229 | [`730f644615969cfd4a84b45d7f62a667b7b95a50`](https://github.com/tiann/KernelSU/commit/730f644615969cfd4a84b45d7f62a667b7b95a50) | GPL-3.0 | 1 |
| [MMRLApp/template-repository](https://github.com/MMRLApp/template-repository) | 41 | [`d525d283c60b2eb492708a6d25ae803978476895`](https://github.com/MMRLApp/template-repository/commit/d525d283c60b2eb492708a6d25ae803978476895) | GPL-3.0 | 1 |
| [SchweGELBin/KernelSU-Module-Template](https://github.com/SchweGELBin/KernelSU-Module-Template) | 9 | [`9afd31c047627e8cc9b28c575707a39412a7963d`](https://github.com/SchweGELBin/KernelSU-Module-Template/commit/9afd31c047627e8cc9b28c575707a39412a7963d) | MIT | 1 |
| [barsikus007/ksu-webui-module-template](https://github.com/barsikus007/ksu-webui-module-template) | 7 | [`22225413223d12cd42a25b4ea4695376af41e8df`](https://github.com/barsikus007/ksu-webui-module-template/commit/22225413223d12cd42a25b4ea4695376af41e8df) | MIT | 1 |

1. 这些是模块或模块模板。需求禁止改 KSU / Zygisk / HMA。
2. **不采用。** GPL 不抄。MIT 模板会往 `/data/adb/modules` 写模块，和「只读列目录」相反。
3. 无复制。`check_root` 只用固定的 `ls -1 /data/adb/modules`。

## 落到本树的表

| 来源 | commit | License | 复用了什么 | 落点 | 幅度 |
|---|---|---|---|---|---|
| [ccch1mneyyy/dsh-Go](https://github.com/ccch1mneyyy/dsh-Go) | [`d5ad25c5bc4c66ecacee76b3c5378db45e1db3fa`](https://github.com/ccch1mneyyy/dsh-Go/commit/d5ad25c5bc4c66ecacee76b3c5378db45e1db3fa) | AGPL-3.0 | v1.0.12 应用树 | 本仓库基线 | 基线，不是新粘贴 |
| [ghjicb/dsh-Go](https://github.com/ghjicb/dsh-Go) | [`52891a22a149551e2353607cfc22fbdb1af62bd3`](https://github.com/ghjicb/dsh-Go/commit/52891a22a149551e2353607cfc22fbdb1af62bd3) | AGPL-3.0 | 无源码。思路写在 `RootExec.kt` 文件头 | `RootExec.kt` | 未新增复制 |
| [nianyuechan/DSH-GO-ROOT](https://github.com/nianyuechan/DSH-GO-ROOT) | [`6bc548d96c0b62902d3636eb2854024092f64c35`](https://github.com/nianyuechan/DSH-GO-ROOT/commit/6bc548d96c0b62902d3636eb2854024092f64c35) | AGPL-3.0 | 无源码。挂载 chroot 已禁用 | `ChrootLaunch.select` | 移除挂载路径 |
| 其余上表 | 见各行 | MIT / Apache / GPL / NOASSERTION | 无 | 无 | 不采用 |
