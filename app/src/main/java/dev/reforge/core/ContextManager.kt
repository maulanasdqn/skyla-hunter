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

    // The estimate + rolling-window algorithm lives in the Rust native core
    // (skyla_core::context); here we only adapt the app's JSONObject turns (which
    // carry role + content) to the core's Turn record and back.

    fun estimateTokens(turns: List<JSONObject>): Int =
        uniffi.skyla_core.estimateTokens(turns.map { it.toTurn() })

    /** Returns the trimmed window to send plus the full history's estimated tokens. */
    fun window(turns: List<JSONObject>, budget: Int = BUDGET_TOKENS): Pair<List<JSONObject>, Int> {
        val r = uniffi.skyla_core.contextWindow(turns.map { it.toTurn() }, budget)
        return r.turns.map { JSONObject().put("role", it.role).put("content", it.content) } to
            r.totalTokens
    }

    private fun JSONObject.toTurn() =
        uniffi.skyla_core.Turn(optString("role"), optString("content"))
}
