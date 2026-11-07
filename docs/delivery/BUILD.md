# 构建

这条命令在干净的已装好依赖的目录里跑过，退出码 0。产物随后去掉 APK Sig Block 42 并 zipalign。安装前必须用盒子上原来的 key 重签。本机调试证书不是那把 key。

```bash
export PATH="$HOME/dev/flutter/bin:$PATH"
cd app
flutter build apk --release --flavor arm64 --target-platform android-arm64 \
  --build-number 11071721 \
  --dart-define=DSH_APP_VERSION=1.0.12+11071721
```

版本：

| 工具 | 版本 |
|---|---|
| Flutter | 3.47.7 stable，revision abaf9c5237 |
| Dart | 3.13.5 |
| JDK | OpenJDK 21.0.12.1 |
| Android Gradle Plugin 使用的 Gradle | 9.3.1（`/tmp/gradle/gradle-9.3.1`，仓库里没有 gradlew） |
| Android SDK platforms | android-35、android-36 |
| build-tools | 36.0.0 |
| NDK | 28.2.13676358 |
| CMake | 3.22.1（SDK 里已有；这次 APK 构建没有重新编 native） |
| 主机 Node | v22.23.4。打 APK 没有用它编译 Dart |
| 引擎 rootfs 里的 Node | 24.22.1（钉在 runtime，不是这次主机 `node`） |
| 引擎 | 官方 dsh-Go v1.0.12，DSH 0.2.2-rc.1。eta 见 REUSE.md，未定 |

包：`io.github.ronyfork.dsh_app`，versionName `1.0.12`，versionCode `11071721`（大于 11071614）。

第二次构建（含最后的 Dart 改动）退出码 0：`Built build/app/outputs/flutter-apk/app-arm64-release.apk (194.4MB)`。

然后去掉 APK Sig Block 42（12288 字节），把中央目录偏移改到块的起点，再 `zipalign -p -f 4`。`zipalign -c` 和 `unzip -t` 通过。`apksigner verify` 会因为没有 `META-INF/MANIFEST.MF` 失败，这是去掉签名块之后的预期。

| 项 | 值 |
|---|---|
| 包名 | io.github.ronyfork.dsh_app |
| versionName | 1.0.12 |
| versionCode | 11071721 |
| 无签名整包大小 | 194417505 |
| 无签名整包 SHA256 | `87a3c6a8f1a14db90ecdc62732c176bc216167224f8be89249984b89467e5aa9` |
| prompt.md | 21403 字节，`4fbd3a104fcd16065800e4e730a67f7781698300eec33af8dc67cfd4f328c118` |
| 分片 | 19000000 字节，`.00`–`.10`，`cat` 后与整包 `cmp` 相同 |

分片校验在 `dist/spec-11071721/SHA256SUMS`。源码归档是这次提交的 `git archive`，不含 APK、不含私钥、不含用户诊断包。
