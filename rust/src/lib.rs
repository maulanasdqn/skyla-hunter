//! Native core for Reforge (Skyla NetHunter).
//!
//! Pure, deterministic, Android-free logic lives here in Rust and is exposed to
//! the Kotlin app through UniFFI. Modules:
//! - [`ducky`]    — Ducky Script -> USB-HID compiler
//! - [`infrared`] — IR remote code generators + a small catalog
//! - [`markdown`] — Markdown -> structured block model (Kotlin renders it)
//! - [`wifi`]     — airodump-ng CSV parser
//! - [`commands`] — builders for parameterised tool commands (msfvenom, hydra)
//! - [`recon`]    — recon-agent command denylist, tool detection, header parsing
//! - [`report`]   — recon-report Markdown builder + tool extraction
//! - [`context`]  — LLM context token estimate + rolling window
//! - [`netutil`]  — MAC parse/validate + network-interface filtering

uniffi::setup_scaffolding!();

mod commands;
mod context;
mod ducky;
mod infrared;
mod markdown;
mod netutil;
mod recon;
mod report;
mod wifi;

/// Compile Ducky Script into a bash script that writes USB-HID boot-keyboard
/// reports to `/dev/hidg0`. Empty string if nothing typable was found.
#[uniffi::export]
pub fn compile_ducky(script: String) -> String {
    ducky::compile(&script)
}

/// Version of the native core, so the app can show/verify which `.so` it loaded.
#[uniffi::export]
pub fn core_version() -> String {
    env!("CARGO_PKG_VERSION").to_string()
}
