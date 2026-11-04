package dev.reforge.core

import org.json.JSONObject

/**
 * Keeps the conversation sent to DeepSeek within a token budget.
 * Uses a rough char/4 estimate and a rolling window: the most recent turns are
 * kept; when older turns are dropped, a short note is prepended so the model
 * knows context was trimmed. (A future step can replace trimming with an
 * LLM-generated running summary.)
 */
object ContextManager {
    const val BUDGET_TOKENS = 16_000

    fun estimateTokens(turns: List<JSONObject>): Int =
        turns.sumOf { it.optString("content").length / 4 + 4 }

    /** Returns the trimmed window to send plus the full history's estimated tokens. */
    fun window(turns: List<JSONObject>, budget: Int = BUDGET_TOKENS): Pair<List<JSONObject>, Int> {
        val total = estimateTokens(turns)
        val kept = ArrayDeque<JSONObject>()
        var tok = 0
        for (t in turns.asReversed()) {
            val c = t.optString("content").length / 4 + 4
            if (tok + c > budget && kept.isNotEmpty()) break
            kept.addFirst(t)
            tok += c
        }
        val out = if (kept.size < turns.size) {
            listOf(
                JSONObject().put("role", "system")
                    .put("content", "[Earlier conversation was trimmed to fit the context window.]")
            ) + kept
        } else kept.toList()
        return out to total
    }
}
