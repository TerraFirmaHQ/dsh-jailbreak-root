# 渠道系统提示词

数据在渠道（Provider）上，不在单个模型上。Eta 只作对照，没有抄它的源码。

## Eta

[Mangi-11/Eta](https://github.com/Mangi-11/Eta)。GitHub 上的 license 字段是 `NOASSERTION`。仓库里的 `LICENSE` 是 PolyForm Noncommercial 1.0.0。不复制。

| | |
|---|---|
| 最新正式版 | tag `v3.4.0`，提交 [`e90922a4490389f524a10c6acebbcca336883935`](https://github.com/Mangi-11/Eta/commit/e90922a4490389f524a10c6acebbcca336883935)，2026-10-31 |
| 查阅时的 HEAD | [`c866a747ba89787d15beed5030aa8513960aea45`](https://github.com/Mangi-11/Eta/commit/c866a747ba89787d15beed5030aa8513960aea45)，2026-11-07。比正式版新，不是 release |
| 字段 | `ProviderSetting.systemPrompt: String?`，`app/src/main/kotlin/io/github/mangi/eta/data/model/Provider.kt` |
| 模型 | 同一次查阅的 `Model.kt` 没有 `systemPrompt`。提示词在渠道上，模型是渠道里的列表 |
| 请求 | `AgentPromptBuilder.buildSystemMessages` 用 `isNotBlank()` 决定发不发。空串、空白和 null 在那边被收成同一类。本树不这么做 |

没有第二个仓库同时满足「Android 聊天、渠道级 system prompt、这份源码路径」。以前那些同名 eta 插件仍然不是这一份。

## 真值表

`systemPrompt == null` 是没设置过。`""` 是用户清空。不 trim，不用 `??` 把空串收成 null。

| 存的文本 | 内置开关 | 发出的渠道提示词 | 列表 |
|---|---|---|---|
| null | true | `prompt.md` 原文 | 默认 |
| null | false | 不发 | 内置已关 |
| `""` | true 或 false | 不发 | 开关关时是「内置已关」，否则「空」 |
| 非空，且等于原文 | 任意 | 这段非空文本 | 开关关时是「内置已关」，否则「默认」 |
| 非空，且不同于原文 | 任意 | 这段非空文本 | 开关关时是「内置已关」，否则「自定义」 |

开关优先决定列表上的四个字。非空文本仍然会发出，所以「内置已关」只表示不再用 null 的回退，不表示已保存的字被丢掉。打开开关不会把空或非默认文本填回去。只有「恢复默认」才把文本写成原文并把开关打开。

旧渠道没有这两个字段时按 null / true 读，不改 Root 和自动允许。文件是应用私有的 `channel_prompts.json`，不写进会话库。

## 请求

渠道文本替换 `dsh:builtin-prompt`。`deployment:persona-prefix` 和以 `You are an AI agent powered by DeepSeek Harness.` 开头的默认身份段不跟渠道文本拼在一起。`dsh:persona` 和工具段留着。空段不发。

Chat Completions（`openai-completions`）按原顺序合成一条 system。Anthropic 放在 `system`。Responses 放在 `instructions`。用户和工具消息留在后面。构造函数返回新数组，不改传入的历史。

```text
A openai-completions  system: PROMPT-A
                      user: hi
B 文本为 ""           没有 system
                      user: hi
```

历史数组本身不被改写。副本里只拿掉内容恰好等于已辨识旧全局段的那一条 system。用户自己的 system 笔记和工具说明留在副本里。引擎真正发出的请求不走这个辅助函数，见下面的真引擎记录。

## 校验

空文本是清空，可以保存。其它文本：不能以 BOM 开头，首行不能是空行，不能以 ``` 结尾。不通过就标红并且不写文件。不改 CRLF，不去掉末尾空格。没有新的字数上限。5000 个「汉」加 🙂 能原样读回。

## 导入 txt / md

编辑页有「导入文件」。系统选择器不过滤 MIME，所以文件管理器把 `.md` 标错类型时仍能选到。选中后只看文件名的扩展名（`.txt` / `.md`，不区分大小写）和文件字节。名字和路径不会变成提示词。

读到的纯文本整段替换草稿，不接在原来的字后面，也不当 Markdown 渲染。点保存才写入。取消选择、取消替换、读失败、扩展名不对、非法 UTF-8、BOM、首行是空的、以 ``` 结尾，都留着原来的草稿，并标明原因。BOM 不会被悄悄删掉。空文件是显式空草稿；若当前草稿非空，先确认再替换。导入不改「使用内置系统提示词」。不上传，也不另要存储权限。

## 真引擎 0.2.2-rc.1

`dsh-agent-loop` 的 `preStep` 调用 `systemPrompt.assemble`，上下文是 `{ agent, scope: agent, signal, modelSelection, toolSet }`。`modelSelection` 来自 `agent.model.current()`，字段是 `{ provider, model }`。选中渠道就是 `modelSelection.provider`。以前的查找没有读这个字段，缺省时会按 null/true 用默认文本，等于每条请求都落回默认。这一处已改：先读 `modelSelection.provider`，再读 `agent.model.current()`，最后才是创建时的 `agent.options.provider`。三个都没有时，钩子不改装配结果，不再假装这是「没设置过的渠道」。

本机前缀 `runtime/.work/x86_64/dsh-prefix` 里的包版本是 `@deepseek-ai/dsh@0.2.2-rc.1`。测试命令：

```bash
node --test runtime/tests/channel-prompt-live.test.mjs
```

模型端点是 `127.0.0.1` 上的假 OpenAI Chat Completions。密钥是 `synthetic-local-only`。没有访问外部模型。结果：1 项通过。

- 渠道 A 的 `/v1/chat/completions` 含 `PROMPT-A-CHANNEL` 和 bash 工具句 `Check the [exit code: N] marker...`，不含 `PROMPT-B-CHANNEL`，也不含默认文件开头 `You are rony`。
- 渠道 B 只有 `PROMPT-B-CHANNEL` 和同一句工具说明。
- 显式空字符串的渠道不含默认原文，也不含 A/B 文本，工具句还在。
- 会话 A 跑完后的 jsonl 哈希，在会话 B 跑完时不变。
- 同一会话从 A 切到 B 之后的下一次请求含 B、不含 A。
- 两个会话同时 prompt，请求各自只带自己的渠道文本。

引擎自己的 `SystemPromptProjection` 仍可能在 `in-history` 时追加一条新的 `system/message` 事件。那是追加，不是改写已有行。这次测试看到的是另一会话的文件字节不变，以及切换后的 HTTP 不再带旧渠道文本。用户消息和工具段没有被钩子删掉。真机没跑。
