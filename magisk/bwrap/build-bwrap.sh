#!/bin/bash
# build-bwrap.sh — build an EINVAL-tolerant bubblewrap for the NetHunter chroot.
#
# RUN THIS INSIDE THE KALI CHROOT (e.g. `nh bash /root/build-bwrap.sh`, or copy
# it in and run from a `kali-native` shell). It is idempotent.
#
# Why patched: on this device the Kali rootfs is a directory subtree on the
# f2fs /data partition, not a first-class mount. Stock bwrap dies at
#   bwrap: Failed to make / slave: Invalid argument
# and later at "Failed to make old root rprivate", because the kernel rejects
# propagation changes on that /data-backed mount (only tmpfs accepts them).
# kali-native already runs bwrap inside a private mount namespace AND enters via
# pivot_root (so / is a real mount, which bwrap's own pivot_root needs), so
# those two propagation calls are redundant here. This patch makes just those
# two calls tolerate EINVAL instead of aborting. Everything else is stock.
#
# Needs kali-native's pivot_root entry to actually run bwrap — a plain chroot
# root still can't pivot. See magisk/skyla-tools/system/bin/kali-native.
set -euo pipefail

VER=0.13.1
SRC="https://github.com/containers/bubblewrap/releases/download/v${VER}/bubblewrap-${VER}.tar.xz"
WORK=/root/bwbuild

echo "[bwrap] install build deps"
apt-get install -y --no-install-recommends \
  gcc meson ninja-build pkg-config libcap-dev ca-certificates curl xz-utils >/dev/null

echo "[bwrap] fetch + extract ${VER}"
rm -rf "$WORK"; mkdir -p "$WORK"; cd "$WORK"
curl -fsSL -o bw.tar.xz "$SRC"
tar xf bw.tar.xz
cd "bubblewrap-${VER}"

echo "[bwrap] patch: tolerate EINVAL on the two /-propagation calls"
python3 - "$PWD/bubblewrap.c" <<'PY'
import sys
p = sys.argv[1]
s = open(p, encoding="utf-8").read()
pairs = [
 ('''  if (mount (NULL, "/", NULL, MS_SILENT | MS_SLAVE | MS_REC, NULL) < 0)
    die_with_mount_error ("Failed to make / slave");''',
  '''  if (mount (NULL, "/", NULL, MS_SILENT | MS_SLAVE | MS_REC, NULL) < 0)
    {
      /* skyla-nethunter: /data-backed rootfs rejects propagation changes on /
         (EINVAL); kali-native already runs bwrap in a private mount ns, so
         skipping this cannot leak to the real host. */
      if (errno != EINVAL)
        die_with_mount_error ("Failed to make / slave");
    }'''),
 ('''  if (mount ("oldroot", "oldroot", NULL, MS_SILENT | MS_REC | MS_PRIVATE, NULL) != 0)
    die_with_mount_error ("Failed to make old root rprivate");''',
  '''  if (mount ("oldroot", "oldroot", NULL, MS_SILENT | MS_REC | MS_PRIVATE, NULL) != 0)
    {
      /* skyla-nethunter: same /data-backed-rootfs EINVAL; tolerated because the
         outer namespace is already private. */
      if (errno != EINVAL)
        die_with_mount_error ("Failed to make old root rprivate");
    }'''),
]
for old, new in pairs:
    if s.count(old) != 1:
        if new.split("\n")[2] in s:
            print("[bwrap] already patched"); continue
        sys.exit(f"patch anchor not found/unique: {old[:40]!r}")
    s = s.replace(old, new)
open(p, "w", encoding="utf-8").write(s)
print("[bwrap] patched bubblewrap.c")
PY

echo "[bwrap] build"
rm -rf _build
meson setup _build -Dman=disabled -Dtests=false >/dev/null
ninja -C _build >/dev/null

echo "[bwrap] install (backup original)"
[ -f /usr/bin/bwrap ] && cp /usr/bin/bwrap "/usr/bin/bwrap.orig.$(date +%s)"
install -m755 _build/bwrap /usr/bin/bwrap
/usr/bin/bwrap --version
echo "[bwrap] done — run bwrap inside a kali-native session"
