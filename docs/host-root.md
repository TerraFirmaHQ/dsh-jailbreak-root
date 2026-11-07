# dsh Root

Upstream base: [ccch1mneyyy/dsh-Go](https://github.com/ccch1mneyyy/dsh-Go) **v1.0.12**, commit `d5ad25c5bc4c66ecacee76b3c5378db45e1db3fa` (annotated tag object `b75880dbde9d559a578ced3e0c8bb309e045c2d3`). Engine pins in `runtime/patches/pins.json` include DSH **0.2.2-rc.1**. Launcher name: **dsh Root**. Application id: `io.github.ronyfork.dsh_app`. `versionCode` is the UTC+8 build minute from `tooling/build-apk.sh` / `build.gradle.kts`, which is higher than a placeholder `1`.

## What is on

- Locked built-in prompt. Bytes are `app/assets/engine/hostprompt/prompt.md`, sha256 `4fbd3a104fcd16065800e4e730a67f7781698300eec33af8dc67cfd4f328c118` (21403 bytes, DeepSeek-4.1-Flash prompt). The plugin registers section `dsh:builtin-prompt` at order `-2000`, before the harness identity. It is mounted for normal, direct, chroot, and **safe** sessions. Safe mode still disables host-root tools and the optional persona, and the log says whether the prompt was mounted. If the asset is missing or the hash does not match, the patch is not written and no replacement text is invented.
- Persona is a separate section at `-1500`, only when the settings text is non-empty. Default is blank. There is no coordinator persona.
- **设置** main list, under 能力: a locked row for the built-in prompt (not editable), then **Root**, then **人设自定义**. Persona is not inside the Root page. A blank persona adds no section and does not replace the locked prompt. Saving does not run unless the person taps save, so an upgrade keeps a stored persona and a new install stays empty. It is also linked from 本机引擎 › 更多. Turning it on or off restarts a running normal engine so the tools mount or unmount; chats stay. Safe mode refuses the on position and does not call `su`. While the engine is stopped, the choice applies on the next start.
- Host root tools (`mcp__root__*`) default **off**. While off, the app does not call `su`. A command that needs approval opens a dialog. Dismissing it denies the command. The model does not sit on an invisible wait. `read_file` is a fixed `dd` of one absolute path, capped at 256 KiB, including `/data/adb`, with no approval dialog. `write_file` base64-decodes into the file so the bytes are not shell. Arbitrary `shell` always needs a preview, even when the text looks read-only. Text classification is not isolation. Protected targets (hiding stacks, modules, SELinux, remount, resetprop, `/data/adb` writes) are labeled; cancel runs nothing. There is no `/data/adb` ban.
- Optional chroot engine defaults **off**. PRoot remains the normal launch. Chroot drops to the app uid/gid and keeps the app's supplementary groups (including inet). Adoption checks handoff root, uid, `/opt/node`, cwd, and the token. Phone control reconnects only when that engine was started with the phone overlay; otherwise the log says to restart the engine.
- Nothing at startup changes mounts, SELinux, boot, or modules. Battery exemption is a settings action with read, write, readback. Refresh rate and CPU governors are not touched.

## Migration and rollback

Upstream already keeps `/root` across a runtime swap (`home.carry`) and replaces `/usr/local`, `/opt`, and apt packages that lived outside `/root`. Before that swap, `MigrationPreflight` writes `filesDir/migration/<time>/NOTES.txt` and, when it fits, a copy of `root/.dsh`.

Scheduled tasks written by DSH 0.2.2-rc.1 are not guaranteed to load on an older engine. Downgrade is not a way to keep them. Chats and app settings stay in `/root` and app storage when the **same signing certificate** covers the install.

## Signing

The installed app's certificate, checked from `dsh-rony-arm64-1.0.9-rony-ace7-11021745.apk` (versionCode 11021745), is Android Debug:

`SHA-256 58ff469f18dc624e76b9a416128391548e847062edc8a00bc2f28a7932084402`

The matching keystore stays on the build machine. This tree does not contain it, and this environment does not sign with a new debug key and call that continuity. The APK handed over here is the complete package **before** that re-sign. `versionCode` is the UTC+8 build minute and must be greater than 11021745. Package id stays `io.github.ronyfork.dsh_app`.

## Launcher

The default launcher glyph is the first frame of the DSH-TUI pixel whale girl (`app/tool/pet_src/whale-girl/sheets/idle.png`, x=13..61). The next frame is not included. Nearest-neighbor scale, kept inside the adaptive 66/108 safe circle. Legacy PNGs and the adaptive foreground are RGBA with alpha 0 outside the character; the adaptive background drawable is Android's transparent color. A launcher can still paint its own plate. ColorOS often does. Copyright stays with ccch1mneyyy; see `ASSETS.md`. The in-app brand mark is unchanged.

## Provenance

| Source | Adopted | Not adopted |
| --- | --- | --- |
| ghjicb/dsh-Go (AGPL-3.0) | `su` stdin script, marker so a refusal is not a command failure, output spool, timeout, process-tree kill, approval preview | Any path that skips approval. Shell text is not treated as a sandbox. |
| nianyuechan/DSH-GO-ROOT `301fb10`, `7c3f8cc` (AGPL-3.0) | Optional chroot, drop to app uid/gid plus groups, no second `/dev` mount, adoption by identity/workdir/port/token, targeted pid stop, phone-control reconnect vs notice, battery read/write/readback | Making chroot the default. Refresh-rate or governor tuning. Accessibility auto-grant. |
| Rakanrkk/dsh-Su v1.2.0-dev.2 (AGPL-3.0) | Upstream `TarExtractor.resolve` already rejects paths that escape the rootfs. That check stays. | libsu `RootService` as a root-mounted default runtime. |

QQ, NapCat, and SnowLuma are not in this tree. Upstream phone, PRoot, direct launch, and the rest of v1.0.12 stay.

## 中文

桌面名 **dsh Root**。包名 `io.github.ronyfork.dsh_app`。Root 在设置的能力区第一行，不在引擎页深处。主机 root 与 chroot 默认关，关时不调用 `su`。打开后重启本机引擎才挂上工具。需要确认的命令会弹出对话框，关掉就是拒绝。模型超过 45 秒没有第一个字会说明，三分钟仍没有就停止这一轮，可以再发。`read_file` 可读 `/data/adb`。写入和任意 shell 必须先预览。没有 `/data/adb` 路径封禁。内置 4.1 提示词字节锁定，正常、直接启动、chroot、安全模式都会挂上；资源不对就留空，不编造。人设默认空白。升级保留 `/root`，`/usr/local` 与 `/opt` 要重装。0.2.2 写入的定时任务不保证旧引擎能读。没有发布证书时不能承诺覆盖安装。
