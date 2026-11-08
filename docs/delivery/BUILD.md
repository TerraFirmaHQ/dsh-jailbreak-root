# 构建

11071905 是手机上已经在用的那一版。11080152 是上一包，system 里还带着工具说明句。这轮版本号是 **11081033**。

runtime 不在 git 里。包 `runtime-arm64-v8a.tar.gz` 的 SHA256 是 `54a4640cd999689cf09e969e02b6a6943ef780d680c196adfa87aa483c306ca2`。包内 rootfs `bcfa37dd43a378d874077381450f769eee89ed80b755d48d04b800d7e5ee4bb3`（159528652 字节），libproot `aad737c5b546ee7471f133be90ef20f765aa98a303522557ceed3dff76605085`（259832 字节）。

```bash
export PATH="$HOME/dev/flutter/bin:$PATH"
export ANDROID_SDK_ROOT="$HOME/dev/android-sdk"
tooling/bootstrap-locked-build.sh <解压后的源码根> runtime-arm64-v8a.tar.gz 11081033
```

脚本核对 tarball 哈希，解到 `runtime/out`，写 `local.properties`，然后：

```bash
flutter build apk --release --flavor arm64 --target-platform android-arm64 \
  --build-number 11081033 \
  --dart-define=DSH_APP_VERSION=1.0.12+11081033
```

这次干净目录用的是 commit `72dbe708bf9375143ef7034cea6ed4484937ba81`。源码归档 SHA256 `ff09deddb2022c39ebe6b0b89bcccfe504782d05a55288d721c6088eaad910ed`（350953501 字节）。

Flutter 先打上本机 debug 签名。`app-arm64-release.apk` SHA256 `590394d44d25c09841cd1c669c4da44ed13f013ec53106a11104db52dd9435b8`（194460680 字节）。交付用的无签名包是再剥掉 APK Sig Block 42（12288 字节）并 `zipalign -p -f 4` 得到的，SHA256 `b82576f49efbf4354cb165d52a53ad5bee207da6eafc3b5ed8282ea2f4f7f8ea`（194448392 字节）。两份字节不同，不能说一致。`build-clean.log` 记下同一组数。退出码 0。`aapt dump badging` 读到 versionCode `11081033`、versionName `1.0.12`、包名 `io.github.ronyfork.dsh_app`。

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
| 引擎 | 官方 dsh-Go v1.0.12。Eta 对照见 CHANNEL-PROMPT.md，没有抄源码 |

安装前用盒子原 key 重签。不要卸载。
