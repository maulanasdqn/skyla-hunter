use regex_lite::Regex;
use std::sync::OnceLock;

fn mac_re() -> &'static Regex {
    static RE: OnceLock<Regex> = OnceLock::new();
    RE.get_or_init(|| Regex::new(r"([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}").expect("mac re"))
}

const IFACE_PREFIXES: &[&str] = &["wlan", "eth", "usb", "mon", "ap0", "wlx"];

#[uniffi::export]
pub fn is_valid_mac(s: String) -> bool {
    mac_re().find(&s).is_some_and(|m| m.as_str() == s)
}

#[uniffi::export]
pub fn extract_mac(s: String) -> Option<String> {
    mac_re().find(&s).map(|m| m.as_str().to_string())
}

#[derive(uniffi::Record, Clone, Debug, Default)]
pub struct MacStatus {
    pub actual: Option<String>,
    pub current: Option<String>,
    pub permanent: Option<String>,
}

#[uniffi::export]
pub fn parse_mac_status(out: String) -> MacStatus {
    let re = mac_re();
    let find_in = |pred: &dyn Fn(&str) -> bool| -> Option<String> {
        out.lines()
            .find(|l| pred(l))
            .and_then(|l| re.find(l).map(|m| m.as_str().to_string()))
    };
    MacStatus {
        actual: find_in(&|l: &str| l.starts_with("ACTUAL=")),
        current: find_in(&|l: &str| l.to_lowercase().contains("current")),
        permanent: find_in(&|l: &str| l.to_lowercase().contains("permanent")),
    }
}

#[uniffi::export]
pub fn net_iface_candidates(raw: String) -> Vec<String> {
    raw.lines()
        .map(|l| l.trim())
        .filter(|l| !l.is_empty())
        .filter(|n| IFACE_PREFIXES.iter().any(|p| n.starts_with(p)))
        .map(|s| s.to_string())
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn valid_and_extract() {
        assert!(is_valid_mac("aa:bb:cc:dd:ee:ff".into()));
        assert!(!is_valid_mac("New MAC: aa:bb:cc:dd:ee:ff (vendor)".into()));
        assert!(!is_valid_mac("no mac here".into()));

        assert_eq!(
            extract_mac("x de:ad:be:ef:00:11 y".into()),
            Some("de:ad:be:ef:00:11".into())
        );
        assert_eq!(extract_mac("nope".into()), None);
    }

    #[test]
    fn parse_status_prefers_actual() {
        let out = "Current MAC:   11:22:33:44:55:66 (x)\n\
                   Permanent MAC: aa:bb:cc:dd:ee:ff (y)\n\
                   ACTUAL=99:88:77:66:55:44";
        let s = parse_mac_status(out.into());
        assert_eq!(s.actual.as_deref(), Some("99:88:77:66:55:44"));
        assert_eq!(s.current.as_deref(), Some("11:22:33:44:55:66"));
        assert_eq!(s.permanent.as_deref(), Some("aa:bb:cc:dd:ee:ff"));
    }

    #[test]
    fn iface_filter() {
        let raw = "lo\nwlan0\neth0\ndummy0\nwlan1mon\nwlxaabb\nsit0\n";
        assert_eq!(
            net_iface_candidates(raw.into()),
            vec!["wlan0", "eth0", "wlan1mon", "wlxaabb"]
        );
    }
}
