#[uniffi::export]
pub fn ir_nec_hz() -> i32 {
    38_000
}

#[uniffi::export]
pub fn ir_sony_hz() -> i32 {
    40_000
}

#[derive(uniffi::Record, Clone)]
pub struct IrEntry {
    pub name: String,
    pub freq: i32,
    pub pattern: Vec<i32>,
}

#[uniffi::export]
pub fn ir_nec(address: i32, command: i32) -> Vec<i32> {
    let mut out = Vec::with_capacity(68);
    out.push(9000);
    out.push(4500);
    let byte = |b: i32, out: &mut Vec<i32>| {
        for i in 0..8 {
            out.push(560);
            out.push(if (b >> i) & 1 == 1 { 1690 } else { 560 });
        }
    };
    byte(address & 0xFF, &mut out);
    byte((!address) & 0xFF, &mut out);
    byte(command & 0xFF, &mut out);
    byte((!command) & 0xFF, &mut out);
    out.push(560);
    out
}

#[uniffi::export]
pub fn ir_samsung(address16: i32, command: i32) -> Vec<i32> {
    let mut out = Vec::with_capacity(76);
    out.push(4500);
    out.push(4500);
    let bits = |value: i32, n: i32, out: &mut Vec<i32>| {
        for i in 0..n {
            out.push(560);
            out.push(if (value >> i) & 1 == 1 { 1690 } else { 560 });
        }
    };
    bits(address16, 16, &mut out);
    bits(command & 0xFF, 8, &mut out);
    bits((!command) & 0xFF, 8, &mut out);
    out.push(560);
    out
}

#[uniffi::export]
pub fn ir_sony12(command: i32, device: i32) -> Vec<i32> {
    let mut out = Vec::with_capacity(28);
    out.push(2400);
    out.push(600);
    let bits = |value: i32, n: i32, out: &mut Vec<i32>| {
        for i in 0..n {
            out.push(if (value >> i) & 1 == 1 { 1200 } else { 600 });
            out.push(600);
        }
    };
    bits(command & 0x7F, 7, &mut out);
    bits(device & 0x1F, 5, &mut out);
    out
}

fn entry(name: &str, freq: i32, pattern: Vec<i32>) -> IrEntry {
    IrEntry {
        name: name.to_string(),
        freq,
        pattern,
    }
}

#[uniffi::export]
pub fn ir_tv_power() -> Vec<IrEntry> {
    let nec = ir_nec_hz();
    let sony = ir_sony_hz();
    vec![
        entry("Samsung — Power", nec, ir_samsung(0xE0E0, 0x40)),
        entry("LG — Power", nec, ir_nec(0x04, 0x08)),
        entry("Sony — Power", sony, ir_sony12(0x15, 0x01)),
        entry("Panasonic — Power", nec, ir_nec(0x40, 0x3D)),
        entry("NEC generic — Power", nec, ir_nec(0x00, 0x02)),
        entry("Toshiba — Power", nec, ir_nec(0x40, 0x12)),
    ]
}

#[uniffi::export]
pub fn ir_samsung_controls() -> Vec<IrEntry> {
    let nec = ir_nec_hz();
    vec![
        entry("Vol +", nec, ir_samsung(0xE0E0, 0x07)),
        entry("Vol \u{2212}", nec, ir_samsung(0xE0E0, 0x0B)),
        entry("Ch +", nec, ir_samsung(0xE0E0, 0x12)),
        entry("Ch \u{2212}", nec, ir_samsung(0xE0E0, 0x10)),
        entry("Mute", nec, ir_samsung(0xE0E0, 0x0F)),
        entry("Source", nec, ir_samsung(0xE0E0, 0x01)),
    ]
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn nec_shape() {
        assert_eq!(ir_nec(0x00, 0x02).len(), 67);
        assert_eq!(ir_nec(0, 0)[0], 9000);
        assert_eq!(ir_nec(0, 0)[1], 4500);
    }

    #[test]
    fn nec_inverse_byte() {
        let p = ir_nec(0x00, 0x00);

        assert_eq!(p[18 + 1], 1690);
    }

    #[test]
    fn sony_shape() {
        assert_eq!(ir_sony12(0x15, 0x01).len(), 26);
    }

    #[test]
    fn catalog_nonempty() {
        assert_eq!(ir_tv_power().len(), 6);
        assert_eq!(ir_samsung_controls().len(), 6);
        assert_eq!(ir_tv_power()[0].name, "Samsung — Power");
    }
}
