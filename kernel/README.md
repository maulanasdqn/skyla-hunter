# Kernel config — container cgroup controllers

> **Status: BUILT & FLASHED (11 Nov 2026).** The 4.14 kernel was rebuilt with
> these two options and flashed to the device. Verified on the running kernel:
> `zcat /proc/config.gz` shows `CONFIG_CGROUP_PIDS=y` and
> `CONFIG_CGROUP_DEVICE=y`; `/sys/fs/cgroup/cgroup.controllers` now lists
> `pids` (was `memory` only); `/proc/cgroups` shows `pids` and `devices`
> enabled. `kali-ok` still passes 12/12 and bwrap still runs — no regression.
> The built config matches `../kernel-config-container.txt` exactly.

Config change to enable `podman`/`runc`/`crun` on the existing **4.14** kernel.
This is **not** a version bump: the `sweet`/`sm6150` kernel is locked to the
Qualcomm 4.14 BSP and is pre-GKI, so 5.x is not an option. This only switches on
two controllers in the same 4.14 build, the same way `USER_NS`/`PID_NS` were
added (see [`../KERNEL-NATIVE.md`](../KERNEL-NATIVE.md)).

## The change

| Option | Was | Now | Provides |
|---|---|---|---|
| `CONFIG_CGROUP_PIDS` | not set | `y` | `pids` controller (v1 **and** v2) — process-count limits |
| `CONFIG_CGROUP_DEVICE` | not set | `y` | `devices` controller (cgroup **v1**/hybrid) |

Both are plain bools gated only by `CONFIG_CGROUPS` (already `=y`), so no extra
dependencies are pulled in. Already present and sufficient for the rest:
`MEMCG`, `BLK_CGROUP`, `CGROUP_SCHED`, `CPUSETS`, `CGROUP_FREEZER`,
`CGROUP_BPF`, `OVERLAY_FS`, `SECCOMP`, `USER_NS`, `PID_NS`.

- Files: [`nethunter-cgroup.config`](nethunter-cgroup.config) (the fragment to
  build with), [`cgroup-pids-device.diff`](cgroup-pids-device.diff) (unified
  diff against the running config), and `../kernel-config-container.txt` (the
  full resulting config, for reference).

### cgroup v2 note

The device runs a hybrid cgroup setup (`/sys/fs/cgroup` is `cgroup2`, plus v1
`/dev/cpuctl` etc.). In **pure v2** there is no `devices` controller file —
device access is enforced through eBPF (`CONFIG_CGROUP_BPF`, already `=y`), so
`CGROUP_DEVICE` matters for runc/crun running in **v1/hybrid** mode. `CGROUP_PIDS`
is the one that will newly appear in `/sys/fs/cgroup/cgroup.controllers`.

## Apply + build

Append the fragment to the kernel tree's NetHunter config fragment, then rebuild
as in KERNEL-NATIVE.md §6:

```sh
cat kernel/nethunter-cgroup.config \
  >> android_kernel_xiaomi_sm6150/arch/arm64/configs/vendor/nethunter.config

# same build line as before:
# make vendor/sdmsteppe-perf_defconfig vendor/sweet.config vendor/nethunter.config
# (then build Image.gz + modules, repack boot-ns.img, flash)
```

## Verify the options actually landed

Config-level (before flashing — the build must show them `=y`):

```sh
grep -E 'CONFIG_CGROUP_PIDS|CONFIG_CGROUP_DEVICE' \
  kbuild/out-kernel4/kernel.config        # both must be =y, not "is not set"
```

On-device after flashing (this kernel exposes `/proc/config.gz`):

```sh
zcat /proc/config.gz | grep -E 'CGROUP_PIDS|CGROUP_DEVICE'   # expect =y
cat /sys/fs/cgroup/cgroup.controllers                        # expect 'pids' to appear
```

Runtime smoke test (pids controller usable):

```sh
kali-native sh -c 'mkdir -p /sys/fs/cgroup/t && echo +pids > /sys/fs/cgroup/cgroup.subtree_control && echo ok'
```

## Why this is needed

`bwrap` already works without any rebuild (see [`../magisk/bwrap/`](../magisk/bwrap/)).
`podman` additionally needs an OCI runtime (crun/runc) that sets up cgroups for
the container; the `pids` (and, in v1/hybrid, `devices`) controllers are the
missing kernel pieces. After this rebuild the kernel side is in place; the
runtime side (crun + netavark/pasta config) is userspace and separate.
