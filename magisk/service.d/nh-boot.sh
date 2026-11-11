#!/system/bin/sh
# Skyla Tools post-boot: keep the Kali chroot usable.
#
# Installed to /data/adb/service.d/nh-boot.sh (runs late-start, as root).
#
# kali-native now isolates its own mounts in a private mount namespace
# (PATCH-PNODE-PLAN.md §4), so this boot script only establishes the two
# long-lived host binds NetHunter expects (/dev and /sdcard). It deliberately
# does NOT mount /proc or /sys — those are created per session by kali-native
# and torn down with the session.

K=/data/local/nhsystem/kali-arm64

sleep 20
[ -d "$K" ] || exit 0

mountpoint -q "$K/dev"   || mount -o bind /dev "$K/dev" 2>/dev/null
mountpoint -q "$K/sdcard" || mount -o bind /storage/emulated/0 "$K/sdcard" 2>/dev/null

# keep doze off so Termux / sshd survive
dumpsys deviceidle disable >/dev/null 2>&1
