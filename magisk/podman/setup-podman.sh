#!/bin/bash
# setup-podman.sh — install & configure podman + crun in the NetHunter chroot.
#
# RUN THIS INSIDE THE KALI CHROOT (nh bash /root/setup-podman.sh). Idempotent.
#
# Requires the container kernel (see kernel/ — CGROUP_PIDS/DEVICE, POSIX_MQUEUE,
# FHANDLE) AND the pivot_root launcher: podman containers only run inside a
# `kali-native` session (first-class root mount + a cgroup2 mount, both provided
# by kali-native).
#
# NETWORKING on this locked-down Android: the netavark bridge driver does NOT
# work (Android netd blocks it on four independent fronts — see README.md), so
# this script sets up the two backends that DO work:
#   * --network=slirp4netns : isolated per-container net with internet + DNS
#   * --network=host        : container shares the phone's network
# and installs nlshim.so (lets netavark at least initialise, for experiments).
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive
HERE=$(cd "$(dirname "$0")" && pwd)

echo "[podman] install packages"
apt-get update -qq
apt-get install -y --no-install-recommends \
  podman crun netavark aardvark-dns fuse-overlayfs slirp4netns uidmap \
  libjemalloc2 ca-certificates gcc libc6-dev >/dev/null

echo "[podman] containers.conf (crun runtime + graceful bridge)"
mkdir -p /etc/containers
cat > /etc/containers/containers.conf <<'CONF'
[engine]
runtime = "crun"

# netavark's nftables backend has no kernel here, and its iptables backend can't
# write the Android-polluted filter/mangle tables; "none" lets the bridge driver
# at least create interfaces without erroring out. Bridge *egress* is still
# blocked by Android netd (see README.md) — use --network=slirp4netns instead.
[network]
firewall_driver = "none"
CONF

# nlshim.so: netavark unconditionally sets NETLINK_GET_STRICT_CHK (kernel >=5.2);
# on 4.14 that returns ENOPROTOOPT and netavark aborts. The shim swallows that one
# error. Not needed for slirp4netns/host, but lets netavark run for experiments.
echo "[podman] build nlshim.so -> /opt/skyla"
mkdir -p /opt/skyla
cc -shared -fPIC -O2 -o /opt/skyla/nlshim.so "$HERE/nlshim.c" -ldl
NV=/usr/lib/podman/netavark
if [ -x "$NV" ] && [ ! -e "$NV.real" ]; then
  cp "$NV" "$NV.real"
  cat > "$NV" <<WRAP
#!/bin/sh
export LD_PRELOAD="/opt/skyla/nlshim.so\${LD_PRELOAD:+:\$LD_PRELOAD}"
exec "$NV.real" "\$@"
WRAP
  chmod 755 "$NV"
fi

# podman wrapper: preload jemalloc. On this 4.14 kernel glibc's main-arena malloc
# hits a sysmalloc page-alignment assertion from go-sqlite3 during podman's DB
# init, crashing ~4 in 5 runs. jemalloc sidesteps glibc malloc entirely and makes
# podman 100% stable. The wrapper execs the real binary.
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
echo "  # isolated per-container network (internet + DNS, no host net):"
echo "  kali-native podman run --rm --network=slirp4netns docker.io/library/alpine \\"
echo "    wget -qO- https://api.ipify.org"
echo "  # or share the phone's network:"
echo "  kali-native podman run --rm --network=host docker.io/library/alpine echo ok"
echo
echo "The default bridge (netavark) is NOT functional on this Android kernel;"
echo "see README.md for the full diagnosis. Use slirp4netns or host."
