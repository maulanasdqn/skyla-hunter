# Reforge

A modern, AI-assisted front-end for Kali NetHunter.

Reforge re-skins the NetHunter workflow into a Jetpack Compose / Material 3 app
and adds an AI recon agent that can drive both the Kali chroot and the app
itself. It runs as a companion to an existing NetHunter install — same Magisk
root, same Kali chroot — through a thin `su -> chroot` bridge. All pure,
deterministic logic lives in a Rust/UniFFI native core.

Note: for authorized security testing only. Use Reforge against assets you own
or are explicitly permitted to test. See [Security & ethics](#security--ethics).

## Features

- **AI Recon Agent** — a streaming function-calling loop (DeepSeek or any
  OpenAI-compatible endpoint) that runs recon tools in the chroot, reads the
  output and drives the UI: Markdown + tables, live output streaming, per-session
  history, token-budgeted context, run summaries, downloadable reports.
- **Terminal** — a real shell into the chroot: live streaming, structured
  command/output, ANSI stripped.
- **Tool runners** — one-tap launchers for Recon, Web, OSINT-People (holehe,
  maigret, sherlock, phoneinfoga, exiftool), OSINT-Domain (theHarvester,
  subfinder, dnsrecon, whois, dig) and Exploits (searchsploit), with a live
  command preview.
- **Payloads** — an `msfvenom` generator (Android/Linux/Windows/PHP/Python/Bash,
  LHOST/LPORT) that writes into the chroot.
- **Brute-force** — a `hydra` form (ssh/ftp/mysql/postgres/telnet/rdp/smb/vnc).
- **DuckHunter (HID)** — a Ducky Script compiler injecting USB-HID keystrokes via
  `/dev/hidg0`.
- **MAC Changer** — spoof/randomize/restore, verified against the real kernel MAC.
- **Chroot Manager**, **Kali Services** (ssh/apache/postgresql/mariadb) and a
  **Dashboard** (live status, service state, recent agent sessions).
- **Containers** — `podman` runs real OCI containers in the chroot
  (see [magisk/podman](magisk/podman/README.md)).

## Requirements

- Android with **Magisk root** and **Kali NetHunter**, chroot at
  `/data/local/nhsystem/kali-arm64` (the standard NetHunter path).
- Reforge **granted superuser** in Magisk.
- For the AI agent: an API key for DeepSeek or any OpenAI-compatible endpoint.
- Developed on a Redmi Note 10 Pro (`sweet`, arm64) with a NetHunter kernel.

Reforge does not bundle Kali — it drives the NetHunter install already set up.

## Build & install

Wrapperless Gradle layout; needs Gradle 8.14+ and JDK 17. A Gradle preBuild hook
runs `rust/build.sh`, which builds the Rust core, regenerates the Kotlin UniFFI
bindings and cross-compiles the arm64 `.so` (requires `cargo`, `cargo-ndk` and an
Android NDK).

```bash
export JAVA_HOME=/path/to/jdk-17
gradle :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Grant Reforge superuser in Magisk when prompted.

Stack: Kotlin 2.0.21 · AGP 8.7.3 · Gradle 8.14.4 · Compose BOM 2024.12.01 ·
`compileSdk/targetSdk 35` · `minSdk 26` · `applicationId dev.reforge` (`.debug`
suffix for debug builds) · abi `arm64-v8a` · Rust core via UniFFI 0.29.

## Configuring the AI agent

In **Settings**:

| Field | Purpose | Default |
|---|---|---|
| API key | Bearer token, stored on-device only | — |
| Base URL | OpenAI-compatible endpoint; `/chat/completions` is appended | `https://api.deepseek.com` |
| Model | model id | `deepseek-chat` |
| Custom headers | one `Key: Value` per line | — |
| Extra body JSON | merged into the request body | — |

Any OpenAI-compatible provider works (DeepSeek, OpenRouter, a self-hosted
gateway, a local model). The key and your prompts stay in `SharedPreferences` and
are sent only to the endpoint you configure.

## Architecture

- **`core/RootShell.kt`** — the bridge: `root()`, `chroot()` and `chrootStream()`
  (a PTY via `script -qec`, base64-encoded command, throttled live chunks, ANSI
  stripped, cancellable). Routes through the `kali-native` launcher when present.
- **`core/ReconAgent.kt`** — the agent loop. Two tools: `exec_chroot` (streaming
  recon) and `app_action` (navigate / toast / get_status). A denylist guards
  destructive commands with an inline Skip / Run-anyway confirmation.
- **`core/AppController.kt`** — the agent's hand into the live UI.
- **`rust/` (`skyla_core`)** — the Rust/UniFFI native core holding all pure logic:
  Ducky compiler, IR code generators, Markdown parser, airodump CSV parser,
  msfvenom/hydra builders, recon denylist + tool detection + header parsing,
  report builder, context windowing, MAC/interface parsing, `KEY=VALUE` parsing.
  Built and bound by `rust/build.sh`.
- **`ui/screens/*`** — one screen per feature; `RunnerViewModel` and the terminal
  palette are shared by the runners, payload and hydra screens.

## Native kernel & containers

The device runs a rebuilt NetHunter kernel (`sm6150`, 4.14, pre-GKI) with
`USER_NS`, `PID_NS`, `IPC_NS` and cgroup/container options enabled. Kali binaries
run natively (arm64 on the kernel, no qemu/proot).

- **Namespaces** — `kali-native` enters the chroot in a private mount+pid+uts
  namespace; mount isolation works via `busybox mount --make-rprivate /`, so the
  per-session `/proc`, `/sys`, `/dev/pts`, `/sdcard` tear down on exit.
  `kali-native --isolated` reports `pid1` with ~5 procs vs the host's hundreds.
- **Containers** — `bwrap` and `podman` run in the chroot (pivot_root entry, a
  netlink shim and a cgroup2 mount). `podman` runs real OCI containers with
  `slirp4netns` (isolated) or host networking; real bridge networking works by
  authorising the bridge through Android `netd` with `skybridge`.
- Details: [docs/KERNEL-NATIVE.md](docs/KERNEL-NATIVE.md) (kernel rebuild and
  measurements), [magisk/bwrap](magisk/bwrap/README.md),
  [magisk/podman](magisk/podman/README.md), [kernel](kernel/README.md).

## Security & ethics

Reforge is a tool for **authorized** penetration testing and security research.

- Target only systems you own or have **explicit written permission** to test.
- The agent refuses to profile a specific private individual; the denylist and
  inline confirmation stay active regardless of which model/endpoint you use.
- Keystroke injection types into whatever host the phone is plugged into — know
  the host.
- Your API key lives on-device; never commit it. `local.properties` and secrets
  are git-ignored.

You are responsible for how you use this software and for complying with all
applicable laws.

## Status

Covers the core NetHunter surface (terminal, chroot, services, MAC, HID, KeX,
wireless monitor/scan) plus the tool runners, payload generator, brute-force form,
the AI agent and working containers. Not yet in-app: Wifite-class wireless
automation, Bluetooth Arsenal, Pineapple/USB Arsenal. Device caveat (`sweet`): the
internal Wi-Fi (QCA6391) has no monitor mode — wireless needs a USB OTG adapter
(AR9271 / RT3070-class).

## License

No license granted yet — all rights reserved by the author. Open an issue if you
want to use or contribute.
