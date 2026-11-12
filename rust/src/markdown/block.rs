use super::inline::parse_inline;
use super::*;

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
                out.push(MdBlock::Code {
                    code: code_buf.trim_end().to_string(),
                });
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

        if is_table_row(line) && i + 1 < lines.len() && is_table_sep(lines[i + 1]) {
            let header: Vec<Vec<Span>> = split_row(line).into_iter().map(parse_inline).collect();
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
            out.push(MdBlock::Heading {
                level: 3,
                spans: parse_inline(rest.to_string()),
            });
        } else if let Some(rest) = line.strip_prefix("## ") {
            out.push(MdBlock::Heading {
                level: 2,
                spans: parse_inline(rest.to_string()),
            });
        } else if let Some(rest) = line.strip_prefix("# ") {
            out.push(MdBlock::Heading {
                level: 1,
                spans: parse_inline(rest.to_string()),
            });
        } else if is_bullet(line) {
            out.push(MdBlock::Bullet {
                spans: parse_inline(strip_bullet(line)),
            });
        } else if is_numbered(line) {
            let t = line.trim_start();
            let number = t.split('.').next().unwrap_or("").to_string();
            let text = match t.split_once(". ") {
                Some((_, after)) => after.to_string(),
                None => String::new(),
            };
            out.push(MdBlock::Numbered {
                number,
                spans: parse_inline(text),
            });
        } else {
            out.push(MdBlock::Paragraph {
                spans: parse_inline(line.to_string()),
            });
        }
        i += 1;
    }

    if in_code && !code_buf.is_empty() {
        out.push(MdBlock::Code {
            code: code_buf.trim_end().to_string(),
        });
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
        && t.chars()
            .all(|c| c == '|' || c == '-' || c == ':' || c == ' ')
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
