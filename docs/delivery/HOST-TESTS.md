# 这轮 host 测试

都在本机跑过。runner、`Process`、时钟和 HTTP 是注入的假对象。没有 su，没有 Ace7。

## JVM

```bash
/tmp/gradle/gradle-9.3.1/bin/gradle -p app/android :app:testArm64DebugUnitTest \
  --tests io.github.ccch1mneyyy.dsh_app.hostroot.HostRootSpecHostTest \
  --tests io.github.ccch1mneyyy.dsh_app.phone.A11yDownHostTest --offline
```

上一轮（2026-11-07T09:51:08Z）`HostRootSpecHostTest` 9 项、`A11yDownHostTest` 1 项通过。本轮改了分类器和分页，那 9 项的期望已经换掉，不能再引用旧数字。

本轮实际命令：

```bash
/tmp/gradle/gradle-9.3.1/bin/gradle -p app/android :app:testArm64DebugUnitTest \
  --tests io.github.ccch1mneyyy.dsh_app.hostroot.CommandClassifierTest \
  --tests io.github.ccch1mneyyy.dsh_app.hostroot.HostRootSpecHostTest \
  --tests io.github.ccch1mneyyy.dsh_app.hostroot.RootHostTest --offline
```

结果：`BUILD SUCCESSFUL`。`CommandClassifierTest` 5 项 0 失败，`HostRootSpecHostTest` 12 项 0 失败。时间戳 2026-11-07T11:02:44Z。`A11yDownHostTest` 这轮没重跑，`PhoneCore` 没改。

覆盖：B1/B2/B3 和 17 例写进 `D1-17-stdout.txt`。真机弹窗那条 `ps … 2>/dev/null` 流水线在分类器里是只读。D2 预览不再带 `@@session=1`。D3 自动允许开时 `setprop` 不询问且带 `"approval":"auto"`；root 关时 runner 不被调用。D6 超时字段仍在。D7 改为 `bs=1` + base64，UTF-8 分页能拼回「你好🙂」，坏字节前进。D9 摘要、90 秒 spool、job 出表仍在。R2 封顶、查询、取消仍在。R3 审计行有 `exitCode`，没有 stdout 正文。引擎启动源码不再包含 root-chroot 那条日志。

## Dart

```bash
flutter test test/settings/host_root_approval_test.dart \
  test/settings/host_persona_page_test.dart \
  test/settings/attachment_fallback_test.dart
flutter test test/usage/usage_stats_test.dart --name "locked product"
flutter test test/onboarding/local_optional_test.dart --name "an upgrade"
flutter test test/update/update_golden_test.dart --name "locked-version"
flutter test test/settings/index/index_sync_test.dart --name "关于"
```

人设、附件、使用统计、欢迎页、关于页这几条是上一轮的结果，本轮没改那些文件，没有重跑。

渠道提示词（本轮）：

```bash
node --test app/tool/hostprompt/channel_prompt.test.mjs app/tool/hostprompt/hash.test.mjs
flutter test test/channel_prompt/channel_prompt_test.dart test/channel_prompt/channel_prompt_page_test.dart \
  test/settings/host_root_approval_test.dart test/settings/host_persona_page_test.dart
flutter test test/settings/index/index_sync_test.dart --name "能力里能看到 Root"
```

`channel_prompt` 的 Dart 测试 10 项（含 txt/md、大小写扩展、中文 emoji、CRLF、5000 字、BOM、非法 UTF-8、空文件、路径不当正文）和页面 4 项（取消替换、不保存重开仍是原文、保存后 JSON 含导入全文、BOM 与取消空文件不写）通过。审批 3 项、人设 2 项、设置页那一项通过。Node 7 项通过。Gradle 的 `CommandClassifierTest`、`HostRootSpecHostTest`、`PromptMountTest` 在导入文件之前那一轮 `BUILD SUCCESSFUL`，导入没有改分类器。真引擎请求捕获：

```bash
node --test runtime/tests/channel-prompt-live.test.mjs
```

前缀是本机 `runtime/.work/x86_64/dsh-prefix` 里的 `@deepseek-ai/dsh@0.2.2-rc.1`。假端点 `127.0.0.1`，密钥 `synthetic-local-only`。1 项通过。Chat Completions 的 system content 与 `<system-reminder>\n` + 原文 + `\n</system-reminder>` 逐字节相等，只有一条。空字符串和 null/false 不发标签。null/true 包的是 `prompt.md` 原文。user 里没有渠道文本。A/B、切换、并发不串。重试不重复包装。会话 A 的 jsonl 在会话 B 运行期间哈希不变。含 `hello-b` 的文件在重试后旧字节仍是开头。`tools` 里仍有 `bash`。system 里没有工具说明句。没有访问外部模型。假端点的 `LOCAL_REPLY` 不是模型效果。

本轮重跑：

```bash
flutter test test/settings/host_root_approval_test.dart
```

结果：`All tests passed`，3 项。对话框标题是「允许执行？」。分类明细默认不在界面上，点「查看审计」才出现。没有「本会话不再询问」。

## 不好在 host 上冒充通过的

- 真 su、KernelSU 弹窗、180 秒真实超时杀进程树。
- `root_audit.jsonl` 写到 `/data/user/0/.../files/` 需要 Android `filesDir`。序列化本身已测。
- 无障碍服务在真机上掉线之后的系统行为。JVM 测的是 `PhoneCore` 在 `driver==null` 时的返回。
- 附件选择器真的打不开文件。测的是失败文案。
- D9 引擎进程里的 projection / prompt cache。没有接口，见 `D9-ENGINE-CACHE.md`。
- 20 轮手机内存和模型 token。
