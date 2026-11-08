#!/bin/sh
# Place the pinned arm64 runtime next to a clean git archive, then build.
# The runtime tree is not in git (runtime/out is ignored). The tarball hash
# is the pin. This script does not download it.
#
#   tooling/bootstrap-locked-build.sh <source-root> <runtime-arm64.tar.gz> <build-number>
#
# Requires Flutter 3.47.7 on PATH, Android SDK in ANDROID_SDK_ROOT or
# ANDROID_HOME, JDK 21, and the Gradle 9.3.1 install the app already uses
# when the wrapper is absent (GRADLE_USER_HOME / a gradle on PATH).
set -eu
root=${1:?source root}
tar=${2:?runtime tarball}
code=${3:?build number}
want=$(cat "$root/runtime/ARM64-ROOTFS.sha256")
got=$(sha256sum "$tar" | awk '{print $1}')
if [ "$got" != "$want" ]; then
  echo "runtime tarball sha256 $got != $want" >&2
  exit 1
fi
mkdir -p "$root/runtime/out"
tar -C "$root/runtime/out" -xzf "$tar"
sdk=${ANDROID_SDK_ROOT:-${ANDROID_HOME:?set ANDROID_SDK_ROOT}}
flutter_sdk=$(dirname "$(dirname "$(command -v flutter)")")
printf 'sdk.dir=%s\nflutter.sdk=%s\n' "$sdk" "$flutter_sdk" > "$root/app/android/local.properties"
cd "$root/app"
flutter build apk --release --flavor arm64 --target-platform android-arm64 \
  --build-number "$code" \
  --dart-define=DSH_APP_VERSION=1.0.12+$code
