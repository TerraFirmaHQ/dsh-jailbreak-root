# SPEC-COVERAGE

这轮（host 测试跑完之后）的状态。目标机一档全部是「未测」。D9 的引擎缓存和 eta 是未完成，不是「没手机所以没写」。

| 编号 | 实现 | host 验证 | 真机 |
|---|---|---|---|
| B1 | `2>/dev/null`、`>/dev/null`、`2>&1`、`&>/dev/null` 不是写入。`>&` 和 `&>` 不按后台 `&` 切开。普通路径重定向（`>/path`、`2>/sdcard/err`）仍是写入 | `CommandClassifierTest`：R7、R8、R9 为只读；`echo x 2>/sdcard/err` 为写入。真机弹窗里的 `ps … 2>/dev/null \| grep \| head; echo; ps … \| sort -rn \| head` 在同一测试里 `readonly=true` | 未测。截图只用来对照分类，不是这台机器跑出来的 |
| B2 | 只读白名单加上 echo、printf、sort、uniq、cut、tr、head、tail、wc、nl、tac、rev、basename、dirname、which、type、test、true、false、seq、sleep、date、id。`sort -o`、`uniq -o`、`date -s`、awk 的 `system(` / 管道 getline、sed 的 `-i` 和 `w`/`e` 仍是写入。未知仍是写入 | 同一测试的边界断言，加上 R10、W6、W7 | 未测 |
| B3 | 每个子命令都是只读时，整条（含 `;` `\|` `&`）`readonly=true`。不再因为复合而拦截 | R3、R8 和 `id &`。`compoundBlocked` 现为 false，session key 为空 | 未测 |
| D1 | `classifyCommand` 形状没变。17 例按 R1–R10 只读、W1–W7 写入 | `CommandClassifierTest` 把 17 行原始 JSON 写到 `D1-17-stdout.txt`。引号、`$(id)`、未知、`find -delete` 仍在 | 未测 |
| D2 | 对话框只有标题「允许执行？」、等宽可复制的命令、拒绝 / 允许。分类明细收在默认折叠的「查看审计」里。默认没有「本会话不再询问」 | `host_root_approval_test`：折叠时看不到 `/data/adb` 预览，展开才看到；`id &` 没有第三按钮，点允许送 `session:false` | 未测。没有在手机上重拍这张对话框 |
| D3 | 设置 → 主机 root 工具下第一项是「自动允许 root 命令（不再逐条询问）」，默认关，不从旧开关迁移。打开后 shell 不再询问，仍分类，审计 `choice=auto`。关掉恢复询问。root 关时即使这项开着也不调用 su。旧的「只读免确认」仍默认关，且不放过写入 | `HostRootSpecHostTest`：自动允许开时 `setprop` 不询问且结果含 `"approval":"auto"`；root 关且自动允许开时 runner 不被调用，正文是 root 关 | 未测 |
| D4 | 本机审计 JSONL：time、cmd、verdict、reason、choice、exitCode，能解析到时还有 durationMs、stdoutBytes、paging。设置页可查看和清空。诊断包里的 `rootToolMeta` 只有命令哈希、verdict、choice、exit、duration、bytes、paging，没有命令原文和输出 | `auditLine` 含 `exitCode`，不含 `"stdout"` 字段，也不含测试里的 `SECRET-STDOUT`。`files/root_audit.jsonl` 这条路径要 Android `filesDir`，JVM 没落到那个路径 | 未测。没有把诊断包原文放进仓库 |
| D5 | `check_root` 结构化字段保留。模块目录是固定 `ls`，不弹窗 | root 关时 `suCalled:false`，runner 未被调用 | 未测。不会把字段写成这台 Ace7 的实测 |
| D8 | JSON：`stdout` `stderr` `exitCode` `durationMs` `stdoutBytes` `spoolPath` | 超时和大于 64KB 的用例里 stderr 在自己的字段，stdout 里没有旧的 `stderr:` 拼接 | 未测 |
| D6 | 失败带 `stage` `error` `next` | `HostRootSpecHostTest` 用 `State.TIMEOUT` 的结果：`stage=timeout`，error 是超时原文，next 写明 `background=true` | 未测。没有真 su 卡满 180 秒 |
| D7 | `dd bs=1` 后接 `base64`。`utf8Slice` 不切开半个 UTF-8 字符，`nextOffset` 等于实际消费的字节。非法字节变成 U+FFFD 并前进 | 同一测试：命令含 `bs=1` 和 `base64`。中文加 emoji 按 4 字节页拼回原文；偏移落在字符中间仍前进；`0xFF` 后跟 `A` 消费 2 字节且含 U+FFFD | 未测。runner 是假的，没有读真实大文件 |
| D9 | 超过 64KB 的 stdout 换成摘要，stderr 分开。90 秒删本应用 spool 和已结束任务。**引擎缓存清不掉，这一条未完成** | 摘要和 stderr 分离、旧 spool 删除、90 秒后 job 表清空，都在 `HostRootSpecHostTest`。`EngineCacheGapTest` 锁住「没有释放路由」 | 未测。20 轮手机内存没有测。`PERF-host.txt` 不是这组数 |
| D10 | 摘要是前 40 行、后 10 行、总行数、路径。token 按字符数/4 | 80 行样例。`PERF-host.txt` 的平均 token 是这个算法，不是模型账单 | 未测 |
| D11 | 无障碍未连接时 `isError=false`，并写出 root 兜底命令 | `A11yDownHostTest`：`screen` 在服务未连接时不是错误，正文有「这不是命令失败」和 `input tap` / `keyevent` / `screencap` / `am start` | 未测 |
| D12 | 三类附件失败文案都带下载目录 | `attachment_fallback_test`：三句都含 `/sdcard/Download/DSH-Go/` | 未测。没有真的打开失败的附件界面 |
| R1 | root 关不 su。root 开才做结构化自检 | 关的路径有单测。开的路径会找 `su`，本机没有，所以没有把「开」跑成通过 | 未测 |
| R2 | 默认超时 180s。后台最多 2 个，可查询可取消 | `HostRootSpecHostTest` 用假 `Process`：第三个 `startJob` 返回 null；`job` 读到 `running`；`cancel:true` 返回 cancelled。没有真 su，也没有把一条命令挂满 180 秒 | 未测 |
| R3 | JSONL 增加 exitCode。自动允许记 `choice=auto`。诊断导出不带命令原文 | 见 D4 的 `auditLine` 测试 | 未测那个路径上的真文件 |
| R4 | 全只读复合不再进会话记忆（B3，对话框也没有第三按钮）。写入和未知的 key 仍是空。`clearSessionMemory` 仍清空。`sessionAllows` 为 true 也挡不住 `rm` | `HostRootSpecHostTest`：`id &` 现在是只读，预览不含 `@@session=1`；`rm` 仍要问 | 未测进程重启。记忆本来就不落盘 |
| R5 | 见 REUSE.md。没有吸收挂载、LSPosed、常驻 | 文档 | 不适用 |
| P2-1 | 破甲仍是首段，21403 字节和原 hash | hash 测试。人设字符串不含该 hash | 未测手机上的请求 |
| P2-2 / P2-3 | 称呼、自称、语气、口语程度、恢复默认。文案写明不改变审批 | `host_persona_page_test`：打开不写；默认方案含四个字段和「也不改变审批」，不含破甲 hash | 未测 |
| P2-4 | 没有合适的现成插件。短句和「不知道 / 等我看看」写在人设字符串里 | 同上，字符串在 | 未测模型口气 |
| P2-5 | 刷新率仍取当前分辨率的最高模式，没有写死 120 | 读 `MainActivity.applyRefreshRate`。没有 165Hz 计数 | 未测 |
| G1 | 索引和关于页没有检查更新、没有自动检查 | 上一轮 `index_sync_test`「关于 DSH Go」通过。`update_golden_test`「locked-version」断言没有「检查更新」、没有「自动检查更新」。本轮没改这些文件 | 未测 |
| G2 | 欢迎页、升级提示、隐私页都没有使用统计或对话日志上传。产品路径不上传 | `usage_ui_test`、`usage_upgrade_notice_test`。`usage_stats_test`「locked product path」：`suppressUpload` 为 true 时 count 和回到前台都不产生 HTTP | 未测 |
| G3 | `check()` 和 `autoCheckIfDue()` 在锁定开关后立刻返回，不联网 | 关于页点击不调用检查（`update.calls` 为空）。启动检查测试期望不调用 `autoCheckIfDue` | 未测有没有别的进程自己去连更新服务器 |
| G4 | 已有旧 rootfs 时不进欢迎页 | 上一轮 `local_optional_test`：「an upgrade runs…」有旧 `installed` 时是 `OnboardingStep.runtime` 或 null，不是 welcome。全新安装仍是 welcome。本轮没改 | 未测 |
| G5 | 关于页那一行的标题就是「当前为锁定版本」 | 同上 locked-version 测试，`findsOneWidget` | 未测 |
| 禁挂载 | `ChrootLaunch.select` 恒为 `mounts-disabled`。`DshProcess.start` 不再写启动脚本，也不再打 `launch method: root-chroot`。若请求里仍带着 chroot，只打一行「chroot mount path disabled; su not called」，随后一行 `launch method: proot` 或 `direct-glibc` | `HostRootSpecHostTest` 读 `DshProcess.kt`：源码里没有那条启动脚本名，也没有 `launch method: root-chroot` | 未改设备。日志里更早的一条 su 启动没有 APK 路径，不能记到 11071753 上。首帧 6359ms 的 prepare 只有 224ms，这轮没有改启动路径 |
| 渠道提示词 | `prompt.md` 仍是默认源，21403 字节、原 hash。null 且开关开才回默认；空串不回填。渠道文本替换破甲段，不改会话历史 | Dart 真值表、页面取消/清空、Node 组装。设置页仍是 Root 前面那一行，标题改为「默认系统提示词」 | 未测。没有外部模型请求体 |

## 明确未完成

- **D9 引擎缓存。** 桥的 `/dsh-go/v1/` 没有一条路由能在保留会话的前提下丢掉 session projection cache 或供应商 prompt cache。没有用 `/compact`，因为那会改写对话。见 `D9-ENGINE-CACHE.md`。
- **eta。** 不能唯一确定。见 `REUSE.md`。
- **真机。** 没有 Ace7，也没有 ARM64 系统镜像。上面 host 一档不能写成目标机通过。P0 的 B/D 在本机测过，不能写成全部通过。
- **首帧 6359ms。** 对应启动的 prepare 是 224ms。没有证据是这轮新增的启动阻塞，代码没改。
- **更早日志里的 root-chroot。** 那条没有 APK 路径，不能记到 11071753。这轮删掉了那条启动分支，避免以后再走到。没有在手机上执行那份历史脚本。
