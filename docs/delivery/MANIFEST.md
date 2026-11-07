# 交付文件

这些是给盒子审的独立小文件。大归档和 APK 分片另附，不代替这些文件。

| 文件 | 内容 |
|---|---|
| SPEC-COVERAGE.md | F/D/R/P/G 逐项：实现 / 本机测试 / 真机未验证 |
| CHANGED-FILES.md | 改动文件到条目的映射 |
| REUSE.md | 五方向各至少两个候选的仓库、star、commit、License，以及实际复用表 |
| D1-17-stdout.txt | 分类器 17 例原始输出（R1–R10 只读，W1–W7 写入） |
| D1-12-stdout.txt | 上一轮 12 例，保留对照 |
| D1-TEST.md | 跑这 12 例的命令 |
| D9-ENGINE-CACHE.md | 引擎缓存为什么不能算完成 |
| PERF-host.txt | 本机 JVM 摘要，不是手机 |
| BUILD.md | 版本、命令、干净目录构建 |
| ROLLBACK.md | 回滚 |
| UNFINISHED.md | 未完成和卡点 |
| HOST-TESTS.md | 这轮 host 测试命令和结果 |
| build-clean.log | 干净目录构建日志。记录源码归档 SHA、runtime SHA、解压 commit、`app-arm64-release.apk` 的 SHA 和大小、剥签名后的 SHA，以及转换命令。签名包和无签名包不是同一份字节 |

D1 测试命令：

```bash
/tmp/gradle/gradle-9.3.1/bin/gradle -p app/android :app:testArm64DebugUnitTest \
  --tests io.github.ccch1mneyyy.dsh_app.hostroot.CommandClassifierTest \
  --tests io.github.ccch1mneyyy.dsh_app.hostroot.EngineCacheGapTest --offline
```
