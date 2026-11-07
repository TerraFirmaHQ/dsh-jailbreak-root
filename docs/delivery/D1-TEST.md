# D1 测试

```bash
/tmp/gradle/gradle-9.3.1/bin/gradle -p app/android :app:testArm64DebugUnitTest \
  --tests io.github.ccch1mneyyy.dsh_app.hostroot.CommandClassifierTest --offline
```

通过时，测试把 17 行 JSON 写入 `docs/delivery/D1-17-stdout.txt`。那 17 行是测试打印的原文。`D1-12-stdout.txt` 是上一轮的 12 例，不再当这轮的结果。
