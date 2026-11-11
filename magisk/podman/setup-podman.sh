#!/bin/bash
# setup-podman.sh — install & configure podman + crun in the NetHunter chroot.
#
# RUN THIS INSIDE THE KALI CHROOT (nh bash /root/setup-podman.sh). Idempotent.
#
# Requires the container kernel (see kernel/ — CGROUP_PIDS/DEVICE, POSIX_MQUEUE,
# FHANDLE, BRIDGE_NETFILTER, xt_addrtype) AND the pivot_root launcher: podman
# containers only run inside a `kali-native` session (first-class root mount +
# a cgroup2 mount, both provided by kali-native).
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive

echo "[podman] install packages"
apt-get update -qq
apt-get install -y --no-install-recommends \
  podman crun netavark aardvark-dns fuse-overlayfs slirp4netns uidmap \
  libjemalloc2 ca-certificates >/dev/null

echo "[podman] containers.conf (crun runtime)"
mkdir -p /etc/containers
cat > /etc/containers/containers.conf <<'CONF'
[engine]
runtime = "crun"
CONF

# podman wrapper: preload jemalloc. On this 4.14 kernel glibc's main-arena
# malloc hits a sysmalloc page-alignment assertion from go-sqlite3 during
# podman's DB init, crashing ~4 in 5 runs. jemalloc sidesteps glibc malloc
# entirely and makes podman 100% stable. The wrapper execs the real binary.
echo "[podman] jemalloc wrapper -> /usr/local/bin/podman"
JEM=$(ls /usr/lib/*/libjemalloc.so.2 2>/dev/null | head -1)
cat > /usr/local/bin/podman <<WRAP
#!/bin/sh
JEM="${JEM:-/usr/lib/aarch64-linux-gnu/libjemalloc.so.2}"
[ -e "\$JEM" ] && export LD_PRELOAD="\$JEM\${LD_PRELOAD:+:\$LD_PRELOAD}"
exec /usr/bin/podman "\$@"
WRAP
chmod 755 /usr/local/bin/podman

echo "[podman] versions"
/usr/bin/podman --version
crun --version | head -1
echo "[podman] done. Use inside kali-native:"
echo "  kali-native podman run --rm --network=host docker.io/library/alpine echo ok"
echo
echo "Networking: --network=host works (shares the phone's network). The default"
echo "bridge (netavark) currently fails with a netlink error on this kernel."
