# bubblewrap (bwrap) on NetHunter — how it was made to work

`bwrap` now runs inside a `kali-native` session. Two independent fixes were
needed; either alone is not enough.

## The two walls (both proven on-device)

1. **Propagation.** The Kali rootfs is a directory subtree on the f2fs `/data`
   partition, not a first-class mount. The kernel rejects *every* mount
   propagation change (`MS_SLAVE`/`MS_SHARED`/`MS_PRIVATE`) on that mount —
   only tmpfs accepts them. Stock bwrap aborts at `Failed to make / slave` and
   later at `Failed to make old root rprivate`.

2. **pivot_root.** bwrap requires `pivot_root`, which needs the current root to
   be a real mount. Inside a plain `chroot`, `/` is not a mount, so
   `pivot_root` returns `EINVAL` — confirmed even with a manual `busybox
   pivot_root`.

## The fixes

- **kali-native enters via `pivot_root`, not `chroot`**
  (`../skyla-tools/system/bin/kali-native`). It `--rbind`s the rootfs onto
  itself (carrying the proc/sys/dev submounts), then `pivot_root`s into it, so
  `/` becomes a first-class mount that bwrap's own `pivot_root` can use. Falls
  back to `chroot` if `pivot_root` fails, so normal tool-runs never regress.

- **Patched bwrap** ([`build-bwrap.sh`](build-bwrap.sh)) makes just the two
  propagation calls tolerate `EINVAL` instead of aborting. Safe because
  kali-native already wraps bwrap in a private mount namespace, so nothing can
  leak to the real host. Only `EINVAL` is tolerated; every other error still
  aborts. The rest of bwrap is stock 0.13.1.

## Build / install

```sh
# inside the Kali chroot (nh bash /root/build-bwrap.sh), or:
adb push magisk/bwrap/build-bwrap.sh /data/local/tmp/
adb shell "su -c 'K=/data/local/nhsystem/kali-arm64; cp /data/local/tmp/build-bwrap.sh \$K/root/; nh bash /root/build-bwrap.sh'"
```

## Use

```sh
# bwrap only works inside a kali-native session (needs the pivot_root root):
kali-native bwrap --ro-bind /usr /usr --ro-bind /bin /bin --ro-bind /lib /lib \
  --ro-bind /etc /etc --proc /proc --dev /dev --tmpfs /tmp \
  --unshare-pid --unshare-uts --hostname sandbox /bin/bash
```

Verified: `uid=0`, `hostname=sandbox` (bwrap UTS ns), `pid1=2` with its own
isolated `/proc` (bwrap PID ns), no leak to the host.

## podman

Not done. podman needs a container runtime (crun/runc) + cgroup delegation on
top of this. With bwrap/pivot_root working the groundwork exists, but cgroup v2
support on this 4.14 kernel is unverified — a separate effort.
