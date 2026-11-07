# D1 测试

```bash
/tmp/gradle/gradle-9.3.1/bin/gradle -p app/android :app:testArm64DebugUnitTest \
  --tests io.github.ccch1mneyyy.dsh_app.hostroot.CommandClassifierTest --offline
```

通过时，测试把 12 行 JSON 打到 stdout，并写入 `docs/delivery/D1-12-stdout.txt`。原始 12 行就是那个文件，不要改写。
