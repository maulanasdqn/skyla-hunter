#!/system/bin/sh

K=/data/local/nhsystem/kali-arm64

sleep 20
[ -d "$K" ] || exit 0

mountpoint -q "$K/dev"   || mount -o bind /dev "$K/dev" 2>/dev/null
mountpoint -q "$K/sdcard" || mount -o bind /storage/emulated/0 "$K/sdcard" 2>/dev/null

dumpsys deviceidle disable >/dev/null 2>&1
