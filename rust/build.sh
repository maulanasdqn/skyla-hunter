#!/usr/bin/env bash
# Build the Rust core and wire its outputs into the Android app:
#   1. build a host cdylib and generate Kotlin UniFFI bindings from it
#   2. cross-compile the arm64-v8a .so into app/src/main/jniLibs
#
# Idempotent; safe to run from Gradle (preBuild depends on it) or by hand.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
APP_JAVA="$HERE/../app/src/main/java"
JNILIBS="$HERE/../app/src/main/jniLibs"

if ! command -v cargo >/dev/null 2>&1; then
  echo "[skyla-core] cargo not found on PATH — install Rust (https://rustup.rs) to build the native core" >&2
  exit 1
fi

# Locate the Android NDK (cargo-ndk needs ANDROID_NDK_HOME).
: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
if [ -z "${ANDROID_NDK_HOME:-}" ]; then
  ANDROID_NDK_HOME="$(ls -d "$ANDROID_HOME"/ndk/* 2>/dev/null | sort -V | tail -1 || true)"
  export ANDROID_NDK_HOME
fi
if [ -z "${ANDROID_NDK_HOME:-}" ] || [ ! -d "$ANDROID_NDK_HOME" ]; then
  echo "[skyla-core] Android NDK not found (set ANDROID_NDK_HOME or install an NDK under \$ANDROID_HOME/ndk)" >&2
  exit 1
fi
echo "[skyla-core] NDK: $ANDROID_NDK_HOME"

cd "$HERE"

echo "[skyla-core] (1/3) host build for bindgen"
cargo build --release

LIB=""
for cand in \
  "target/release/libskyla_core.dylib" \
  "target/release/libskyla_core.so"; do
  [ -f "$cand" ] && LIB="$cand" && break
done
[ -n "$LIB" ] || { echo "[skyla-core] host cdylib not found" >&2; exit 1; }

echo "[skyla-core] (2/3) generating Kotlin bindings -> $APP_JAVA/uniffi/skyla_core"
rm -rf "$APP_JAVA/uniffi/skyla_core"
cargo run --quiet --bin uniffi-bindgen -- \
  generate --library "$LIB" --language kotlin --out-dir "$APP_JAVA"

echo "[skyla-core] (3/3) building arm64-v8a .so -> $JNILIBS/arm64-v8a"
cargo ndk -t arm64-v8a -o "$JNILIBS" build --release

echo "[skyla-core] done:"
ls -la "$JNILIBS/arm64-v8a" "$APP_JAVA/uniffi/skyla_core"
