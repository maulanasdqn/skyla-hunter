//! airodump-ng CSV parser. Turns the AP section of an airodump `-w ... --output-format csv`
//! dump into a sorted access-point list (strongest signal first).
//!
//! Port of `WirelessViewModel.parseCsv`.

/// One access point seen by airodump-ng.
#[derive(uniffi::Record, Clone)]
pub struct AccessPoint {
    pub bssid: String,
    pub essid: String,
    pub channel: String,
    pub power: i32,
    pub enc: String,
}

/// Parse airodump CSV, keeping only AP rows, sorted by signal power descending.
#[uniffi::export]
pub fn parse_airodump_csv(csv: String) -> Vec<AccessPoint> {
    let mut out: Vec<AccessPoint> = Vec::new();
    for raw in csv.lines() {
        let line = raw.trim();
        if line.is_empty() {
            continue;
        }
        if line.starts_with("BSSID") {
            continue;
        }
        if line.starts_with("Station MAC") {
            break; // station section — stop
        }
        let c: Vec<String> = line.split(',').map(|s| s.trim().to_string()).collect();
        if c.len() < 14 {
            continue;
        }
        let bssid = c[0].clone();
        if !bssid.contains(':') {
            continue;
        }
        let channel = c[3].clone();
        let enc = if c[5].is_empty() { "OPN".to_string() } else { c[5].clone() };
        let power = c[8].parse::<i32>().unwrap_or(-100);
        let essid = if c[13].is_empty() { "<hidden>".to_string() } else { c[13].clone() };
        out.push(AccessPoint { bssid, essid, channel, power, enc });
    }
    // strongest first (stable, matches Kotlin sortedByDescending)
    out.sort_by(|a, b| b.power.cmp(&a.power));
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_and_sorts() {
        let csv = "\
BSSID, First time seen, Last time seen, channel, Speed, Privacy, Cipher, Authentication, Power, beacons, # IV, LAN IP, ID-length, ESSID, Key\n\
AA:BB:CC:DD:EE:01, t, t, 6, 130, WPA2, CCMP, PSK, -70, 10, 0, 0.0.0.0, 7, FarAP, \n\
AA:BB:CC:DD:EE:02, t, t, 1, 130, WPA2, CCMP, PSK, -40, 10, 0, 0.0.0.0, 7, NearAP, \n\
\n\
Station MAC, First time seen, Last, Power, packets, BSSID, Probed\n\
FF:FF:FF:FF:FF:FF, t, t, -30, 5, AA:BB:CC:DD:EE:02,\n";
        let aps = parse_airodump_csv(csv.to_string());
        assert_eq!(aps.len(), 2);
        assert_eq!(aps[0].essid, "NearAP"); // -40 strongest first
        assert_eq!(aps[0].power, -40);
        assert_eq!(aps[1].essid, "FarAP");
    }

    #[test]
    fn blank_enc_and_essid_defaults() {
        let csv = "\
AA:BB:CC:DD:EE:03, t, t, 11, 130, , , , -55, 10, 0, 0.0.0.0, 0, , \n";
        let aps = parse_airodump_csv(csv.to_string());
        assert_eq!(aps.len(), 1);
        assert_eq!(aps[0].enc, "OPN");
        assert_eq!(aps[0].essid, "<hidden>");
    }
}
