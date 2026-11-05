//! Markdown -> structured block model.
//!
//! The old Kotlin `MarkdownText` fused parsing with Compose rendering. Parsing
//! now lives here and returns a flat list of [`MdBlock`]s (headings, lists,
//! code, tables, paragraphs) whose text is already split into styled [`Span`]s;
//! the Kotlin side only turns that model into Compose nodes.
//!
//! Supports: headings, bullet & numbered lists, bold/italic/inline-code,
//! fenced code blocks, and GitHub-style tables.

/// Inline text style for a run of characters.
#[derive(uniffi::Enum, Clone, Copy, PartialEq, Eq, Debug)]
pub enum SpanStyle {
    Plain,
    Bold,
    Italic,
    Code,
}

/// A run of text with a single inline style.
#[derive(uniffi::Record, Clone, PartialEq, Eq, Debug)]
pub struct Span {
    pub text: String,
    pub style: SpanStyle,
}

/// A parsed block. `Heading.level` is 1/2/3 for `#`/`##`/`###`.
/// Tables carry cells already parsed into spans: `header[col]`, `rows[row][col]`.
#[derive(uniffi::Enum, Clone, Debug)]
pub enum MdBlock {
    Heading { level: u8, spans: Vec<Span> },
    Paragraph { spans: Vec<Span> },
    Bullet { spans: Vec<Span> },
    Numbered { number: String, spans: Vec<Span> },
    Code { code: String },
    Table { header: Vec<Vec<Span>>, rows: Vec<Vec<Vec<Span>>> },
    Spacer,
}

/// Parse inline markdown: `**bold**`, `*italic*`, `` `code` ``. Consecutive
/// plain characters coalesce into a single [`Span`].
#[uniffi::export]
pub fn parse_inline(s: String) -> Vec<Span> {
    let chars: Vec<char> = s.chars().collect();
    let n = chars.len();
    let mut out: Vec<Span> = Vec::new();
    let mut buf = String::new();
    let mut i = 0usize;

    let flush = |buf: &mut String, out: &mut Vec<Span>| {
        if !buf.is_empty() {
            out.push(Span { text: std::mem::take(buf), style: SpanStyle::Plain });
        }
    };

    while i < n {
        if i + 1 < n && chars[i] == '*' && chars[i + 1] == '*' {
            if let Some(end) = find_pair(&chars, i + 2) {
                flush(&mut buf, &mut out);
                out.push(Span { text: chars[i + 2..end].iter().collect(), style: SpanStyle::Bold });
                i = end + 2;
            } else {
                buf.push(chars[i]);
                i += 1;
            }
        } else if chars[i] == '`' {
            if let Some(end) = find_char(&chars, i + 1, '`') {
                flush(&mut buf, &mut out);
                out.push(Span { text: chars[i + 1..end].iter().collect(), style: SpanStyle::Code });
                i = end + 1;
            } else {
                buf.push(chars[i]);
                i += 1;
            }
        } else if chars[i] == '*' {
            if let Some(end) = find_char(&chars, i + 1, '*') {
                flush(&mut buf, &mut out);
                out.push(Span { text: chars[i + 1..end].iter().collect(), style: SpanStyle::Italic });
                i = end + 1;
            } else {
                buf.push(chars[i]);
                i += 1;
            }
        } else {
            buf.push(chars[i]);
            i += 1;
        }
    }
    flush(&mut buf, &mut out);
    out
}

/// Index of the next `**` at or after `from`.
fn find_pair(chars: &[char], from: usize) -> Option<usize> {
    let n = chars.len();
    let mut j = from;
    while j + 1 < n {
        if chars[j] == '*' && chars[j + 1] == '*' {
            return Some(j);
        }
        j += 1;
    }
    None
}

/// Index of the next `c` at or after `from`.
fn find_char(chars: &[char], from: usize, c: char) -> Option<usize> {
    chars[from..].iter().position(|&x| x == c).map(|p| from + p)
}

/// Parse a whole markdown document into a flat list of blocks.
#[uniffi::export]
pub fn parse_markdown(md: String) -> Vec<MdBlock> {
    let normalized = md.replace("\r\n", "\n");
    let lines: Vec<&str> = normalized.split('\n').collect();
    let mut out: Vec<MdBlock> = Vec::new();
    let mut code_buf = String::new();
    let mut in_code = false;
    let mut i = 0usize;

    while i < lines.len() {
        let line = lines[i];

        if line.trim_start().starts_with("```") {
            if in_code {
                out.push(MdBlock::Code { code: code_buf.trim_end().to_string() });
                code_buf.clear();
                in_code = false;
            } else {
                in_code = true;
            }
            i += 1;
            continue;
        }
        if in_code {
            code_buf.push_str(line);
            code_buf.push('\n');
            i += 1;
            continue;
        }

        // table: header row + separator row
        if is_table_row(line) && i + 1 < lines.len() && is_table_sep(lines[i + 1]) {
            let header: Vec<Vec<Span>> =
                split_row(line).into_iter().map(parse_inline).collect();
            let mut j = i + 2;
            let mut rows: Vec<Vec<Vec<Span>>> = Vec::new();
            while j < lines.len() && is_table_row(lines[j]) && !is_table_sep(lines[j]) {
                rows.push(split_row(lines[j]).into_iter().map(parse_inline).collect());
                j += 1;
            }
            out.push(MdBlock::Table { header, rows });
            i = j;
            continue;
        }

        if line.trim().is_empty() {
            out.push(MdBlock::Spacer);
        } else if let Some(rest) = line.strip_prefix("### ") {
            out.push(MdBlock::Heading { level: 3, spans: parse_inline(rest.to_string()) });
        } else if let Some(rest) = line.strip_prefix("## ") {
            out.push(MdBlock::Heading { level: 2, spans: parse_inline(rest.to_string()) });
        } else if let Some(rest) = line.strip_prefix("# ") {
            out.push(MdBlock::Heading { level: 1, spans: parse_inline(rest.to_string()) });
        } else if is_bullet(line) {
            out.push(MdBlock::Bullet { spans: parse_inline(strip_bullet(line)) });
        } else if is_numbered(line) {
            let t = line.trim_start();
            let number = t.split('.').next().unwrap_or("").to_string();
            let text = match t.split_once(". ") {
                Some((_, after)) => after.to_string(),
                None => String::new(),
            };
            out.push(MdBlock::Numbered { number, spans: parse_inline(text) });
        } else {
            out.push(MdBlock::Paragraph { spans: parse_inline(line.to_string()) });
        }
        i += 1;
    }

    if in_code && !code_buf.is_empty() {
        out.push(MdBlock::Code { code: code_buf.trim_end().to_string() });
    }
    out
}

fn is_table_row(l: &str) -> bool {
    let t = l.trim();
    t.starts_with('|') && t.contains('|')
}

fn is_table_sep(l: &str) -> bool {
    let t = l.trim();
    t.starts_with('|')
        && t.chars().all(|c| c == '|' || c == '-' || c == ':' || c == ' ')
        && t.contains('-')
}

fn split_row(l: &str) -> Vec<String> {
    l.trim()
        .trim_matches('|')
        .split('|')
        .map(|s| s.trim().to_string())
        .collect()
}

fn is_bullet(l: &str) -> bool {
    let t = l.trim_start();
    t.starts_with("- ") || t.starts_with("* ")
}

fn strip_bullet(l: &str) -> String {
    l.trim_start().chars().skip(2).collect()
}

fn is_numbered(l: &str) -> bool {
    let t = l.trim_start();
    if let Some(dot) = t.find(". ") {
        (1..=3).contains(&dot) && t[..dot].chars().all(|c| c.is_ascii_digit())
    } else {
        false
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn plain(s: &str) -> Span {
        Span { text: s.to_string(), style: SpanStyle::Plain }
    }

    #[test]
    fn inline_bold_italic_code() {
        let got = parse_inline("a **b** c `d` *e*".to_string());
        assert_eq!(
            got,
            vec![
                plain("a "),
                Span { text: "b".into(), style: SpanStyle::Bold },
                plain(" c "),
                Span { text: "d".into(), style: SpanStyle::Code },
                plain(" "),
                Span { text: "e".into(), style: SpanStyle::Italic },
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
}
