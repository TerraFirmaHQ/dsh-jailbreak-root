# SPEC-COVERAGE

状态分三档：实现、host 测试、目标机。目标机一档全部是「未测」。

| 编号 | 实现 | host 测试 | 目标机 |
|---|---|---|---|
| D1 | `CommandClassifier.classifyCommand` 按需求拆分和白名单。未知按写入。 | `CommandClassifierTest` 12 例写入 `D1-12-stdout.txt`，另有引号、替换、重定向、未知、`-delete`、尾随 `&` | 未测 |
| D2 | 审批事件带四段预览。命令可选择复制。`@@session=1` 时才有「本会话不再询问」 | 原对话框允许/拒绝测试仍过。第三按钮没有单独的 widget 测试 | 未测 |
| D3 | 设置 → Root 下「只读命令免确认」，prefs 默认关。只有 `readonly==true` 且开关开才不经审批 | 单测：开着开关时 `id` 不询问，`setprop` 仍拒绝且不执行 | 未测 |
| D4 | `check_root` 返回 state/granted/uid/suPath/managerName/managerVersion/selinux/hideStackModules/kernelRelease/androidSdk。模块目录是固定 `ls`，不弹窗 | root 关时 `suCalled:false` 且 runner 未被调用 | 未测。字段值不会假装成这台 Ace7 |
| D5 / D8 | shell 结果是 JSON：`stdout` `stderr` `exitCode` `spoolPath`。正文不把 spool 路径拼进 stdout | 只读跳过的返回里有 `stdout` 字段 | 未测 |
| D6 | 失败时带 `stage` `error` `next`。超时的 next 写明可转后台 | 代码在 `failureOf`。没有单独断言超时文案的测试 | 未测 |
| D7 | `read_file` 的 `offset` `limit`，默认 256KB，最大 1MB。更大时 `needChunk/totalSize/nextOffset` | 路径拒绝和原读文件测试仍走这条函数。没有对真实大文件的测试 | 未测 |
| D9 | 超过 64KB 的 stdout 换成摘要，完整内容在 spool。90 秒只清 spool 和已结束任务，**没有**清引擎缓存。见 D9-ENGINE-CACHE.md | `summarize` 单测；`EngineCacheGapTest` 锁住「没有接口」这句话。`PERF-host.txt` 不是手机 | 未测。标准未达到 |
| D10 | 摘要是前 40 行、后 10 行、总行数、路径。token 按字符数/4 估算 | 80 行样例包含头尾行。20 轮平均摘要 token 见 PERF-host.txt，低于 8K 的是摘要文本，不是模型账单 | 未测 |
| D11 | 无障碍未连接时 `isError=false`，`decision=a11y-down`，并写明 root 兜底命令仍要审批 | 没有对应 widget/JVM 测试 | 未测 |
| D12 | 打不开或传不了附件时的三句文案都带 `/sdcard/Download/DSH-Go/` | 文案生成过。没有跑附件失败界面 | 未测 |
| R1 | root 关：`prepare` 把自检记为 skipped，不调用 su。root 开：启动时跑一次结构化 `check_root` | 关的路径有单测。开的路径会真的找 `su`，本机没有 su，所以没有把「开」跑成通过 | 未测 |
| R2 | shell 默认超时 180s，上限 180s。`background:true` 在审批之后最多 2 个任务，线程随命令结束。`job` 可查询，`cancel` 可中断 | 任务上限写在 `RootExec.startJob`。没有在本机用真 su 跑满 180 秒 | 未测 |
| R3 | JSONL 追加到 `context.filesDir/root_audit.jsonl`，字段 time/cmd/verdict/reason/choice，不含 stdout | 代码在 `HostRootControl.audit`。单测没有 Android Context，所以没有读到那个文件 | 未测 |
| R4 | 记忆 key 只在「全只读但被复合规则拦住」时生成。写入返回 null。内存集合，关 root 或点清除就空 | `sessionKey("rm ...")` 为 null；`id &` 有 key | 未测跨进程。重启进程会空，因为没有落盘 |
| R5 | 见 REUSE.md。没有吸收挂载、LSPosed、常驻 | 文档 | 不适用 |
| P2-1 | 锁定破甲仍是首段 system，21403 字节和原 hash。人设不是这段原文 | 原 hash 测试还在 | 未测 |
| P2-2 / P2-3 | 人设页是称呼、自称、语气、口语程度、补充、恢复默认。保存进 persona 字符串，不进审批 | widget 测试：打开不写；保存含四个字段和旧补充 | 未测 |
| P2-4 | 没有找到不挂载、不常驻、不依赖 LSPosed 的现成人设插件可直接嵌进这段。默认方案写了短句、口语、可以「不知道 / 等我看看」、不要机械列表 | 保存在人设字符串里 | 未测是否改变模型口气 |
| P2-5 | 未改刷新率算法，仍按当前分辨率取系统最高模式。没有写 120 | 读代码。没有 165Hz 计数 | 未测 |
| G1 | 关于页和设置索引里不再有「检查更新」和「自动检查更新」。单独一行标题就是「当前为锁定版本」，没有点击检查。启动检查直接返回。首页不挂新版本标记 | `index_sync_test` 关于页通过。`update_golden_test` 断言找不到「检查更新」 | 未测 |
| G2 | 欢迎页、升级提示、隐私页都不再出现使用统计或对话日志上传。产品路径 `suppressUpload` 为 true 时 `count` / `foreground` / `_send` 直接返回 | 隐私和欢迎的索引/界面测试通过。协议单测把 `suppressUpload` 设回 false 才发请求，那不是产品路径 | 未测 |
| G3 | 不跑版本检查，服务端不能在这次启动里改本机版本号。Android versionName 仍来自构建参数 | 代码 | 未测 |
| G4 | 已有旧 rootfs 时，原来的 `initialOnboardingStep` 就不进欢迎页。这次没有改这段，避免把已有数据的升级流程弄丢 | 原逻辑，这次没加新测试 | 未测 |
| G5 | 文案「当前为锁定版本」出现在关于页的值和开关说明，以及模型过旧按钮 | 文案在 arb。界面测试待跑 | 未测 |
| 禁挂载 | `ChrootLaunch.select` 恒返回 `mounts-disabled`。启动不再因为 chroot 偏好调用 `su`。设置开关写不回去 | 原「未授权则关」测试仍成立。没有在设备上卸载模块 | 未改设备 |
| 破甲 | `prompt.md` 未改 | hash 测试 | 未测注入是否出现在手机请求里 |
