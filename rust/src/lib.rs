uniffi::setup_scaffolding!();

mod commands;
mod context;
mod ducky;
mod infrared;
mod kv;
mod markdown;
mod netutil;
mod recon;
mod report;
mod wifi;

#[uniffi::export]
pub fn compile_ducky(script: String) -> String {
    ducky::compile(&script)
}

#[uniffi::export]
pub fn core_version() -> String {
    env!("CARGO_PKG_VERSION").to_string()
}
