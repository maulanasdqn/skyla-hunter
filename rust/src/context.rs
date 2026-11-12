#[derive(uniffi::Record, Clone, Debug)]
pub struct Turn {
    pub role: String,
    pub content: String,
}

#[derive(uniffi::Record, Clone, Debug)]
pub struct WindowResult {
    pub turns: Vec<Turn>,
    pub total_tokens: i32,
}

fn cost(content: &str) -> i32 {
    (content.encode_utf16().count() as i32) / 4 + 4
}

#[uniffi::export]
pub fn estimate_tokens(turns: Vec<Turn>) -> i32 {
    turns.iter().map(|t| cost(&t.content)).sum()
}

#[uniffi::export]
pub fn context_window(turns: Vec<Turn>, budget: i32) -> WindowResult {
    let total: i32 = turns.iter().map(|t| cost(&t.content)).sum();
    let mut kept: Vec<Turn> = Vec::new();
    let mut tok = 0i32;
    for t in turns.iter().rev() {
        let c = cost(&t.content);
        if tok + c > budget && !kept.is_empty() {
            break;
        }
        kept.insert(0, t.clone());
        tok += c;
    }
    let out = if kept.len() < turns.len() {
        let mut v = Vec::with_capacity(kept.len() + 1);
        v.push(Turn {
            role: "system".into(),
            content: "[Earlier conversation was trimmed to fit the context window.]".into(),
        });
        v.extend(kept);
        v
    } else {
        kept
    };
    WindowResult {
        turns: out,
        total_tokens: total,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    fn t(role: &str, content: &str) -> Turn {
        Turn {
            role: role.into(),
            content: content.into(),
        }
    }

    #[test]
    fn estimate_is_chars_over_four_plus_four() {
        assert_eq!(estimate_tokens(vec![t("user", "12345678")]), 6);
    }

    #[test]
    fn window_keeps_all_under_budget() {
        let turns = vec![t("user", "hi"), t("assistant", "yo")];
        let r = context_window(turns.clone(), 16_000);
        assert_eq!(r.turns.len(), 2);
        assert!(r
            .turns
            .iter()
            .all(|x| x.role != "system" || x.content.contains("trimmed") == false));
    }

    #[test]
    fn window_trims_and_prepends_note() {
        let big = "x".repeat(100);
        let turns = vec![t("user", &big), t("assistant", &big), t("user", &big)];
        let r = context_window(turns, 60);
        assert_eq!(r.turns[0].role, "system");
        assert!(r.turns[0].content.contains("trimmed"));

        assert!(r.turns.last().unwrap().content.len() == 100);
    }

    #[test]
    fn window_keeps_at_least_newest_even_if_over_budget() {
        let big = "y".repeat(1000);
        let r = context_window(vec![t("user", &big)], 1);
        assert_eq!(r.turns.len(), 1);
        assert_eq!(r.turns[0].role, "user");
    }
}
