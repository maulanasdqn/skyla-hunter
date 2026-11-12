# Reforge

**A modern, AI-assisted front-end for Kali NetHunter.**

Reforge re-skins the whole NetHunter workflow into a clean Jetpack Compose /
Material 3 app and adds something NetHunter has never had: an **AI recon agent**
that can drive both the Kali chroot *and* the app itself. It runs as a companion
to an existing NetHunter install — same Magisk root, same Kali chroot — and talks
to them through a thin `su → chroot` bridge.

> ⚠️ **For authorized security testing only.** Use Reforge against assets you own
> or are explicitly permitted to test (your own lab, infra, domains). See
> [Security & ethics](#security--ethics).

---

## Highlights

- 🤖 **AI Recon Agent** — a streaming function-calling loop (DeepSeek or any
  OpenAI-compatible endpoint) that runs recon tools in the chroot, reads the
  output, and drives the UI. Markdown + tables, live tool-output streaming,
  per-session history, token-budgeted context, run summaries and downloadable
  reports.
- 🖥️ **Terminal** — a real shell into the Kali chroot with live streaming,
  structured command/output, and ANSI stripped for clean rendering.
- 🧰 **Tool runners** — curated, one-tap launchers for Recon, Web, **OSINT-People**
  (holehe, maigret, sherlock, phoneinfoga, exiftool), **OSINT-Domain**
  (theHarvester, subfinder, dnsrecon, whois, dig) and Exploits (searchsploit),
  with a live command preview.
- 💣 **Payloads** — an `msfvenom` generator (Android/Linux/Windows/PHP/Python/
  Bash presets, LHOST/LPORT) that writes straight into the chroot.
- 🔑 **Brute-force** — a `hydra` form for ssh/ftp/mysql/postgres/telnet/rdp/smb/vnc.
- ⌨️ **DuckHunter (HID)** — a Ducky Script compiler that injects USB-HID
  keystrokes via `/dev/hidg0`.
- 🔀 **MAC Changer** — spoof/randomize/restore an interface MAC, verified
  against the real kernel MAC.
- 📦 **Chroot Manager** — OS / kernel / disk / memory / uptime / process count,
  plus on-demand chroot size.
- ⚙️ **Kali Services** — start/stop ssh, apache, postgresql, mariadb.
- 🏠 **Dashboard** — live status (root, chroot, system, device IP), service
  dots and recent agent sessions.

---

## Requirements

- An Android device with **Magisk root** and **Kali NetHunter** installed, with
  the chroot at `/data/local/nhsystem/kali-arm64` (the standard NetHunter path).
- Reforge must be **granted superuser** in Magisk.
- For the AI agent: an API key for DeepSeek or any OpenAI-compatible endpoint.
- Tested on a Redmi Note 10 Pro (arm64) with a NetHunter kernel.

Reforge does **not** bundle Kali or a chroot — it drives the one NetHunter
already set up.

---

## Build & install

The project uses the Gradle wrapperless layout; any Gradle 8.14+ works.

```bash
# JDK 17 required
export JAVA_HOME=/path/to/jdk-17

gradle -p . :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then open Magisk and grant Reforge superuser when prompted.

**Stack:** Kotlin 2.0.21 · AGP 8.7.3 · Gradle 8.14.4 · Compose BOM 2024.12.01 ·
`compileSdk/targetSdk 35` · `minSdk 26` · `applicationId dev.reforge`
(`.debug` suffix for debug builds) · abiFilter `arm64-v8a`.

---

## Configuring the AI agent

In **Settings**:

| Field | Purpose | Default |
|---|---|---|
| **API key** | Bearer token, stored on-device only | — |
| **Base URL** | OpenAI-compatible endpoint; `/chat/completions` is appended | `https://api.deepseek.com` |
| **Model** | model id | `deepseek-chat` |
| **Custom headers** | one `Key: Value` per line (provider-specific) | — |
| **Extra body JSON** | merged into the request body (provider-specific) | — |

Any OpenAI-compatible provider works (DeepSeek, OpenRouter, a self-hosted
gateway, a local model, …). The key and your prompts are kept in
`SharedPreferences` and sent only to the endpoint you configure.

---

## How it works

```
┌──────────────┐   su -c "chroot … bash -c '<cmd>'"   ┌──────────────────┐
│  Compose UI  │ ───────────────────────────────────▶ │  Kali chroot     │
│  (dev.reforge)│                                      │  /data/local/…    │
└──────┬───────┘                                       └──────────────────┘
       │ AgentEvent stream (SSE)
┌──────▼───────┐   OpenAI-compatible function calling  ┌──────────────────┐
│  ReconAgent  │ ◀──────────────────────────────────▶ │  LLM endpoint     │
└──────────────┘                                       └──────────────────┘
```

- **`core/RootShell.kt`** — the bridge. `root()`, `chroot()`, and
  `chrootStream()` (a PTY via `script -qec`, base64-encoded command, throttled
  live chunks, ANSI stripped, cancellable with `cancelProc()`).
- **`core/ReconAgent.kt`** — the agent loop. Two tools: `exec_chroot` (runs
  recon commands, streaming) and `app_action` (navigate / toast / get_status
  via `AppController`). A **denylist** guards against destructive commands; when
  one trips, the UI shows an inline **Skip / Run anyway** confirmation that
  blocks the agent until you decide.
- **`core/AppController.kt`** — the agent's second hand into the live UI.
- **`core/ContextManager.kt`** — token-budget windowing of conversation history.
- **`ui/screens/*`** — one screen per feature; `RunnerViewModel` + the terminal
  palette are shared by the tool runners, payload and hydra screens.

---

## Security & ethics

Reforge is a tool for **authorized** penetration testing and security research.

- Only target systems you own or have **explicit written permission** to test.
- The agent will refuse to profile a specific private individual, and the app's
  denylist + inline confirmation are a second line of defense that stay active
  regardless of which model/endpoint you configure.
- Keystroke injection (DuckHunter) types into whatever host the phone is plugged
  into — know what that host is.
- Your API key lives on-device; never commit it. `local.properties` and secrets
  are git-ignored.

You are responsible for how you use this software and for complying with all
applicable laws.

---

## Status & roadmap

Reforge already covers the core NetHunter surface (terminal, chroot, services,
MAC, HID, KeX desktop, wireless monitor/scan) **plus** the tool runners, payload
generator, brute-force form and the AI agent that NetHunter's app doesn't have.

Not yet implemented (hardware- or scope-gated):

- **Wireless attacks** (MANA, wifite, full handshake/deauth flow in-app) — the
  monitor-mode scan and per-AP deauth/handshake capture are wired up, but
  Wifite-class automation needs a supported external USB adapter
  (AR9271 / RT3070-class).
- Bluetooth Arsenal, Pineapple connector, USB Arsenal gadget configs.

Known device caveats for the Redmi Note 10 Pro (`sweet`):

- The internal Wi-Fi (QCA6391) **does not support monitor mode** — wireless
  features need a USB OTG adapter.
- **Stock kernel had no `CONFIG_USER_NS` / `CONFIG_PID_NS`.** A rebuilt kernel
  with those enabled now runs on the device (see below); the namespace-mode
  section documents what each one gives you.
- **Mount namespaces do not isolate on this kernel.** `/` is a `shared` mount
  and `mount --make-r*` inside a namespace still propagates back to Android's
  mount table. This is a kernel/Android limitation, not a config flag: it also
  makes `bwrap` fail with `Failed to make / slave: Invalid argument`. Work with
  explicit mounts plus a teardown trap, or accept host-visible mounts.

## Native namespace kernel

The stock LineageOS/sweet kernel ships `CONFIG_USER_NS` and `CONFIG_PID_NS`
disabled, which rules out proot, bubblewrap and anything container-shaped.
A rebuilt boot image with them enabled is produced by
`~/Downloads/nethunter-sweet/build4.sh` (in a container, same flow as the
original NetHunter build) and repacked by `repack.sh`.

Resulting capability on the flashed kernel — verified on-device:

| namespace | stock | rebuilt |
|---|---|---|
| user (`-U`) | ✗ | ✅ `max_user_namespaces=29019` |
| pid (`-p`) | ✗ | ✅ isolated `pid1`, 5 procs vs 865 |
| ipc (`-i`) | ✗ | ✅ |
| uts / net / mnt / cgroup | ✅ | ✅ |

Isolation actually observed: `kali-native --isolated` reports
`pid1=kali-native procs=5`, while the host keeps `pid1=init procs=865`.

Kali binaries still run **natively** — ARM64 code executed directly by the
kernel with full capabilities, not through qemu or proot.

Untuk detail lengkap proses rebuild kernel, angka pengukuran, bukti isolasi, dan
batasan yang tersisa, lihat **[KERNEL-NATIVE.md](docs/KERNEL-NATIVE.md)**. Status
bwrap dan podman (sudah berjalan) ada di
**[magisk/bwrap/README.md](magisk/bwrap/README.md)** dan
**[magisk/podman/README.md](magisk/podman/README.md)**.

---

## License

No license granted yet — all rights reserved by the author. Open an issue if you
want to use or contribute.
