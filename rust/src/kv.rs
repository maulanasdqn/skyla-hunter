//! `KEY=VALUE` line parsing, shared by the Dashboard and Chroot info views.
//!
//! Both gather system facts by printing `key=value` lines from a shell
//! one-liner, then splitting them into a map. That parse lives here so it is
//! written (and tested) once.

use std::collections::HashMap;

/// Parse `KEY=VALUE` lines into a map. Splits each line on its first `=`, trims
/// key and value, and skips lines without a `=`. On a duplicate key the last
/// line wins (matching Kotlin's `associate`).
#[uniffi::export]
pub fn parse_kv_lines(text: String) -> HashMap<String, String> {
    let mut map = HashMap::new();
    for line in text.lines() {
        if let Some((k, v)) = line.split_once('=') {
            map.insert(k.trim().to_string(), v.trim().to_string());
        }
    }
    map
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_trims_and_skips() {
        let out = "os=Kali GNU/Linux\n  kernel = 4.14.357 \nnoequalshere\nempty=";
        let m = parse_kv_lines(out.into());
        assert_eq!(m.get("os").map(String::as_str), Some("Kali GNU/Linux"));
        assert_eq!(m.get("kernel").map(String::as_str), Some("4.14.357"));
        assert_eq!(m.get("empty").map(String::as_str), Some(""));
        assert!(!m.contains_key("noequalshere"));
        assert_eq!(m.len(), 3);
    }

    #[test]
    fn value_may_contain_equals() {
        let m = parse_kv_lines("disk=3G used / 10G (30%)\nq=a=b=c".into());
        assert_eq!(m.get("disk").map(String::as_str), Some("3G used / 10G (30%)"));
        assert_eq!(m.get("q").map(String::as_str), Some("a=b=c"));
    }

    #[test]
    fn last_duplicate_wins() {
        let m = parse_kv_lines("k=1\nk=2".into());
        assert_eq!(m.get("k").map(String::as_str), Some("2"));
    }
}
