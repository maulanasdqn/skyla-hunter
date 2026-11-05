# skyla_core — Rust native core (UniFFI)

Pure, deterministic, Android-free logic for Reforge lives here in Rust and is
exposed to the Kotlin app through [UniFFI](https://mozilla.github.io/uniffi-rs/).

## What's here

- `src/ducky.rs` — Ducky Script → USB-HID (`/dev/hidg0`) compiler. First module
  ported from the old Kotlin `DuckyScript`. Covered by unit tests (`cargo test`).
- `src/lib.rs` — UniFFI exports (`compile_ducky`, `core_version`).
- `uniffi-bindgen.rs` — binding generator entry point (library mode).
- `build.sh` — builds the core and wires it into the app.

Exports are declared with UniFFI **proc-macros** (no `.udl` file). The generated
Kotlin lands in `app/src/main/java/uniffi/skyla_core/` and the arm64 shared
library in `app/src/main/jniLibs/arm64-v8a/libskyla_core.so`.

## Requirements

- Rust (stable) with the Android target: `rustup target add aarch64-linux-android`
- `cargo install cargo-ndk`
- An Android NDK (the Gradle task passes `ANDROID_HOME`; `build.sh` auto-detects
  the newest NDK under `$ANDROID_HOME/ndk`, or honor `ANDROID_NDK_HOME`).

Only `arm64-v8a` is built, matching the app's `abiFilters`.

## Build

Normally you don't run anything by hand: the app's `preBuild` depends on the
`buildRustCore` Gradle task, which runs `build.sh`. To build the core directly:

```sh
cd rust
./build.sh      # generate bindings + arm64 .so into the app
cargo test      # run the ducky compiler tests
```

## Adding a new function

1. Write the Rust and annotate it with `#[uniffi::export]` in `src/lib.rs`.
2. Re-run `./build.sh` (or just build the app) to regenerate the Kotlin bindings.
3. Call it from Kotlin via `uniffi.skyla_core.<fnNameCamelCased>(...)`.
