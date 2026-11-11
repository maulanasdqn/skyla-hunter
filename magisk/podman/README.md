# podman on NetHunter — working setup

`podman` runs real OCI containers inside the Kali chroot on the phone. Verified:
an Alpine container runs as pid 1 in its own PID/UTS/mount namespace, with its
own overlay rootfs, and reaches the internet via host networking.

```
$ kali-native podman run --rm --network=host docker.io/library/alpine \
    sh -c 'cat /etc/os-release | grep PRETTY; wget -qO- https://api.ipify.org'
PRETTY_NAME="Alpine Linux v3.24"
114.12.10.213
```

## What it took

Three layers had to line up:

1. **Kernel** (see [`../../kernel/`](../../kernel/)) — rebuilt 4.14 with the
   container controllers/options podman needs: `CGROUP_PIDS`, `CGROUP_DEVICE`,
   `POSIX_MQUEUE` (crun mounts mqueue in every container), `FHANDLE`,
   `BRIDGE_NETFILTER`, `NETFILTER_XT_MATCH_ADDRTYPE`.

2. **Launcher** — podman only runs inside a `kali-native` session, which:
   - enters via `pivot_root` so `/` is a first-class mount (crun/pivot needs it);
   - mounts a fresh **cgroup2** hierarchy at `/sys/fs/cgroup` (the chroot's fresh
     sysfs doesn't carry the host's cgroup mounts), exposing the `memory`+`pids`
     controllers. This makes podman use **cgroup v2**.

3. **Userspace** ([`setup-podman.sh`](setup-podman.sh)) — podman + crun +
   jemalloc wrapper. crun (not runc) is the runtime.

## The jemalloc fix (important)

On this kernel, glibc's main-arena malloc hits a hard `sysmalloc` page-alignment
assertion from `go-sqlite3` during podman's DB init:

```
Fatal glibc error: malloc.c:2371 (sysmalloc): assertion failed ...
```

It crashes ~4 of 5 runs. Preloading **jemalloc** sidesteps glibc malloc
entirely and makes podman 100 % stable (measured 5/5). `setup-podman.sh`
installs a `/usr/local/bin/podman` wrapper that sets
`LD_PRELOAD=.../libjemalloc.so.2` and execs the real binary, so plain `podman`
is stable with no per-command flags.

## Install

```sh
adb push magisk/podman/setup-podman.sh /data/local/tmp/
adb shell "su -c 'K=/data/local/nhsystem/kali-arm64; cp /data/local/tmp/setup-podman.sh \$K/root/; nh bash /root/setup-podman.sh'"
```

## Networking

- `--network=host` **works** — the container shares the phone's network
  (internet, DNS, tools that bind ports). Use this.
- The default **bridge** (netavark) currently fails:
  `netavark: host netlink socket: Protocol not available (os error 92)`.
  The kernel has bridge/veth/NAT/addrtype, so this is a userspace netavark/netns
  issue on this kernel, not a missing kernel option — unresolved, networking via
  host is the workaround.

## Status

| Piece | State |
|---|---|
| Container exec (crun, overlay, cgroup v2, mqueue, pivot_root) | ✅ stable (jemalloc) |
| Image pull / run | ✅ |
| Host networking | ✅ |
| Bridge networking (netavark) | ❌ netlink error (use `--network=host`) |
| Rootless | not set up (runs as root in the chroot) |
