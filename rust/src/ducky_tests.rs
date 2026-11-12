use super::*;

#[test]
fn empty_input_is_just_two_releases() {
    assert_eq!(compile(""), format!("{}{}", release(), release()));
}

#[test]
fn rem_is_ignored() {
    assert_eq!(
        compile("REM hello world"),
        format!("{}{}", release(), release())
    );
}

#[test]
fn gui_r_combo() {
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
    assert!(out.contains(&printf_report(0, 0x04)));
    assert!(out.contains(&printf_report(0, 0x28)));
}

#[test]
fn delay_and_default_delay() {
    assert!(compile("DELAY 500").contains("sleep 0.500\n"));

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
