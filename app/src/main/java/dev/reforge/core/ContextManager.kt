package dev.reforge.core

import org.json.JSONObject

object ContextManager {
    const val BUDGET_TOKENS = 16_000

    fun estimateTokens(turns: List<JSONObject>): Int =
        uniffi.skyla_core.estimateTokens(turns.map { it.toTurn() })

    fun window(turns: List<JSONObject>, budget: Int = BUDGET_TOKENS): Pair<List<JSONObject>, Int> {
        val r = uniffi.skyla_core.contextWindow(turns.map { it.toTurn() }, budget)
        return r.turns.map { JSONObject().put("role", it.role).put("content", it.content) } to
            r.totalTokens
    }

    private fun JSONObject.toTurn() =
        uniffi.skyla_core.Turn(optString("role"), optString("content"))
}
