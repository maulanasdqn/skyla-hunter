package dev.reforge.ui.screens

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A saved conversation: display messages + the LLM turn history. */
data class Session(
    val id: String,
    var title: String,
    var updatedAt: Long,
    val ui: MutableList<ChatMessage>,
    val turns: MutableList<JSONObject>,
)

/** Lightweight listing entry for the history sheet. */
data class SessionMeta(val id: String, val title: String, val updatedAt: Long)

/** Persists chat sessions as JSON files under filesDir/sessions. */
class ChatStore(context: Context) {
    private val dir = File(context.filesDir, "sessions").apply { mkdirs() }

    fun newId(): String = System.currentTimeMillis().toString(36) + "-" + (0..9999).random()

    fun save(s: Session) {
        val uiArr = JSONArray()
        s.ui.forEach {
            uiArr.put(JSONObject().put("id", it.id).put("kind", it.kind.name).put("text", it.text))
        }
        val turnsArr = JSONArray()
        s.turns.forEach { turnsArr.put(it) }
        val o = JSONObject()
            .put("id", s.id).put("title", s.title).put("updatedAt", s.updatedAt)
            .put("ui", uiArr).put("turns", turnsArr)
        File(dir, "${s.id}.json").writeText(o.toString())
    }

    fun load(id: String): Session? {
        val f = File(dir, "$id.json")
        if (!f.exists()) return null
        return parse(JSONObject(f.readText()))
    }

    fun list(): List<SessionMeta> =
        dir.listFiles { f -> f.extension == "json" }?.mapNotNull { f ->
            runCatching {
                val o = JSONObject(f.readText())
                SessionMeta(o.getString("id"), o.optString("title", "Chat"), o.optLong("updatedAt"))
            }.getOrNull()
        }?.sortedByDescending { it.updatedAt } ?: emptyList()

    fun delete(id: String) { File(dir, "$id.json").delete() }

    private fun parse(o: JSONObject): Session {
        val ui = mutableListOf<ChatMessage>()
        val uiArr = o.optJSONArray("ui") ?: JSONArray()
        for (i in 0 until uiArr.length()) {
            val m = uiArr.getJSONObject(i)
            val kind = runCatching { MsgKind.valueOf(m.getString("kind")) }.getOrDefault(MsgKind.ANSWER)
            ui.add(ChatMessage(m.optLong("id", i.toLong()), kind, m.getString("text")))
        }
        val turns = mutableListOf<JSONObject>()
        val turnsArr = o.optJSONArray("turns") ?: JSONArray()
        for (i in 0 until turnsArr.length()) turns.add(turnsArr.getJSONObject(i))
        return Session(o.getString("id"), o.optString("title", "Chat"), o.optLong("updatedAt"), ui, turns)
    }
}
