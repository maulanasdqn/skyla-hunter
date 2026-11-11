//! IR remote code generators. Phones transmit only, so this powers a
//! universal-remote / TV-B-Gone style feature. Patterns are alternating
//! on/off durations in microseconds, as `ConsumerIrManager.transmit()` wants.
//!
//! Port of the original Kotlin `Infrared` object + `IrCatalog`.

/// NEC carrier frequency (Hz).
#[uniffi::export]
pub fn ir_nec_hz() -> i32 {
    38_000
}

/// Sony SIRC carrier frequency (Hz).
#[uniffi::export]
pub fn ir_sony_hz() -> i32 {
    40_000
}

/// A labelled IR command: name, carrier frequency, and on/off pattern (µs).
#[derive(uniffi::Record, Clone)]
pub struct IrEntry {
    pub name: String,
    pub freq: i32,
    pub pattern: Vec<i32>,
}

/// Standard NEC: 8-bit address + inverse, 8-bit command + inverse, LSB-first.
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

/// Samsung: 4500/4500 lead, 16-bit address, 8-bit command + inverse, LSB-first.
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

/// Sony SIRC 12-bit: 7-bit command + 5-bit device, LSB-first, 40 kHz.
#[uniffi::export]
pub fn ir_sony12(command: i32, device: i32) -> Vec<i32> {
    let mut out = Vec::with_capacity(28);
    out.push(2400);
    out.push(600); // header
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
    IrEntry { name: name.to_string(), freq, pattern }
}

/// Starter multi-brand TV power catalog.
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

/// Samsung TV control set (volume / channel / mute / source).
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
        // 2 (lead) + 4*16 (four bytes) + 1 (stop) = 67
        assert_eq!(ir_nec(0x00, 0x02).len(), 67);
        assert_eq!(ir_nec(0, 0)[0], 9000);
        assert_eq!(ir_nec(0, 0)[1], 4500);
    }

    #[test]
    fn nec_inverse_byte() {
        // address 0x00 -> inverse 0xFF: the 8 bits after the first byte are all "1" (1690)
        let p = ir_nec(0x00, 0x00);
        // first byte (0x00) is all zero bits -> all 560; second byte (0xFF) all 1690
        // byte starts at index 2; second byte starts at 2 + 16 = 18
        assert_eq!(p[18 + 1], 1690);
    }

    #[test]
    fn sony_shape() {
        // 2 (header) + 2*(7+5) = 26
        assert_eq!(ir_sony12(0x15, 0x01).len(), 26);
    }

    #[test]
    fn catalog_nonempty() {
        assert_eq!(ir_tv_power().len(), 6);
        assert_eq!(ir_samsung_controls().len(), 6);
        assert_eq!(ir_tv_power()[0].name, "Samsung — Power");
    }
}
