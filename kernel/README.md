# Kernel config — container support

> **Status: BUILT & FLASHED (11 Nov 2026).** The 4.14 kernel was rebuilt with
> these options and flashed. `podman` now runs real containers (see
> [`../magisk/podman/`](../magisk/podman/)); `bwrap` works too
> ([`../magisk/bwrap/`](../magisk/bwrap/)). `kali-ok` still passes 12/12 — no
> regression.

Config changes to enable container tooling (`podman`/`crun`/`bwrap`) on the
existing **4.14** kernel. This is **not** a version bump: the `sweet`/`sm6150`
kernel is locked to the Qualcomm 4.14 BSP and is pre-GKI, so 5.x is not an
option. These only switch on options in the same 4.14 build, the same way
`USER_NS`/`PID_NS` were added (see [`../KERNEL-NATIVE.md`](../KERNEL-NATIVE.md)).

## The options (added over the namespace kernel)

| Option | For |
|---|---|
| `CONFIG_CGROUP_PIDS` | `pids` controller (v1+v2) — appears in `cgroup.controllers` |
| `CONFIG_CGROUP_DEVICE` | devices controller (cgroup v1/hybrid) |
| `CONFIG_POSIX_MQUEUE` | crun mounts `mqueue` in every container — hard blocker without it |
| `CONFIG_FHANDLE` | `open_by_handle_at`, moby "generally necessary" |
| `CONFIG_BRIDGE_NETFILTER` | bridged container networking through netfilter |
| `CONFIG_NETFILTER_XT_MATCH_ADDRTYPE` | MASQUERADE rules for container NAT |
| `CONFIG_VXLAN`, `CONFIG_MACVLAN` | extra network modes |

All are gated by options already `=y` (`CGROUPS`, `NETFILTER`, …), so nothing
exotic is pulled in. `POSIX_MQUEUE_SYSCTL` auto-enables with `POSIX_MQUEUE`.

- Files: [`nethunter.config`](nethunter.config) is the **complete** build
  fragment used (Wi-Fi + BT + namespaces + these container options);
  [`container-options.diff`](container-options.diff) is the unified diff of the
  running config vs the previous namespace-only kernel;
  `../kernel-config-container.txt` is the full running config.

## Apply + build + flash

The fragment is merged into the defconfig and the kernel built in a Debian
container (case-sensitive FS); the boot image is repacked and `dd`-flashed.
See `KERNEL-NATIVE.md §6` and `~/Downloads/nethunter-sweet/kbuild/build4.sh`
(the `VERIFY` guard aborts the build if any of these options don't stick):

```sh
# in the kernel build dir (~/Downloads/nethunter-sweet/kbuild):
docker run --rm -v "$PWD":/work debian:bookworm-slim bash /work/build4.sh
# then on the host: ./repack.sh out-kernel4/Image.gz boot-new.img
# back up current boot, then: dd if=boot-new.img of=/dev/block/by-name/boot bs=1M
```

## Verify on-device (this kernel exposes `/proc/config.gz`)

```sh
zcat /proc/config.gz | grep -E 'CGROUP_PIDS|CGROUP_DEVICE|POSIX_MQUEUE|FHANDLE|BRIDGE_NETFILTER|XT_MATCH_ADDRTYPE'
cat /sys/fs/cgroup/cgroup.controllers          # host: shows 'pids'
kali-native podman run --rm --network=host docker.io/library/alpine echo ok
```
