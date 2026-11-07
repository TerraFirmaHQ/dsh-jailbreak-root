# 构建

11071738 只是上一轮暂存，不是这轮的包。这轮版本号是 **11071753**。

runtime 不在 git 里。包 `runtime-arm64-v8a.tar.gz` 的 SHA256 是 `54a4640cd999689cf09e969e02b6a6943ef780d680c196adfa87aa483c306ca2`。包内 rootfs `bcfa37dd43a378d874077381450f769eee89ed80b755d48d04b800d7e5ee4bb3`（159528652 字节），libproot `aad737c5b546ee7471f133be90ef20f765aa98a303522557ceed3dff76605085`（259832 字节）。

```bash
export PATH="$HOME/dev/flutter/bin:$PATH"
export ANDROID_SDK_ROOT="$HOME/dev/android-sdk"
tooling/bootstrap-locked-build.sh <解压后的源码根> runtime-arm64-v8a.tar.gz 11071753
```

脚本核对 tarball 哈希，解到 `runtime/out`，写 `local.properties`，然后：

```bash
flutter build apk --release --flavor arm64 --target-platform android-arm64 \
  --build-number 11071753 \
  --dart-define=DSH_APP_VERSION=1.0.12+11071753
```

Flutter 先打上本机 debug 签名。交付用的无签名包是再剥掉 APK Sig Block 42 并 `zipalign -p -f 4` 得到的。两份字节不同，不能说一致。`build-clean.log` 同时记下：源码归档 SHA、runtime SHA、解压 commit、`app-arm64-release.apk` 的 SHA 和大小、剥签名后的 SHA 和那条转换命令。

| 工具 | 版本 |
|---|---|
| Flutter | 3.47.7 stable，revision abaf9c5237 |
| Dart | 3.13.5 |
| JDK | OpenJDK 21.0.12.1 |
| Gradle | 9.3.1。仓库没有 gradlew |
| platforms | android-35、android-36 |
| build-tools | 36.0.0 |
| NDK | 28.2.13676358 |
| 主机 Node | v22.23.4。打 APK 不用它编译 Dart |
| rootfs 内 Node | 24.22.1 |
| 引擎 | 官方 dsh-Go v1.0.12。eta 未定 |

安装前用盒子原 key 重签。不要卸载。
