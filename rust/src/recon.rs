//! Recon-agent pure logic, ported from Kotlin `ReconAgent`.
//!
//! These are deterministic, Android-free decisions the agent makes about shell
//! commands and provider config, so they live in the native core where they can
//! be unit-tested and shared:
//! - [`command_is_dangerous`] — the destructive-command denylist (security
//!   critical: it guards the LLM tool-call loop against a scan target injecting
//!   something destructive).
//! - [`detect_tools`] — which known recon tools a command invokes (run summary).
//! - [`parse_headers`] — "Key: Value" lines -> header pairs.

use regex_lite::Regex;
use std::sync::OnceLock;

/// Destructive / dangerous command patterns. Everything not matched is allowed
/// (this is the user's own authorized device); a match makes the agent ask for
/// explicit confirmation before running. Compiled once.
fn deny_patterns() -> &'static [Regex] {
    static PATTERNS: OnceLock<Vec<Regex>> = OnceLock::new();
    PATTERNS.get_or_init(|| {
        [
            r"\brm\s+-\w*[rf]",
            r"\bdd\b[^|]*\bof=",
            r"\bmkfs\b",
            r"\bshred\b",
            r"\bfdisk\b",
            r"\bparted\b",
            r"\bwipefs\b",
            r"\bmkswap\b",
            r"\b(reboot|shutdown|poweroff|halt)\b",
            r">\s*/dev/(sd|block|mmc|null/)",
            r"\bof=/dev/",
            r":\s*\(\s*\)\s*\{", // fork bomb
            r"\buserdel\b",
            r"\b(passwd|chpasswd)\b",
            r"\bchmod\s+-R\s+777\s+/",
            r"\bmv\s+/\s",
        ]
        .iter()
        .map(|p| Regex::new(p).expect("deny pattern must compile"))
        .collect()
    })
}

/// True if the command matches any destructive pattern on the denylist.
#[uniffi::export]
pub fn command_is_dangerous(cmd: String) -> bool {
    deny_patterns().iter().any(|re| re.is_match(&cmd))
}

/// Recon tools recognised for the "tools used" run summary. Order is preserved
/// in [`detect_tools`] output. Matching is case-sensitive, matching the Kotlin
/// original (e.g. `theHarvester`).
const KNOWN_TOOLS: &[&str] = &[
    "rustscan", "nmap", "masscan", "subfinder", "amass", "sublist3r", "whatweb",
    "dnsenum", "dnsrecon", "dig", "host", "whois", "theHarvester", "wafw00f",
    "nikto", "feroxbuster", "ffuf", "gobuster", "sherlock", "holehe", "maigret",
    "phoneinfoga", "exiftool", "socialscan", "finalrecon", "fierce", "dnstwist",
    "nbtscan", "dmitry", "curl", "wpscan",
];

/// Whole-word (ASCII `\b…\b`) search: `word` must be bounded on both sides by a
/// non-`[A-Za-z0-9_]` char or the string edge. Avoids compiling 30 regexes.
fn contains_word(hay: &str, word: &str) -> bool {
    let h = hay.as_bytes();
    let w = word.as_bytes();
    let (n, m) = (h.len(), w.len());
    if m == 0 || m > n {
        return false;
    }
    let is_word = |c: u8| c.is_ascii_alphanumeric() || c == b'_';
    let mut i = 0;
    while i + m <= n {
        if &h[i..i + m] == w {
            let before_ok = i == 0 || !is_word(h[i - 1]);
            let after_ok = i + m == n || !is_word(h[i + m]);
            if before_ok && after_ok {
                return true;
            }
        }
        i += 1;
    }
    false
}

/// Known recon tools present in the command, in [`KNOWN_TOOLS`] order, no dupes.
#[uniffi::export]
pub fn detect_tools(cmd: String) -> Vec<String> {
    KNOWN_TOOLS
        .iter()
        .filter(|t| contains_word(&cmd, t))
        .map(|t| t.to_string())
        .collect()
}

/// One parsed `Key: Value` header line.
#[derive(uniffi::Record, Clone)]
pub struct HeaderPair {
    pub name: String,
    pub value: String,
}

/// Parse "Key: Value" lines into header pairs. Splits on the first `:`, trims
/// both sides, and drops lines with an empty key or value (and blank lines).
#[uniffi::export]
pub fn parse_headers(raw: String) -> Vec<HeaderPair> {
    raw.lines()
        .filter_map(|line| {
            let i = line.find(':')?;
            if i == 0 {
                return None;
            }
            let name = line[..i].trim().to_string();
            let value = line[i + 1..].trim().to_string();
            if name.is_empty() || value.is_empty() {
                None
            } else {
                Some(HeaderPair { name, value })
            }
        })
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn flags_destructive_commands() {
        assert!(command_is_dangerous("rm -rf /".into()));
        assert!(command_is_dangerous("sudo rm -fr ~/x".into()));
        assert!(command_is_dangerous("dd if=/dev/zero of=/dev/sda".into()));
        assert!(command_is_dangerous("mkfs.ext4 /dev/sdb1".into()));
        assert!(command_is_dangerous("reboot".into()));
        assert!(command_is_dangerous(":(){ :|:& };:".into()));
        assert!(command_is_dangerous("chmod -R 777 /".into()));
    }

    #[test]
    fn allows_recon_commands() {
        assert!(!command_is_dangerous(
            "rustscan -a 10.0.0.1 --ulimit 5000 -- -sV".into()
        ));
        assert!(!command_is_dangerous("nmap -sV scanme.nmap.org".into()));
        assert!(!command_is_dangerous("whois example.com".into()));
        // "format" contains no bare mkfs; must not false-positive.
        assert!(!command_is_dangerous("echo please format the report".into()));
    }

    #[test]
    fn detects_tools_in_order_without_dupes() {
        let got = detect_tools("rustscan -a t -- -sV | tee; nmap -p- t; nmap again".into());
        assert_eq!(got, vec!["rustscan".to_string(), "nmap".to_string()]);
    }

    #[test]
    fn tool_match_is_whole_word() {
        // "dig" must not match inside "digest"; "host" not inside "ghost".
        assert!(detect_tools("digest ghostscript".into()).is_empty());
        assert_eq!(detect_tools("dig +short example.com".into()), vec!["dig"]);
    }

    #[test]
    fn parses_headers() {
        let got = parse_headers("X-Netra-Admin-Target: deepseek\n: nokey\nBad\nK:  v  \n".into());
        assert_eq!(got.len(), 2);
        assert_eq!(got[0].name, "X-Netra-Admin-Target");
        assert_eq!(got[0].value, "deepseek");
        assert_eq!(got[1].name, "K");
        assert_eq!(got[1].value, "v");
    }
}
