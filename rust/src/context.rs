//! Conversation context windowing, ported from Kotlin `ContextManager`.
//!
//! Keeps the turns sent to the LLM within a token budget using a rough
//! chars/4 estimate and a rolling window that keeps the most recent turns. Pure
//! and deterministic; the Android side only adapts its `JSONObject` turns to the
//! [`Turn`] record and back.

/// One conversation turn. The app's turns carry only role + content.
#[derive(uniffi::Record, Clone, Debug)]
pub struct Turn {
    pub role: String,
    pub content: String,
}

/// Result of windowing: the turns to send, plus the full history's estimated
/// token count (so the UI can show how much was trimmed).
#[derive(uniffi::Record, Clone, Debug)]
pub struct WindowResult {
    pub turns: Vec<Turn>,
    pub total_tokens: i32,
}

/// Rough per-turn token cost: UTF-16 length / 4 + 4 (matches the Kotlin
/// estimate, which used `String.length`, i.e. UTF-16 code units).
fn cost(content: &str) -> i32 {
    (content.encode_utf16().count() as i32) / 4 + 4
}

/// Estimated token count of all turns.
#[uniffi::export]
pub fn estimate_tokens(turns: Vec<Turn>) -> i32 {
    turns.iter().map(|t| cost(&t.content)).sum()
}

/// Trim to a rolling window that fits `budget` tokens, newest turns kept. If any
/// older turns are dropped, a short system note is prepended so the model knows
/// context was trimmed.
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
    WindowResult { turns: out, total_tokens: total }
}

#[cfg(test)]
mod tests {
    use super::*;
    fn t(role: &str, content: &str) -> Turn {
        Turn { role: role.into(), content: content.into() }
    }

    #[test]
    fn estimate_is_chars_over_four_plus_four() {
        // "12345678" -> 8/4 + 4 = 6
        assert_eq!(estimate_tokens(vec![t("user", "12345678")]), 6);
    }

    #[test]
    fn window_keeps_all_under_budget() {
        let turns = vec![t("user", "hi"), t("assistant", "yo")];
        let r = context_window(turns.clone(), 16_000);
        assert_eq!(r.turns.len(), 2);
        assert!(r.turns.iter().all(|x| x.role != "system" || x.content.contains("trimmed") == false));
    }

    #[test]
    fn window_trims_and_prepends_note() {
        // three ~29-token turns, budget fits ~2 -> one dropped + note prepended
        let big = "x".repeat(100);
        let turns = vec![t("user", &big), t("assistant", &big), t("user", &big)];
        let r = context_window(turns, 60);
        assert_eq!(r.turns[0].role, "system");
        assert!(r.turns[0].content.contains("trimmed"));
        // newest turn is always kept
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
