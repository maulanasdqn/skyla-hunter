use super::block::*;
use super::inline::*;
use super::*;

fn plain(s: &str) -> Span {
    Span {
        text: s.to_string(),
        style: SpanStyle::Plain,
    }
}

#[test]
fn inline_bold_italic_code() {
    let got = parse_inline("a **b** c `d` *e*".to_string());
    assert_eq!(
        got,
        vec![
            plain("a "),
            Span {
                text: "b".into(),
                style: SpanStyle::Bold
            },
            plain(" c "),
            Span {
                text: "d".into(),
                style: SpanStyle::Code
            },
            plain(" "),
            Span {
                text: "e".into(),
                style: SpanStyle::Italic
            },
        ]
    );
}

#[test]
fn unterminated_marker_is_literal() {
    assert_eq!(parse_inline("a **b".to_string()), vec![plain("a **b")]);
}

#[test]
fn headings_and_lists() {
    let b = parse_markdown("# Title\n- one\n2. two".to_string());
    assert!(matches!(b[0], MdBlock::Heading { level: 1, .. }));
    assert!(matches!(b[1], MdBlock::Bullet { .. }));
    match &b[2] {
        MdBlock::Numbered { number, .. } => assert_eq!(number, "2"),
        other => panic!("expected numbered, got {other:?}"),
    }
}

#[test]
fn fenced_code_block() {
    let b = parse_markdown("```\nnmap -sV t\nid\n```".to_string());
    assert_eq!(b.len(), 1);
    match &b[0] {
        MdBlock::Code { code } => assert_eq!(code, "nmap -sV t\nid"),
        other => panic!("expected code, got {other:?}"),
    }
}

#[test]
fn github_table() {
    let md = "| A | B |\n| - | - |\n| 1 | 2 |\n| 3 | 4 |";
    let b = parse_markdown(md.to_string());
    assert_eq!(b.len(), 1);
    match &b[0] {
        MdBlock::Table { header, rows } => {
            assert_eq!(header.len(), 2);
            assert_eq!(rows.len(), 2);
            assert_eq!(header[0][0].text, "A");
            assert_eq!(rows[1][1][0].text, "4");
        }
        other => panic!("expected table, got {other:?}"),
    }
}
