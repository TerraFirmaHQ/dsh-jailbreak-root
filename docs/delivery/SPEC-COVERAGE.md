# SPEC-COVERAGE

这轮（host 测试跑完之后）的状态。目标机一档全部是「未测」。D9 的引擎缓存和 eta 是未完成，不是「没手机所以没写」。

| 编号 | 实现 | host 验证 | 真机 |
|---|---|---|---|
| D1 | `CommandClassifier.classifyCommand` 按白名单拆分。未知按写入。 | `CommandClassifierTest` 12 例，输出在 `D1-12-stdout.txt`。另有引号、替换、重定向、未知、`-delete`、尾随 `&` | 未测 |
| D2 | 四段预览。只有全只读被复合规则拦住才带 `@@session=1`。对话框这时才有「本会话不再询问」 | `HostRootSpecHostTest`：`setprop` 和 `foobar` 的预览没有 session 标记；`id &` 有。`host_root_approval_test` 点第三按钮才送 `session:true`；`session:false` 时没有这个按钮。允许/拒绝仍过 | 未测 |
| D3 | 「只读命令免确认」默认关。只有 `readonly==true` 且开关开才不经审批 | 开着开关时 `id` 不询问，`setprop` 仍拒绝且不执行 | 未测 |
| D4 | `check_root` 返回结构化字段。模块目录是固定 `ls`，不弹窗 | root 关时 `suCalled:false`，runner 未被调用 | 未测。不会把字段写成这台 Ace7 的实测 |
| D5 / D8 | JSON：`stdout` `stderr` `exitCode` `spoolPath` | 超时和大于 64KB 的用例里 stderr 在自己的字段，stdout 里没有旧的 `stderr:` 拼接 | 未测 |
| D6 | 失败带 `stage` `error` `next` | `HostRootSpecHostTest` 用 `State.TIMEOUT` 的结果：`stage=timeout`，error 是超时原文，next 写明 `background=true` | 未测。没有真 su 卡满 180 秒 |
| D7 | `offset`/`limit`，默认 256KB，最大 1MB，更大则 `needChunk` | 同一测试：默认 `bs=4096 count=64`，`nextOffset=262144`；`limit` 传 2MB 被收成 `count=1048576` | 未测。runner 是假的，没有读真实大文件 |
| D9 | 超过 64KB 的 stdout 换成摘要，stderr 分开。90 秒删本应用 spool 和已结束任务。**引擎缓存清不掉，这一条未完成** | 摘要和 stderr 分离、旧 spool 删除、90 秒后 job 表清空，都在 `HostRootSpecHostTest`。`EngineCacheGapTest` 锁住「没有释放路由」 | 未测。20 轮手机内存没有测。`PERF-host.txt` 不是这组数 |
| D10 | 摘要是前 40 行、后 10 行、总行数、路径。token 按字符数/4 | 80 行样例。`PERF-host.txt` 的平均 token 是这个算法，不是模型账单 | 未测 |
| D11 | 无障碍未连接时 `isError=false`，并写出 root 兜底命令 | `A11yDownHostTest`：`screen` 在服务未连接时不是错误，正文有「这不是命令失败」和 `input tap` / `keyevent` / `screencap` / `am start` | 未测 |
| D12 | 三类附件失败文案都带下载目录 | `attachment_fallback_test`：三句都含 `/sdcard/Download/DSH-Go/` | 未测。没有真的打开失败的附件界面 |
| R1 | root 关不 su。root 开才做结构化自检 | 关的路径有单测。开的路径会找 `su`，本机没有，所以没有把「开」跑成通过 | 未测 |
| R2 | 默认超时 180s。后台最多 2 个，可查询可取消 | `HostRootSpecHostTest` 用假 `Process`：第三个 `startJob` 返回 null；`job` 读到 `running`；`cancel:true` 返回 cancelled。没有真 su，也没有把一条命令挂满 180 秒 | 未测 |
| R3 | JSONL 字段 time/cmd/verdict/reason/choice | `auditLine` 测试：choice 为 allow/deny，正文里的 `SECRET-STDOUT` 不出现，也没有 stdout 字段。写文件需要 Android `filesDir`，这条没在 JVM 里落到那个路径 | 未测那个路径上的真文件 |
| R4 | 记忆 key 去掉路径。写入和未知没有 key。`clearSessionMemory` 清空 | `ls /data/adb/modules &` 的 key 是 `ls`。记住 `id &` 之后 clear 变空。伪造 session 标记的 `rm` 进不了记忆。`sessionAllows` 返回 true 也挡不住 `rm` | 未测进程重启。记忆本来就不落盘 |
| R5 | 见 REUSE.md。没有吸收挂载、LSPosed、常驻 | 文档 | 不适用 |
| P2-1 | 破甲仍是首段，21403 字节和原 hash | hash 测试。人设字符串不含该 hash | 未测手机上的请求 |
| P2-2 / P2-3 | 称呼、自称、语气、口语程度、恢复默认。文案写明不改变审批 | `host_persona_page_test`：打开不写；默认方案含四个字段和「也不改变审批」，不含破甲 hash | 未测 |
| P2-4 | 没有合适的现成插件。短句和「不知道 / 等我看看」写在人设字符串里 | 同上，字符串在 | 未测模型口气 |
| P2-5 | 刷新率仍取当前分辨率的最高模式，没有写死 120 | 读 `MainActivity.applyRefreshRate`。没有 165Hz 计数 | 未测 |
| G1 | 索引和关于页没有检查更新、没有自动检查 | 这轮 `index_sync_test`「关于 DSH Go」通过。`update_golden_test`「locked-version」断言没有「检查更新」、没有「自动检查更新」 | 未测 |
| G2 | 欢迎页、升级提示、隐私页都没有使用统计或对话日志上传。产品路径不上传 | `usage_ui_test`、`usage_upgrade_notice_test`。`usage_stats_test`「locked product path」：`suppressUpload` 为 true 时 count 和回到前台都不产生 HTTP | 未测 |
| G3 | `check()` 和 `autoCheckIfDue()` 在锁定开关后立刻返回，不联网 | 关于页点击不调用检查（`update.calls` 为空）。启动检查测试期望不调用 `autoCheckIfDue` | 未测有没有别的进程自己去连更新服务器 |
| G4 | 已有旧 rootfs 时不进欢迎页 | 这轮重跑 `local_optional_test`：「an upgrade runs…」有旧 `installed` 时是 `OnboardingStep.runtime` 或 null，不是 welcome。全新安装仍是 welcome | 未测 |
| G5 | 关于页那一行的标题就是「当前为锁定版本」 | 同上 locked-version 测试，`findsOneWidget` | 未测 |
| 禁挂载 | `ChrootLaunch.select` 恒为 `mounts-disabled`。启动不为 chroot 调用 su | 原单测仍成立 | 未改设备 |
| 破甲 | `prompt.md` 未改 | hash 测试；干净包里的 prompt.md 仍是 21403 字节和原 hash | 未测注入是否出现在手机请求里 |

## 明确未完成

- **D9 引擎缓存。** 桥的 `/dsh-go/v1/` 没有一条路由能在保留会话的前提下丢掉 session projection cache 或供应商 prompt cache。没有用 `/compact`，因为那会改写对话。见 `D9-ENGINE-CACHE.md`。
- **eta。** 不能唯一确定。见 `REUSE.md`。
- **真机。** 没有 Ace7，也没有 ARM64 系统镜像。上面 host 一档不能写成目标机通过。
