package dev.reforge.core

import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ReconClient(
    private val apiKey: String,
    private val model: String,
    private val baseUrl: String,
    private val extraHeaders: String,
    private val extraBody: String,
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    @Volatile private var currentCall: Call? = null

    fun cancel() { currentCall?.cancel() }

    fun stream(
        messages: JSONArray,
        emit: (AgentEvent) -> Unit,
        toolChoice: String = "auto",
    ): JSONObject {
        val bodyObj = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("tools", toolsSpec())
            .put("tool_choice", toolChoice)
            .put("temperature", 0.2)
            .put("stream", true)

        if (extraBody.isNotBlank()) {
            try {
                val eb = JSONObject(extraBody)
                val keys = eb.keys()
                while (keys.hasNext()) { val k = keys.next(); bodyObj.put(k, eb.get(k)) }
            } catch (_: Exception) {  }
        }
        val body = bodyObj.toString()

        val builder = Request.Builder()
            .url("${baseUrl.trim().trimEnd('/')}/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(body.toRequestBody("application/json".toMediaType()))
        parseHeaders(extraHeaders).forEach { (k, v) -> builder.addHeader(k, v) }
        val req = builder.build()

        val content = StringBuilder()
        val tools = HashMap<Int, Array<Any>>()

        val call = http.newCall(req)
        currentCall = call
        call.execute().use { r ->
            if (!r.isSuccessful) {
                val err = r.body?.string()?.take(300) ?: ""
                throw RuntimeException("HTTP ${r.code}: $err")
            }
            val source = r.body!!.source()
            while (true) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.substring(5).trim()
                if (payload == "[DONE]") break
                val delta = JSONObject(payload)
                    .getJSONArray("choices").getJSONObject(0).optJSONObject("delta") ?: continue

                delta.optString("content", "").takeIf { it.isNotEmpty() }?.let {
                    content.append(it)
                    emit(AgentEvent(AgentEvent.Kind.STREAM_DELTA, it))
                }
                delta.optJSONArray("tool_calls")?.let { tcs ->
                    for (j in 0 until tcs.length()) {
                        val tc = tcs.getJSONObject(j)
                        val idx = tc.optInt("index", 0)
                        val slot = tools.getOrPut(idx) { arrayOf("", "", StringBuilder()) }
                        tc.optString("id", "").takeIf { it.isNotEmpty() }?.let { slot[0] = it }
                        val fn = tc.optJSONObject("function")
                        fn?.optString("name", "")?.takeIf { it.isNotEmpty() }?.let { slot[1] = it }
                        fn?.optString("arguments", "")?.takeIf { it.isNotEmpty() }?.let {
                            (slot[2] as StringBuilder).append(it)
                        }
                    }
                }
            }
        }

        val msg = JSONObject().put("role", "assistant").put("content", content.toString())
        if (tools.isNotEmpty()) {
            val arr = JSONArray()
            tools.toSortedMap().forEach { (_, slot) ->
                arr.put(
                    JSONObject().put("id", slot[0]).put("type", "function").put(
                        "function",
                        JSONObject().put("name", slot[1])
                            .put("arguments", (slot[2] as StringBuilder).toString())
                    )
                )
            }
            msg.put("tool_calls", arr)
        }
        return msg
    }

    private fun parseHeaders(raw: String): List<Pair<String, String>> =
        uniffi.skyla_core.parseHeaders(raw).map { it.name to it.value }

    private fun toolsSpec(): JSONArray {
        val execChroot = JSONObject().put("type", "function").put(
            "function",
            JSONObject().put("name", "exec_chroot")
                .put("description", "Run a non-interactive shell command inside the Kali chroot and return its output.")
                .put(
                    "parameters",
                    JSONObject().put("type", "object")
                        .put("properties", JSONObject().put(
                            "command", JSONObject().put("type", "string").put("description", "The shell command to run")
                        ))
                        .put("required", JSONArray().put("command"))
                )
        )
        val appAction = JSONObject().put("type", "function").put(
            "function",
            JSONObject().put("name", "app_action")
                .put("description", "Control the Reforge app UI: navigate between screens, show a toast, or read status.")
                .put(
                    "parameters",
                    JSONObject().put("type", "object")
                        .put("properties", JSONObject()
                            .put("action", JSONObject().put("type", "string")
                                .put("enum", JSONArray().put("navigate").put("toast").put("get_status")))
                            .put("target", JSONObject().put("type", "string")
                                .put("description", "For navigate: home | agent | tools | settings"))
                            .put("message", JSONObject().put("type", "string")
                                .put("description", "For toast: the text to show")))
                        .put("required", JSONArray().put("action"))
                )
        )
        return JSONArray().put(execChroot).put(appAction)
    }
}
