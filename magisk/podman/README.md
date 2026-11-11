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

## Networking — three modes that work

| Mode | Isolation | Internet | Use |
|---|---|---|---|
| `--network=slirp4netns` | own netns + tap0 | ✅ (userspace NAT) | **isolated, zero setup** |
| `--network=host` | shares phone net | ✅ | ports, raw sockets, tools |
| `--network=<bridge>` + `skybridge` | own netns + bridge | ✅ (via netd) | real bridge, multi-container |
| `--network=pasta` | — | ❌ rootless-only | n/a (we run rootful) |

**slirp4netns** is the zero-setup default: each container gets its own `tap0`
(`10.0.2.x`), outbound traffic proxied through a userspace TCP/IP stack over
ordinary uid-0 sockets — which Android *does* permit. No kernel forwarding, no
iptables, no netd interaction. Verified stable (3/3 internet) with working
**port forwarding** (`-p 127.0.0.1:8099:80`).

**Bridge networking also works** — but only after asking Android's `netd` to
authorise it (see [`skybridge`](#real-bridge-networking-via-netd-skybridge)).

### Real bridge networking via netd (`skybridge`)

A real netavark bridge *can* reach the internet, once you get past four walls
(first three fixed in this repo, the fourth handled by `skybridge`):

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

4. **Android policy routing + FORWARD drops** — `netd` installs `ip rule`s that
   force all uid-0 traffic into the uplink's routing table (`table 1030` for
   wlan0), so packets destined to the container subnet get sent out Wi-Fi
   instead of across the bridge (`ip route get 10.201.0.5` →
   `via 192.168.88.1 dev wlan0 table 1030`). And routed container→internet
   traffic must cross `netd`'s **FORWARD** filter chain, which drops it — a chain
   we can't whitelist because the filter table is unwritable (wall 3).
   → **don't fight netd, co-opt it.** `netd` *can* write that table, and it
   already knows how to NAT-forward a downstream interface — that is what
   tethering is. `skybridge` asks netd (via its `ndc` client) to treat the
   podman bridge as a tethered interface: `ipfwd enable` + `ipfwd add` both ways
   (netd installs the FORWARD ACCEPTs) + `nat enable` (netd installs the
   MASQUERADE). We add one `ip rule` (`to <subnet> lookup main`) so replies cross
   the bridge instead of following netd's per-uid rules back out the uplink;
   egress rides Android's own routing + the NAT netd just opened. Verified:
   container on `10.201.0.5` → `ping 1.1.1.1` 0 % loss, `https://api.ipify.org`
   returns the phone's public IP.

Use it (on the **Android** side, as root, after a container brings the bridge
up — `ndc` is bionic and absent inside the chroot):

```sh
kali-native podman network create --subnet 10.201.0.0/24 skynet
kali-native podman run -d --name web --dns 1.1.1.1 --network=skynet nginx
skybridge up skynet       # authorise through netd -> internet over the bridge
skybridge status skynet
skybridge down skynet     # tears the netd rules + routing back down
```

Caveats: run `skybridge up` *after* the bridge iface exists (a container is
running on the network); give containers `--dns 1.1.1.1` (podman's aardvark-dns
answers on the host-local gateway, still blocked by netd's INPUT chain); and if
the phone switches network (Wi-Fi⇄data, screen-off teardown) netd may drop the
rules — just re-run `skybridge up`. For zero-setup isolated networking that
never touches netd, prefer **slirp4netns**.

## Install

```sh
adb push magisk/podman/setup-podman.sh magisk/podman/nlshim.c /data/local/tmp/
adb shell "su -c 'K=/data/local/nhsystem/kali-arm64; cp /data/local/tmp/setup-podman.sh /data/local/tmp/nlshim.c \$K/root/; nh bash /root/setup-podman.sh'"
# skybridge ships in the Magisk module (system/bin); redeploy it with:
adb push magisk/skyla-tools/system/bin/skybridge /data/local/tmp/
adb shell "su -c 'cp /data/local/tmp/skybridge /data/adb/modules/skyla-tools/system/bin/ && chmod 755 /data/adb/modules/skyla-tools/system/bin/skybridge'"
```

## Status

| Piece | State |
|---|---|
| Container exec (crun, overlay, cgroup v2, mqueue, pivot_root) | ✅ stable (jemalloc) |
| Image pull / run | ✅ |
| Isolated networking (`--network=slirp4netns`) | ✅ internet + DNS + port-fwd |
| Host networking (`--network=host`) | ✅ |
| Bridge networking (netavark + `skybridge`) | ✅ internet via netd co-op |
| Rootless | not set up (runs as root in the chroot) |
