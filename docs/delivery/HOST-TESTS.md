# 这轮 host 测试

都在本机跑过。runner、`Process`、时钟和 HTTP 是注入的假对象。没有 su，没有 Ace7。

## JVM

```bash
/tmp/gradle/gradle-9.3.1/bin/gradle -p app/android :app:testArm64DebugUnitTest \
  --tests io.github.ccch1mneyyy.dsh_app.hostroot.HostRootSpecHostTest \
  --tests io.github.ccch1mneyyy.dsh_app.phone.A11yDownHostTest --offline
```

结果：`BUILD SUCCESSFUL`。`HostRootSpecHostTest` 9 项，0 失败。`A11yDownHostTest` 1 项，0 失败。时间戳 2026-11-07T09:51:08Z。

覆盖：D2 第三按钮只给只读误判；未知和写入没有 session key，写入即使 `sessionAllows` 为 true 也要问。D6 超时的 stage/error/next。D7 默认 256KB、上限 1MB、`needChunk`。D9 stdout 超过 64KB 的摘要、stderr 分字段、90 秒删 spool、90 秒后 job 出表。R2 两个任务封顶、查询、取消。R3 审计行没有命令输出。R4 路径归一和 `clearSessionMemory`。

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

结果都是 `All tests passed`。覆盖：D2 对话框、D12 文案、P2 人设不含破甲 hash、G2 产品路径不上传、G4 旧 rootfs 不进欢迎页、G1/G5 关于页只有锁定版本说明。

## 不好在 host 上冒充通过的

- 真 su、KernelSU 弹窗、180 秒真实超时杀进程树。
- `root_audit.jsonl` 写到 `/data/user/0/.../files/` 需要 Android `filesDir`。序列化本身已测。
- 无障碍服务在真机上掉线之后的系统行为。JVM 测的是 `PhoneCore` 在 `driver==null` 时的返回。
- 附件选择器真的打不开文件。测的是失败文案。
- D9 引擎进程里的 projection / prompt cache。没有接口，见 `D9-ENGINE-CACHE.md`。
- 20 轮手机内存和模型 token。
