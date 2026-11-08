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

非空渠道文本在发出前包一次。模型能看见的 system content 是：

```text
<system-reminder>
{存着的提示词全文，一个字节不改}
</system-reminder>
```

只放这一条。不拼到 user。不写第二份。不在标签前后加来源说明、小标题或「请遵循以下」这类应用自己写的句子。用户正文里如果本来就有这些词或这对标签，原样留在标签里面。空字符串和 null/false 不发渠道提示词，也不发空标签。null/true 先取 `prompt.md` 原文，再包这一次。包装不写回 `channel_prompts.json`，也不改 `prompt.md`。标签只是应用约定的发送格式，不改变模型权限。

装配钩子在有选中渠道时把 system 段换成这一条，或在没有渠道文本时换成空列表。`dsh:persona`、`tool:*` 说明段、`harness:identity` 和默认身份句不再跟渠道文本放进同一条 system。工具 schema 仍由引擎挂在请求的 `tools` 上。人格编辑和 `persona.mjs` 还在，只是这段人格不再进入这条 system content。Root 用法说明仍由引擎放进 user 角色的 AGENTS 消息，没有删。选中渠道缺失时钩子不改装配结果，那条路径不走这次包装。

Chat Completions 的首条 system 用 `messages[].role = system`，content 就是上面这一条。这是这次假端点抓到的。同一份 `@earendil-works/pi-ai` 源码里，Anthropic 非 OAuth 把同一段渲染结果放进 `params.system[0].text`；OAuth 会先加引擎自己的 “You are Claude Code...” 块，这次没有改它，也没有抓 Anthropic 请求。`openai-responses` 把同一段放进 `input` 里的一条消息，角色是 `system`；模型开了 reasoning 且没有关掉 developer role 时，角色是 `developer`。它没有顶层 `instructions`。`openai-codex-responses` 才把这段放进 `instructions`，空字符串时引擎会换成 “You are a helpful assistant.”。Codex 这条没有抓包。`sanitizeSurrogates` 会去掉不成对的代理项，普通 emoji 和 CRLF 留着。

本地的 `projectRequest` 只是测试里的副本，不是引擎编码器。它返回新数组，不改传入的历史。

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

模型端点是 `127.0.0.1` 上的假 OpenAI Chat Completions。密钥是 `synthetic-local-only`。没有访问外部模型。假端点只回 `LOCAL_REPLY`，不能当成模型会把短拒绝改成长回答。结果：1 项通过。

渠道 A 存的原文是 `PROMPT-A-CHANNEL` 加 CRLF、`你好🙂`、换行，以及正文自带的「请遵循以下」「user-defined」和 `<system-reminder>`。抓到的 system 只有一条，content 与 `<system-reminder>\n` + 这段原文 + `\n</system-reminder>` 逐字节相等。user 消息里没有这段原文。system 里没有 bash 工具句，也没有 “You are an AI agent powered by DeepSeek Harness.”。`tools` 里仍有 `bash`（有的请求写在 `name`，有的写在 `function.name`）。

- 渠道 B 的 system content 同样只有包住 `PROMPT-B-CHANNEL` 的那一条。
- 显式空字符串和 null/false 的请求没有 `<system-reminder>`，也没有 `You are rony`，`bash` 仍在 `tools`。
- 没有记录的渠道走 null/true。system content 等于把 `prompt.md` 原文包一次。文件 sha256 仍是 `4fbd3a104fcd16065800e4e730a67f7781698300eec33af8dc67cfd4f328c118`。
- 会话 A 跑完后的 jsonl 哈希，在会话 B 跑完时不变。
- 同一会话从 A 切到 B 之后的请求，system content 是 B 的那一条包装，不是 A。
- 两个会话同时 prompt，各自只有自己的那一条。
- 同一会话再 prompt 一次，system content 仍是同一条包装，标签只出现一次。含 `hello-b` 的会话文件在这次重试后，原先的字节仍是文件开头。

引擎自己的 `SystemPromptProjection` 仍可能在 `in-history` 时追加一条新的 `system/message` 事件。那是追加。这次看到的是另一会话的文件哈希不变，以及同一文件重试后旧字节还在开头。真机没跑。Anthropic 和 Responses 没有抓 HTTP，只核对了这份前缀里的转换源码。
