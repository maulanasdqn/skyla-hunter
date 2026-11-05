//! Ducky Script -> USB-HID compiler.
//!
//! Port of the original Kotlin `DuckyScript` object. Each HID boot-keyboard
//! report is 8 bytes: [modifier, reserved, key1..key6]; a keypress is a press
//! report followed by an all-zero release report, written to `/dev/hidg0`.
//!
//! Supported: REM, DELAY, DEFAULTDELAY/DEFAULT_DELAY, STRING, STRINGLN,
//! ENTER/TAB/SPACE/ESC/arrows/F-keys/etc, and modifier combos
//! (CTRL/ALT/SHIFT/GUI + key), e.g. `GUI r`, `CTRL ALT DELETE`.

const CTRL: u8 = 0x01;
const SHIFT: u8 = 0x02;
const ALT: u8 = 0x04;
const GUI: u8 = 0x08;

/// char -> (keycode, modifier). Mirrors the Kotlin `keyForChar` table.
fn key_for_char(c: char) -> Option<(u8, u8)> {
    match c {
        'a'..='z' => Some((0x04 + (c as u8 - b'a'), 0)),
        'A'..='Z' => Some((0x04 + (c as u8 - b'A'), SHIFT)),
        '1'..='9' => Some((0x1e + (c as u8 - b'1'), 0)),
        '0' => Some((0x27, 0)),
        // shifted digit row "!@#$%^&*()"
        '!' => Some((0x1e, SHIFT)),
        '@' => Some((0x1f, SHIFT)),
        '#' => Some((0x20, SHIFT)),
        '$' => Some((0x21, SHIFT)),
        '%' => Some((0x22, SHIFT)),
        '^' => Some((0x23, SHIFT)),
        '&' => Some((0x24, SHIFT)),
        '*' => Some((0x25, SHIFT)),
        '(' => Some((0x26, SHIFT)),
        ')' => Some((0x27, SHIFT)),
        ' ' => Some((0x2c, 0)),
        '-' => Some((0x2d, 0)),
        '_' => Some((0x2d, SHIFT)),
        '=' => Some((0x2e, 0)),
        '+' => Some((0x2e, SHIFT)),
        '[' => Some((0x2f, 0)),
        '{' => Some((0x2f, SHIFT)),
        ']' => Some((0x30, 0)),
        '}' => Some((0x30, SHIFT)),
        '\\' => Some((0x31, 0)),
        '|' => Some((0x31, SHIFT)),
        ';' => Some((0x33, 0)),
        ':' => Some((0x33, SHIFT)),
        '\'' => Some((0x34, 0)),
        '"' => Some((0x34, SHIFT)),
        '`' => Some((0x35, 0)),
        '~' => Some((0x35, SHIFT)),
        ',' => Some((0x36, 0)),
        '<' => Some((0x36, SHIFT)),
        '.' => Some((0x37, 0)),
        '>' => Some((0x37, SHIFT)),
        '/' => Some((0x38, 0)),
        '?' => Some((0x38, SHIFT)),
        '\t' => Some((0x2b, 0)),
        _ => None,
    }
}

/// Named key -> keycode. Mirrors the Kotlin `named` table (keys are uppercase).
fn named(cmd: &str) -> Option<u8> {
    Some(match cmd {
        "ENTER" | "RETURN" => 0x28,
        "ESC" | "ESCAPE" => 0x29,
        "BACKSPACE" => 0x2a,
        "DELETE" | "DEL" => 0x4c,
        "TAB" => 0x2b,
        "SPACE" => 0x2c,
        "UP" | "UPARROW" => 0x52,
        "DOWN" | "DOWNARROW" => 0x51,
        "LEFT" | "LEFTARROW" => 0x50,
        "RIGHT" | "RIGHTARROW" => 0x4f,
        "HOME" => 0x4a,
        "END" => 0x4d,
        "INSERT" => 0x49,
        "PAGEUP" => 0x4b,
        "PAGEDOWN" => 0x4e,
        "CAPSLOCK" => 0x39,
        "PRINTSCREEN" => 0x46,
        "F1" => 0x3a,
        "F2" => 0x3b,
        "F3" => 0x3c,
        "F4" => 0x3d,
        "F5" => 0x3e,
        "F6" => 0x3f,
        "F7" => 0x40,
        "F8" => 0x41,
        "F9" => 0x42,
        "F10" => 0x43,
        "F11" => 0x44,
        "F12" => 0x45,
        _ => return None,
    })
}

/// Modifier name -> bit. Mirrors the Kotlin `modNames` table (keys are uppercase).
fn mod_name(tok: &str) -> Option<u8> {
    Some(match tok {
        "CTRL" | "CONTROL" => CTRL,
        "SHIFT" => SHIFT,
        "ALT" => ALT,
        "GUI" | "WINDOWS" | "WIN" | "COMMAND" | "META" => GUI,
        _ => return None,
    })
}

fn release() -> String {
    "printf '\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00' > /dev/hidg0\n".to_string()
}

fn printf_report(modifier: u8, code: u8) -> String {
    format!(
        "printf '\\x{:02x}\\x00\\x{:02x}\\x00\\x00\\x00\\x00\\x00' > /dev/hidg0\n",
        modifier, code
    )
}

fn tap(sb: &mut String, modifier: u8, code: u8) {
    sb.push_str(&printf_report(modifier, code));
    sb.push_str(&release());
}

fn sleep_ms(ms: i32) -> String {
    if ms <= 0 {
        String::new()
    } else {
        format!("sleep {:.3}\n", ms as f64 / 1000.0)
    }
}

fn type_string(sb: &mut String, s: &str) {
    for c in s.chars() {
        if let Some((code, modifier)) = key_for_char(c) {
            tap(sb, modifier, code);
        }
    }
}

/// Returns a bash script, or an empty string if nothing typable was found.
pub fn compile(text: &str) -> String {
    let mut sb = String::new();
    sb.push_str(&release()); // make sure no key is stuck before we start
    let mut default_delay: i32 = 0;

    for raw in text.split('\n') {
        let line = raw.trim();
        if line.is_empty() {
            continue;
        }
        // split on the first space (limit = 2), preserving the rest verbatim
        let (head, rest) = match line.split_once(' ') {
            Some((a, b)) => (a, b),
            None => (line, ""),
        };
        let cmd = head.to_uppercase();

        if cmd == "REM" {
            // comment: nothing
        } else if cmd == "DELAY" {
            sb.push_str(&sleep_ms(rest.trim().parse::<i32>().unwrap_or(0)));
        } else if cmd == "DEFAULTDELAY" || cmd == "DEFAULT_DELAY" {
            default_delay = rest.trim().parse::<i32>().unwrap_or(0);
        } else if cmd == "STRING" {
            type_string(&mut sb, rest);
        } else if cmd == "STRINGLN" {
            type_string(&mut sb, rest);
            tap(&mut sb, 0, 0x28);
        } else if let Some(code) = named(&cmd) {
            tap(&mut sb, 0, code);
        } else if mod_name(&cmd).is_some() {
            // modifier combo: scan every whitespace-separated token on the line
            let mut modifier: u8 = 0;
            let mut code: u8 = 0;
            for t in line.split_whitespace() {
                let tu = t.to_uppercase();
                if let Some(m) = mod_name(&tu) {
                    modifier |= m;
                } else if let Some(n) = named(&tu) {
                    code = n;
                } else if t.chars().count() == 1 {
                    if let Some((c, m)) = key_for_char(t.chars().next().unwrap()) {
                        code = c;
                        modifier |= m;
                    }
                }
            }
            tap(&mut sb, modifier, code);
        }
        // else: unknown command -> ignore (matches Kotlin)

        if default_delay > 0 {
            sb.push_str(&sleep_ms(default_delay));
        }
    }

    sb.push_str(&release());
    sb
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn empty_input_is_just_two_releases() {
        // leading release + trailing release, nothing in between
        assert_eq!(compile(""), format!("{}{}", release(), release()));
    }

    #[test]
    fn rem_is_ignored() {
        assert_eq!(compile("REM hello world"), format!("{}{}", release(), release()));
    }

    #[test]
    fn gui_r_combo() {
        // GUI (0x08) + r (0x15)
        let out = compile("GUI r");
        let expected = format!(
            "{}{}{}{}",
            release(),
            printf_report(GUI, 0x15),
            release(),
            release()
        );
        assert_eq!(out, expected);
    }

    #[test]
    fn string_types_each_char_with_shift_for_caps() {
        // "Ab" -> A (0x04 + SHIFT), b (0x05)
        let out = compile("STRING Ab");
        let expected = format!(
            "{}{}{}{}{}{}",
            release(),
            printf_report(SHIFT, 0x04),
            release(),
            printf_report(0, 0x05),
            release(),
            release()
        );
        assert_eq!(out, expected);
    }

    #[test]
    fn stringln_appends_enter() {
        let out = compile("STRINGLN a");
        assert!(out.contains(&printf_report(0, 0x04))); // 'a'
        assert!(out.contains(&printf_report(0, 0x28))); // ENTER
    }

    #[test]
    fn delay_and_default_delay() {
        assert!(compile("DELAY 500").contains("sleep 0.500\n"));
        // DEFAULTDELAY applies after each subsequent action line
        let out = compile("DEFAULTDELAY 100\nENTER");
        assert!(out.contains("sleep 0.100\n"));
    }

    #[test]
    fn ctrl_alt_delete() {
        let out = compile("CTRL ALT DELETE");
        let expected = format!(
            "{}{}{}{}",
            release(),
            printf_report(CTRL | ALT, 0x4c),
            release(),
            release()
        );
        assert_eq!(out, expected);
    }
}
