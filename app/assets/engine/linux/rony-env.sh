#!/system/bin/sh
# Rony fork (AGPL-3.0, part of dsh-go-rony): the root Linux environment.
# Debian arm64 in a real chroot inside a private mount namespace (unshare -m): every mount lives
# only as long as the command, nothing leaks into Android's namespace, nothing to clean up after a
# crash. Run as root (the app pipes it to su). POSIX sh only (Android mksh / busybox ash / dash).
#
#   rony-env.sh preflight                 can this phone do it (root, unshare, chroot, busybox)?
#   rony-env.sh status                    installed? size, version, backend
#   rony-env.sh install TARBALL SHA256    verify and unpack the pinned Debian tarball, first-boot setup
#   rony-env.sh exec CMD [CWD]            run CMD with bash -lc inside the chroot
#   rony-env.sh wipe                      delete the environment
#   rony-env.sh qq <rony-qq.sh args>      the local SnowLuma/NapCat stack (rony-qq.sh, run inside)
#
# Env: RONY_ROOT (default /data/local/rony/debian), RONY_SDCARD (default /storage/emulated/0),
#      RONY_PROXY (http proxy passed into the chroot), RONY_DNS (space separated nameservers).

set -u
R="${RONY_ROOT:-/data/local/rony/debian}"
BASE="$(dirname "$R")"
SDCARD="${RONY_SDCARD:-/storage/emulated/0}"
VERSION_FILE="$R/etc/rony-env.version"
ENV_VERSION="1"

say() { printf '%s\n' "$*"; }
die() { printf 'ERROR: %s\n' "$*" >&2; exit "${2:-1}"; }

# busybox from the root manager first (full applets: unshare --propagation, tar -J, mount --rbind).
BB=""
for b in /data/adb/ksu/bin/busybox /data/adb/ap/bin/busybox /data/adb/magisk/busybox /data/adb/modules/busybox-ndk/system/*/busybox /system/xbin/busybox /system/bin/busybox; do
  [ -x "$b" ] && { BB="$b"; break; }
done
bb() { if [ -n "$BB" ]; then "$BB" "$@"; else "$@"; fi; }

installed() { [ -x "$R/bin/sh" ] || [ -x "$R/usr/bin/sh" ]; }

preflight() {
  say "uid=$(id -u)"
  [ "$(id -u)" = 0 ] || { say "result=fail reason=not_root"; return 2; }
  say "busybox=${BB:-none}"
  say "kernel=$(uname -r)"
  say "arch=$(uname -m)"
  say "selinux=$(getenforce 2>/dev/null || echo unknown)"
  if bb unshare -m sh -c 'mount -t tmpfs rony-probe /mnt 2>/dev/null || mount -t tmpfs rony-probe /tmp 2>/dev/null || true; echo ok' 2>/dev/null | grep -q ok; then
    say "unshare=ok"
  else
    say "unshare=fail"; say "result=fail reason=unshare"; return 3
  fi
  command -v chroot >/dev/null 2>&1 || [ -n "$BB" ] || { say "chroot=missing"; say "result=fail reason=chroot"; return 4; }
  say "chroot=ok"
  free=$(df -k "$(dirname "$BASE")" 2>/dev/null | awk 'NR==2{print $4}')
  say "free_kb=${free:-?}"
  say "result=ok"
}

status() {
  if installed; then
    say "installed=yes"
    say "version=$(cat "$VERSION_FILE" 2>/dev/null || echo ?)"
    say "os=$(. "$R/etc/os-release" 2>/dev/null; echo "${PRETTY_NAME:-?}")"
    say "size_kb=$(du -sk "$R" 2>/dev/null | awk '{print $1}')"
  else
    say "installed=no"
  fi
  say "root=$R"
}

# One-time fixes so apt and networking behave on Android (paranoid networking: only AID_INET may
# open sockets; apt's _apt sandbox user is not in it, so apt runs as root).
first_boot() {
  mkdir -p "$R/etc/apt/apt.conf.d" "$R/sdcard" "$R/storage/emulated/0" "$R/dev/shm" "$R/tmp" "$R/run"
  chmod 1777 "$R/tmp"
  printf 'APT::Sandbox::User "root";\nAcquire::Retries "3";\n' > "$R/etc/apt/apt.conf.d/99rony"
  for g in "aid_inet:3003" "aid_net_raw:3004" "aid_net_admin:3005" "aid_sdcard_rw:1015" "aid_everybody:9997"; do
    n="${g%%:*}"; i="${g##*:}"
    grep -q "^$n:" "$R/etc/group" 2>/dev/null || printf '%s:x:%s:root\n' "$n" "$i" >> "$R/etc/group"
  done
  printf '127.0.0.1 localhost\n::1 localhost ip6-localhost\n127.0.1.1 rony\n' > "$R/etc/hosts"
  printf 'rony\n' > "$R/etc/hostname"
  # Mirror: TUNA first (reachable from CN), deb.debian.org as fallback via apt's own retry.
  for f in "$R/etc/apt/sources.list" "$R"/etc/apt/sources.list.d/*.sources; do
    [ -f "$f" ] || continue
    grep -q tuna "$f" && continue
    cp "$f" "$f.orig"
    sed -i 's#http://deb.debian.org/debian#https://mirrors.tuna.tsinghua.edu.cn/debian#g; s#http://security.debian.org/debian-security#https://mirrors.tuna.tsinghua.edu.cn/debian-security#g' "$f"
  done
  printf '%s\n' "$ENV_VERSION" > "$VERSION_FILE"
}

install() {
  tarball="$1"; want="$2"
  [ -f "$tarball" ] || die "tarball not found: $tarball"
  got=$(bb sha256sum "$tarball" | awk '{print $1}')
  [ "$got" = "$want" ] || die "sha256 mismatch: got $got want $want" 5
  say "sha256=ok"
  mkdir -p "$R" || die "cannot create $R"
  # proot-distro tarballs carry one top directory (debian-trixie-aarch64/).
  top=$(bb tar -tJf "$tarball" 2>/dev/null | head -1 | cut -d/ -f1)
  if [ -n "$top" ]; then
    bb tar -xJf "$tarball" -C "$R" --strip-components=1 || die "unpack failed (tar -J)"
  else
    xzcat "$tarball" | bb tar -x -C "$R" --strip-components=1 || die "unpack failed (xzcat)"
  fi
  installed || die "unpacked tree has no /bin/sh"
  first_boot
  say "installed=yes"
}

# The chroot itself. $1 = command for bash -lc, $2 = cwd. Mounts are private and die with us.
enter() {
  installed || die "environment not installed" 10
  cmd="$1"; cwd="${2:-/root}"
  dns="${RONY_DNS:-}"
  if [ -z "$dns" ]; then
    for p in net.dns1 net.dns2; do v=$(getprop "$p" 2>/dev/null); [ -n "$v" ] && dns="$dns $v"; done
    dns="$dns 223.5.5.5 1.1.1.1"
  fi
  rm -f "$R/etc/resolv.conf"
  for d in $dns; do printf 'nameserver %s\n' "$d"; done > "$R/etc/resolv.conf"
  export R SDCARD cmd cwd RONY_PROXY="${RONY_PROXY:-}" BBX="$BB"
  bb unshare -m sh -c '
    b() { if [ -n "$BBX" ]; then "$BBX" "$@"; else "$@"; fi; }
    b mount --make-rprivate / 2>/dev/null
    b mount -t proc proc "$R/proc" || exit 125
    b mount -t sysfs sys "$R/sys" 2>/dev/null || b mount --rbind /sys "$R/sys" 2>/dev/null
    b mount --rbind /dev "$R/dev" || exit 125
    b mount -t devpts -o gid=5,mode=620,ptmxmode=666,newinstance devpts "$R/dev/pts" 2>/dev/null
    mkdir -p "$R/dev/shm"; b mount -t tmpfs -o mode=1777,size=512m tmpfs "$R/dev/shm" 2>/dev/null
    b mount -t tmpfs -o mode=755 tmpfs "$R/run" 2>/dev/null
    if [ -d "$SDCARD" ]; then
      b mount --rbind "$SDCARD" "$R/storage/emulated/0" 2>/dev/null
      [ -L "$R/sdcard" ] || { rmdir "$R/sdcard" 2>/dev/null; ln -s /storage/emulated/0 "$R/sdcard" 2>/dev/null; }
    fi
    [ -d /system ] && { mkdir -p "$R/android/system"; b mount --bind -o ro /system "$R/android/system" 2>/dev/null; }
    [ -d /data/local/tmp ] && { mkdir -p "$R/android/tmp"; b mount --bind /data/local/tmp "$R/android/tmp" 2>/dev/null; }
    if [ -n "$BBX" ]; then set -- "$BBX" chroot; else set -- chroot; fi
    exec "$@" "$R" /usr/bin/env -i HOME=/root USER=root LOGNAME=root SHELL=/bin/bash TERM=xterm-256color LANG=C.UTF-8 \
      PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin TMPDIR=/tmp DEBIAN_FRONTEND=noninteractive \
      http_proxy="$RONY_PROXY" https_proxy="$RONY_PROXY" HTTP_PROXY="$RONY_PROXY" HTTPS_PROXY="$RONY_PROXY" no_proxy=127.0.0.1,localhost \
      RONY_CWD="$cwd" RONY_CMD="$cmd" /bin/bash -lc "cd \"\$RONY_CWD\" 2>/dev/null || cd /root; eval \"\$RONY_CMD\""
  '
}

wipe() {
  # Mounts are namespace-private, so nothing can be mounted under $R from here; refuse anyway if it is.
  if grep -q " $R/" /proc/mounts 2>/dev/null; then die "something is still mounted under $R" 6; fi
  rm -rf "$R" && say "wiped=yes"
}

# ── QQ stack (local SnowLuma / NapCat) ──
# The stack itself lives in rony-qq.sh (shared with the no-root proot host). Here we only copy it
# and its settings (RONY_QQ_*, RONY_SL_*, RONY_NC_*, RONY_GH_MIRRORS, RONY_PROXY) into the chroot.
QQ_DIR=/opt/rony-qq
qq() {
  installed || { say "env=missing"; return 0; }
  here="$(cd "$(dirname "$0")" && pwd)"
  [ -f "$here/rony-qq.sh" ] || die "rony-qq.sh missing next to rony-env.sh"
  mkdir -p "$R$QQ_DIR"
  cp "$here/rony-qq.sh" "$R$QQ_DIR/rony-qq.sh"
  ( umask 077; env | grep -E '^(RONY_QQ_|RONY_SL_|RONY_NC_|RONY_GH_MIRRORS=|RONY_PROXY=|RONY_APT_MIRROR=|RONY_TAIL=)' \
      | sed "s/'/'\\\\''/g; s/^\([^=]*\)=\(.*\)\$/export \1='\2'/" > "$R$QQ_DIR/env.sh"; echo "export RONY_QQ_HOST=chroot" >> "$R$QQ_DIR/env.sh" )
  args=""; for a in "$@"; do args="$args '$(printf '%s' "$a" | sed "s/'/'\\\\''/g")'"; done
  enter "sh $QQ_DIR/rony-qq.sh$args"
}

case "${1:-}" in
  preflight) preflight ;;
  status) status ;;
  install) shift; install "$@" ;;
  exec) shift; enter "$@" ;;
  wipe) wipe ;;
  qq) shift; qq "$@" ;;
  *) die "usage: rony-env.sh preflight|status|install|exec|wipe|qq" ;;
esac
