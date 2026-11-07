# 构建

工作区里编过的 11071721 不算这次交付。这次要证明的是：`git archive` 解到一个空目录，配上钉死哈希的 runtime 包，再跑下面这一条。

runtime 不在 git 里（`runtime/out` 被忽略）。包是 `runtime-arm64-v8a.tar.gz`。

| 文件 | SHA256 | 大小 |
|---|---|---|
| runtime-arm64-v8a.tar.gz | `54a4640cd999689cf09e969e02b6a6943ef780d680c196adfa87aa483c306ca2` | 见交付目录 |
| 包内 rootfs-arm64-v8a.tar.xz | `bcfa37dd43a378d874077381450f769eee89ed80b755d48d04b800d7e5ee4bb3` | 159528652 |
| 包内 libproot.so | `aad737c5b546ee7471f133be90ef20f765aa98a303522557ceed3dff76605085` | 259832 |

```bash
export PATH="$HOME/dev/flutter/bin:$PATH"
export ANDROID_SDK_ROOT="$HOME/dev/android-sdk"
tooling/bootstrap-locked-build.sh <解压后的源码根> runtime-arm64-v8a.tar.gz 11071738
```

脚本会核对 tarball 哈希、解到 `runtime/out`、写 `app/android/local.properties`，然后执行：

```bash
flutter build apk --release --flavor arm64 --target-platform android-arm64 \
  --build-number 11071738 \
  --dart-define=DSH_APP_VERSION=1.0.12+11071738
```

工具版本（这台机器）：

| 工具 | 版本 |
|---|---|
| Flutter | 3.47.7 stable，revision abaf9c5237 |
| Dart | 3.13.5 |
| JDK | OpenJDK 21.0.12.1 |
| Gradle | 9.3.1。仓库没有 gradlew，Flutter 使用本机已缓存的 Gradle |
| Android SDK platforms | android-35、android-36 |
| build-tools | 36.0.0 |
| NDK | 28.2.13676358 |
| 主机 Node | v22.23.4。打 APK 不用它编译 Dart |
| rootfs 内 Node | 24.22.1 |
| 引擎 | 官方 dsh-Go v1.0.12。eta 未定，见 REUSE.md |

干净目录的原始日志是交付包里的 `build-clean.log`，不在这次 git 提交里。签名块去掉和分片哈希也写在那次日志之后的 APK README 里。安装前用盒子原 key 重签。
