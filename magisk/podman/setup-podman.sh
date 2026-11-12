#!/bin/bash
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
echo "Real bridge networking also works, once authorised through Android netd:"
echo "  kali-native podman network create --subnet 10.201.0.0/24 skynet"
echo "  kali-native podman run -d --dns 1.1.1.1 --network=skynet <image>"
echo "  skybridge up skynet     # (Android side, as root; see README.md)"
