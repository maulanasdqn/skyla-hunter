# podman on NetHunter — working setup

`podman` runs real OCI containers inside the Kali chroot on the phone. Verified:
an Alpine container runs as pid 1 in its own PID/UTS/mount namespace, with its
own overlay rootfs, and reaches the internet — both with a shared host network
**and** with an isolated per-container network.

```
# isolated per-container network (own stack, internet + DNS, NO host net):
$ kali-native podman run --rm --network=slirp4netns docker.io/library/alpine \
    sh -c 'ip -4 addr show tap0 | grep inet; wget -qO- https://api.ipify.org'
    inet 10.0.2.100/24 brd 10.0.2.255 scope global tap0
114.12.10.213

# or share the phone's network:
$ kali-native podman run --rm --network=host docker.io/library/alpine \
    wget -qO- https://api.ipify.org
114.12.10.213
```

## What it took

Three layers had to line up for container *exec*:

1. **Kernel** (see [`../../kernel/`](../../kernel/)) — rebuilt 4.14 with the
   container controllers/options podman needs: `CGROUP_PIDS`, `CGROUP_DEVICE`,
   `POSIX_MQUEUE` (crun mounts mqueue in every container), `FHANDLE`.

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

## Networking — use slirp4netns or host, NOT the bridge

| Mode | Isolation | Internet | Use |
|---|---|---|---|
| `--network=slirp4netns` | own netns + tap0 | ✅ (userspace NAT) | **isolated containers** |
| `--network=host` | shares phone net | ✅ | ports, raw sockets, tools |
| `--network=<bridge>` (default/netavark) | own netns | ❌ **blocked by netd** | don't |
| `--network=pasta` | — | ❌ rootless-only | n/a (we run rootful) |

**slirp4netns** is the drop-in replacement for bridge networking here: each
container gets its own `tap0` (`10.0.2.x`), and outbound traffic is proxied
through a userspace TCP/IP stack over ordinary uid-0 sockets — which Android
*does* permit. No kernel forwarding, no iptables, no netd interaction. Verified
stable (3/3 internet) with working **port forwarding** (`-p 127.0.0.1:8099:80`).

### Why the netavark bridge can't work on this phone

Chasing the default bridge all the way down hit **four independent walls**, each
rooted in the stock Android kernel + `netd` firewall. The fixes for the first
three are in this repo; the fourth is unfixable from userspace:

1. **netlink socket option** — netavark sets `NETLINK_GET_STRICT_CHK` (socket
   option 12, Linux ≥ 5.2). On 4.14 `setsockopt` returns `ENOPROTOOPT` and
   netavark aborts (`host netlink socket: ... os error 92`).
   → fixed by [`nlshim.c`](nlshim.c), an `LD_PRELOAD` shim that swallows that one
   error (the option only tightens dump validation).

2. **nftables has no kernel backend** — `nft list ruleset` →
   `cache initialization failed: Invalid argument`; there is no `nf_tables`.
   → switched netavark to the `iptables` firewall driver.

3. **filter / mangle / raw tables are unwritable** — any write (even a bare
   `iptables -t filter -N CHAIN`) fails with *"No chain/target/match by that
   name."* Reads work; writes don't. Cause: the whole-table `REPLACE` carries
   Android's vendor rules (`-m quota2 -m bpf -m u32`, `bw_`/`oem_`/`fw_`/
   `nm_mdmprxy_`/`tetherctrl_` chains) and the 4.14 kernel rejects the
   re-serialised blob (userspace iptables 1.8.13 ≠ kernel module revisions).
   The `nat` table happens to be writable, but `filter`/`mangle`/`raw` are not.
   In a *fresh* netns the tables are empty and everything works — so it is the
   host table content, not a missing feature.
   → set `firewall_driver = "none"` so netavark skips the filter chains it
   cannot create; the one NAT rule it needs can be added by hand in `nat`.

4. **Android policy routing + FORWARD drops** (the unfixable wall) — `netd`
   installs `ip rule`s that force all uid-0 traffic into the wlan0 routing table
   (`table 1030`), so packets destined to the container subnet get sent out
   Wi-Fi instead of across the bridge (`ip route get 10.89.1.2` →
   `via 192.168.88.1 dev wlan0 table 1030`). High-priority rules
   (`ip rule add to/from 10.89.1.0/24 lookup main pref 9000`) fix the routing,
   but routed container→internet traffic must then cross `netd`'s **FORWARD**
   filter chain (`bw_FORWARD`/`oem_fwd`/`tetherctrl_FORWARD`), which drops it —
   and we can't whitelist the bridge there because the **filter table is
   unwritable** (wall 3). Dead end.

The takeaway: on a locked-down Android `netd`, kernel-forwarded bridge
networking is not reachable from the chroot. **Userspace networking
(slirp4netns) is the correct design** — it never touches netd, so none of the
four walls apply.

## Install

```sh
adb push magisk/podman/setup-podman.sh magisk/podman/nlshim.c /data/local/tmp/
adb shell "su -c 'K=/data/local/nhsystem/kali-arm64; cp /data/local/tmp/setup-podman.sh /data/local/tmp/nlshim.c \$K/root/; nh bash /root/setup-podman.sh'"
```

## Status

| Piece | State |
|---|---|
| Container exec (crun, overlay, cgroup v2, mqueue, pivot_root) | ✅ stable (jemalloc) |
| Image pull / run | ✅ |
| Isolated networking (`--network=slirp4netns`) | ✅ internet + DNS + port-fwd |
| Host networking (`--network=host`) | ✅ |
| Bridge networking (netavark) | ❌ blocked by Android netd (4 walls above) |
| Rootless | not set up (runs as root in the chroot) |
