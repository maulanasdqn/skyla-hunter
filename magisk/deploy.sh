#!/usr/bin/env bash
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
MOD=/data/adb/modules/skyla-tools
SVC=/data/adb/service.d
TMP=/data/local/tmp

adb get-state >/dev/null 2>&1 || { echo "no adb device"; exit 1; }
adb shell 'su -c id -u' 2>/dev/null | grep -qx 0 || { echo "no root (su) on device"; exit 1; }

push() {
  local src="$1" dst="$2" base; base="$(basename "$dst")"
  adb push "$src" "$TMP/$base.deploy" >/dev/null
  adb shell "su -c '
    [ -f \"$dst\" ] && cp \"$dst\" \"$dst.bak.\$(date +%s)\"
    cat \"$TMP/$base.deploy\" > \"$dst\"
    chmod 755 \"$dst\"
    chcon u:object_r:system_file:s0 \"$dst\" 2>/dev/null || true
    rm -f \"$TMP/$base.deploy\"
  '"
  echo "  deployed $dst"
}

echo "Deploying skyla-tools…"
for f in kali-native nh dox kali-ok skybridge; do
  push "$HERE/skyla-tools/system/bin/$f" "$MOD/system/bin/$f"
done
push "$HERE/skyla-tools/module.prop" "$MOD/module.prop"
push "$HERE/service.d/nh-boot.sh" "$SVC/nh-boot.sh"

echo
echo "Health check:"
adb shell "su -c '$MOD/system/bin/kali-ok'"
