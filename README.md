# dsh-jailbreak-root

**Jailbreak + root edition of dsh-Go** — an AI agent that runs on your Android phone, forked from [dsh-Go](https://github.com/ccch1mneyyy/dsh-Go) v1.0.9.

> Licensed under **AGPL-3.0**, same as upstream. All changes are open source.

## What it does

Runs an AI assistant on your phone that can actually do things: chat, call tools, run a Linux environment, and, when you allow it, use root for system-level tasks.

## Features

- **Built-in jailbreak system prompt**: a tuned prompt (`app/assets/engine/pojia/prompt.md`) is embedded verbatim as the first segment of every session's system prompt and locked in settings. It cuts needless refusals, boilerplate and over-clarifying. The original tool prompts follow it unchanged.
- **Custom persona**: no persona is built in; write your own in settings (blank by default).
- **Optional root mode**: master switch, off by default. When off, the app never requests `su`. When on, it is read-only first and asks before changing anything. Directories and configs of root-hiding stacks (KernelSU, Zygisk Next, HMA, Tricky Store, Shamiko and similar) are on a hard blacklist: never read or written, no module installs, no mount or SELinux changes.
- **Optional Linux enhancement (chroot)**: separate switch, off by default; proot is used otherwise.
- **High refresh rate**: follows the system rate by default, with a manual display-mode picker (up to 165 Hz) for smooth streaming output.
- **Slimmer**: the QQ integration components were removed.

## Build

Flutter + Android (Kotlin). See the upstream docs (`README.upstream.md`, `docs/`) for build instructions.

## License

AGPL-3.0, see `LICENSE`. Upstream third-party notices are in `THIRD_PARTY_NOTICES.md`.

---

### 中文简介

dsh-Go 的「越狱 + root 版」分支：手机端 AI 智能体，内置锁定的越狱系统提示词、人设自定义（默认空白）、可选 root 模式（默认关，带隐藏环境黑名单）、可选 chroot Linux、可选高刷新率，已移除 QQ 接入。许可证 AGPL-3.0。
