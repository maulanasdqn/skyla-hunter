use super::*;

#[uniffi::export]
pub fn parse_inline(s: String) -> Vec<Span> {
    let chars: Vec<char> = s.chars().collect();
    let n = chars.len();
    let mut out: Vec<Span> = Vec::new();
    let mut buf = String::new();
    let mut i = 0usize;

    let flush = |buf: &mut String, out: &mut Vec<Span>| {
        if !buf.is_empty() {
            out.push(Span {
                text: std::mem::take(buf),
                style: SpanStyle::Plain,
            });
        }
    };

    while i < n {
        if i + 1 < n && chars[i] == '*' && chars[i + 1] == '*' {
            if let Some(end) = find_pair(&chars, i + 2) {
                flush(&mut buf, &mut out);
                out.push(Span {
                    text: chars[i + 2..end].iter().collect(),
                    style: SpanStyle::Bold,
                });
                i = end + 2;
            } else {
                buf.push(chars[i]);
                i += 1;
            }
        } else if chars[i] == '`' {
            if let Some(end) = find_char(&chars, i + 1, '`') {
                flush(&mut buf, &mut out);
                out.push(Span {
                    text: chars[i + 1..end].iter().collect(),
                    style: SpanStyle::Code,
                });
                i = end + 1;
            } else {
                buf.push(chars[i]);
                i += 1;
            }
        } else if chars[i] == '*' {
            if let Some(end) = find_char(&chars, i + 1, '*') {
                flush(&mut buf, &mut out);
                out.push(Span {
                    text: chars[i + 1..end].iter().collect(),
                    style: SpanStyle::Italic,
                });
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

fn find_char(chars: &[char], from: usize, c: char) -> Option<usize> {
    chars[from..].iter().position(|&x| x == c).map(|p| from + p)
}
