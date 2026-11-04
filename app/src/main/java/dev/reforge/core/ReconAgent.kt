package dev.reforge.core

import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** One event surfaced to the UI as the agent works. */
data class AgentEvent(val kind: Kind, val text: String) {
    enum class Kind { STREAM_DELTA, STREAM_END, COMMAND, OUTPUT, ANSWER, ERROR }
}

/**
 * DeepSeek-powered recon agent with a streaming function-calling loop and two
 * "hands":
 *  - exec_chroot: run recon commands inside the Kali chroot (allowlisted).
 *  - app_action : drive the Reforge app itself (navigate, toast, get_status).
 * Streams its text token-by-token and can be cancelled mid-run.
 */
class ReconAgent(
    private val apiKey: String,
    private val model: String,
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    @Volatile private var cancelled = false
    @Volatile private var currentCall: Call? = null

    fun cancel() {
        cancelled = true
        currentCall?.cancel()
    }

    private val systemPrompt = """
        You are Reforge's reconnaissance agent on a Kali NetHunter phone.
        You help with AUTHORIZED reconnaissance only. You have two tools:
        - exec_chroot(command): run a non-interactive command in the Kali chroot
          (rustscan, nmap, subfinder, amass, sublist3r, whatweb, dnsenum, dig, host,
          whois, theHarvester, wafw00f, nikto, feroxbuster, ffuf, sherlock, holehe,
          finalrecon, fierce, dnstwist, curl).
        - app_action(action, target, message): control the Reforge app.
          action="navigate" target in {home, agent, tools, settings};
          action="toast" message="...";
          action="get_status" (returns root + chroot status).
        Guidance:
        - For host/IP port recon, prefer: rustscan -a <t> --ulimit 5000 -b 2000 --no-config -- -sV
        - Always pass non-interactive flags; never run interactive/long-blocking commands.
        - Read each result, then decide the next step. Summarize findings in concise markdown.
    """.trimIndent()

    // Denylist of clearly destructive / dangerous patterns. Everything else is
    // allowed (this is the user's own authorized device). Guards against a
    // malicious scan target injecting a destructive command into the loop.
    private val denyPatterns = listOf(
        Regex("""\brm\s+-\w*[rf]"""), Regex("""\bdd\b[^|]*\bof="""), Regex("""\bmkfs\b"""),
        Regex("""\bshred\b"""), Regex("""\bfdisk\b"""), Regex("""\bparted\b"""),
        Regex("""\bwipefs\b"""), Regex("""\bmkswap\b"""),
        Regex("""\b(reboot|shutdown|poweroff|halt)\b"""),
        Regex(""">\s*/dev/(sd|block|mmc|null/)"""), Regex("""\bof=/dev/"""),
        Regex(""":\s*\(\s*\)\s*\{"""), // fork bomb
        Regex("""\buserdel\b"""), Regex("""\b(passwd|chpasswd)\b"""),
        Regex("""\bchmod\s+-R\s+777\s+/"""), Regex("""\bmv\s+/\s"""),
    )

    private fun isDangerous(cmd: String): Boolean = denyPatterns.any { it.containsMatchIn(cmd) }

    fun run(userGoal: String, history: List<JSONObject>, emit: (AgentEvent) -> Unit): String {
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
        history.forEach { messages.put(it) }
        messages.put(JSONObject().put("role", "user").put("content", userGoal))

        var finalAnswer = ""
        var steps = 0
        while (steps < MAX_STEPS && !cancelled) {
            steps++
            val msg = try {
                stream(messages, emit)
            } catch (e: Exception) {
                emit(AgentEvent(AgentEvent.Kind.STREAM_END, ""))
                if (cancelled) { emit(AgentEvent(AgentEvent.Kind.ERROR, "Stopped.")); return finalAnswer }
                emit(AgentEvent(AgentEvent.Kind.ERROR, "API error: ${e.message}"))
                return "API error: ${e.message}"
            }
            emit(AgentEvent(AgentEvent.Kind.STREAM_END, ""))
            messages.put(msg)

            val toolCalls = msg.optJSONArray("tool_calls")
            if (toolCalls == null || toolCalls.length() == 0) {
                finalAnswer = msg.optString("content", "").trim()
                return finalAnswer
            }
            for (i in 0 until toolCalls.length()) {
                if (cancelled) break
                val call = toolCalls.getJSONObject(i)
                val fn = call.getJSONObject("function")
                val name = fn.optString("name", "")
                val args = JSONObject(fn.optString("arguments", "{}"))
                val result = when (name) {
                    "exec_chroot" -> execChroot(args.optString("command", "").trim(), emit)
                    "app_action" -> appAction(args, emit)
                    else -> "unknown tool: $name"
                }
                messages.put(
                    JSONObject().put("role", "tool")
                        .put("tool_call_id", call.getString("id"))
                        .put("content", result)
                )
            }
        }
        if (cancelled) { emit(AgentEvent(AgentEvent.Kind.ERROR, "Stopped.")); return finalAnswer }

        // Step budget hit: force a final, tool-free summary so the user always
        // gets a conclusion instead of a dead-end "step limit" message.
        messages.put(
            JSONObject().put("role", "user").put(
                "content",
                "You've reached the tool-call budget. Do NOT call any more tools. " +
                    "Summarize your findings so far for the user in concise markdown."
            )
        )
        val summary = try {
            stream(messages, emit, "none")
        } catch (e: Exception) {
            emit(AgentEvent(AgentEvent.Kind.STREAM_END, ""))
            return finalAnswer
        }
        emit(AgentEvent(AgentEvent.Kind.STREAM_END, ""))
        return summary.optString("content", "").trim().ifBlank { finalAnswer }
    }

    private companion object { const val MAX_STEPS = 16 }

    private fun execChroot(command: String, emit: (AgentEvent) -> Unit): String {
        if (command.isBlank()) return "error: empty command"
        if (isDangerous(command)) {
            emit(AgentEvent(AgentEvent.Kind.ERROR, "blocked (potentially destructive): $command"))
            return "blocked: command looks destructive; ask the user to run it manually."
        }
        emit(AgentEvent(AgentEvent.Kind.COMMAND, command))
        val out = RootShell.chroot("$command 2>&1 | head -c 12000")
        emit(AgentEvent(AgentEvent.Kind.OUTPUT, out.ifBlank { "(no output)" }))
        return out.ifBlank { "(no output)" }
    }

    private fun appAction(args: JSONObject, emit: (AgentEvent) -> Unit): String {
        val action = args.optString("action", "")
        val target = args.optString("target", "")
        val message = args.optString("message", "")
        emit(AgentEvent(AgentEvent.Kind.COMMAND, "app ▸ $action ${target.ifBlank { message }}".trim()))
        val r = AppController.handle(action, target, message)
        emit(AgentEvent(AgentEvent.Kind.OUTPUT, r))
        return r
    }

    private fun stream(
        messages: JSONArray,
        emit: (AgentEvent) -> Unit,
        toolChoice: String = "auto",
    ): JSONObject {
        val body = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("tools", toolsSpec())
            .put("tool_choice", toolChoice)
            .put("temperature", 0.2)
            .put("stream", true)
            .toString()

        val req = Request.Builder()
            .url("https://api.deepseek.com/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

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
