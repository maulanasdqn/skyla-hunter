# Skyla Tools — Magisk module source

Versioned source for the on-device Magisk module `skyla-tools` and its boot
service. This directory is the source of truth; the device copies are deployed
from here with [`deploy.sh`](deploy.sh).

## Layout

| Repo path | Device path |
|---|---|
| `skyla-tools/module.prop` | `/data/adb/modules/skyla-tools/module.prop` |
| `skyla-tools/system/bin/kali-native` | `/data/adb/modules/skyla-tools/system/bin/kali-native` |
| `skyla-tools/system/bin/nh` | `…/system/bin/nh` |
| `skyla-tools/system/bin/dox` | `…/system/bin/dox` |
| `skyla-tools/system/bin/kali-ok` | `…/system/bin/kali-ok` |
| `service.d/nh-boot.sh` | `/data/adb/service.d/nh-boot.sh` |

Magisk magic-mounts `system/bin/*` onto `/system/bin`, so the commands are on
root's `PATH` (`su -c kali-native …`).

## The commands

- **`kali-native`** — enters the Kali chroot inside a private **mount + pid +
  uts** namespace. The key fix (PATCH-PNODE-PLAN.md §4) is the mount namespace
  plus `busybox_nh mount --make-rprivate /`: the `/proc`, `/sys`, `/dev/pts`,
  `/sdcard` it mounts per session stay isolated and vanish on exit instead of
  leaking onto the host. User namespace is intentionally **not** nested — the
  launcher already runs as root, and nesting user-ns breaks the sdcardfs bind.
- **`nh`** — plain, always-mounted chroot launcher (no namespace).
- **`dox`** — OSINT wrapper (holehe / maigret / PhoneInfoga / theHarvester / …).
- **`kali-ok`** — health check; proves isolation by mounting a tmpfs inside a
  session and confirming the host never sees it.

The app (`dev.reforge.core.RootShell`) routes chroot commands through
`kali-native` when it is installed, and falls back to a raw chroot otherwise.

## Deploy

```sh
# needs: adb, a connected rooted device (Magisk su)
magisk/deploy.sh
```

It backs up each existing file, copies the repo version in, fixes mode/SELinux
context, then runs `kali-ok`. Script changes are live immediately (same inode as
the magic mount); a reboot is only needed if `module.prop` or a brand-new file
is added.
