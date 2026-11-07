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

历史 `[{"role":"system","content":"OLD"},{"role":"user","content":"hi"}]` 在调用前后字节相同。发出去的副本不再带 OLD。

## 校验

空文本是清空，可以保存。其它文本：不能以 BOM 开头，首行不能是空行，不能以 ``` 结尾。不通过就标红并且不写文件。不改 CRLF，不去掉末尾空格。没有新的字数上限。5000 个「汉」加 🙂 能原样读回。

## 没跑成的

没有 `DSH_PREFIX`，所以没有把真引擎的 HTTP 体打到外部模型上。组装函数是本机测的。引擎如果在 `system-prompt/assemble` 的 context 里不带 provider，钩子按 null/true 用默认，不用另一家渠道的文本。会话 jsonl 没有被这轮代码打开或改写。真机没跑。
