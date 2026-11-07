# 改动文件

编号是需求条目，不是行号。一行可以对应多条。

| 文件 | 条目 |
|---|---|
| `app/android/.../hostroot/CommandClassifier.kt` | D1 R4 |
| `app/android/.../hostroot/CommandClassifierTest.kt` | D1 D9 D10 |
| `app/android/.../hostroot/RootTools.kt` | D1 D3 D4 D5 D6 D7 D8 D9 D10 R2 |
| `app/android/.../hostroot/RootFiles.kt` | D7 |
| `app/android/.../hostroot/RootExec.kt` | D9 R2 |
| `app/android/.../hostroot/HostRootControl.kt` | D2 D3 R1 R3 R4 |
| `app/android/.../hostroot/HostRootPlugin.kt` | D2 D3 R4 |
| `app/android/.../hostroot/HostRootGuest.kt` | D3 文案 |
| `app/android/.../runtime/ChrootLaunch.kt` | 禁挂载 |
| `app/android/.../runtime/RuntimeController.kt` | 禁挂载。启动不再为 chroot 调用 su |
| `app/android/.../runtime/DshProcess.kt` | R1 日志 |
| `app/android/.../phone/PhoneCore.kt` | D11 |
| `app/lib/state/host_root_state.dart` | D3 R4 |
| `app/lib/ui/settings/host_root_page.dart` | D3 禁挂载 |
| `app/lib/ui/settings/host_root_approval.dart` | D2 |
| `app/lib/ui/settings/host_persona_page.dart` | P2-2 P2-3 P2-4 |
| `app/lib/l10n/parts/hostroot_zh.arb` `hostroot_en.arb` 及生成物 | D2 D3 P2 G5 |
| `app/lib/l10n/parts/chat_*.arb` `state_*.arb` | D12 |
| `app/lib/ui/update/update_launch_check.dart` | G1 |
| `app/lib/ui/update/update_tiles.dart` | G1 G5 |
| `app/lib/state/update_state.dart` | G1 G3 |
| `app/lib/ui/settings/settings_screen.dart` | G1 |
| `app/lib/ui/chat/chat_list.dart` | G1 G5 |
| `app/lib/usage/usage_stats.dart` | G2 |
| `app/test/settings/host_persona_page_test.dart` | P2 |
| `app/test/update/update_golden_test.dart` | G1 |
| `app/test/chat/notice_action_test.dart` | G1 |
| `app/android/.../hostroot/RootHostTest.kt` | R1 日志字符串 |
| `docs/delivery/*` | 交付文档 |

没有改 `prompt.md`。没有改桌面图标。没有改包名。没有改 KSU、Zygisk、HMA。
