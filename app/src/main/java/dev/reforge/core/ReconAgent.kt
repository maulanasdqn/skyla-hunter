package dev.reforge.core

import org.json.JSONArray
import org.json.JSONObject

data class AgentEvent(val kind: Kind, val text: String) {
    enum class Kind {
        STREAM_DELTA, STREAM_END, COMMAND, OUTPUT, OUTPUT_DELTA, OUTPUT_END,
        ANSWER, ERROR, RUNINFO,
    }
}

class ReconAgent(
    private val apiKey: String,
    private val model: String,

    private val confirm: (String) -> Boolean = { false },

    private val baseUrl: String = "https://api.deepseek.com",

    private val extraHeaders: String = "",
    private val extraBody: String = "",
) {
    private val client = ReconClient(apiKey, model, baseUrl, extraHeaders, extraBody)

    @Volatile private var cancelled = false

    fun cancel() {
        cancelled = true
        client.cancel()
        RootShell.cancelProc()
    }

    private val systemPrompt = """
        You are Reforge's reconnaissance agent on a Kali NetHunter phone.
        You help with AUTHORIZED reconnaissance only. You have two tools:
        - exec_chroot(command): run a non-interactive command in the Kali chroot
          (rustscan, nmap, subfinder, amass, sublist3r, whatweb, dnsenum, dig, host,
          whois, theHarvester, wafw00f, nikto, feroxbuster, ffuf, sherlock, holehe,
          maigret, phoneinfoga, exiftool, finalrecon, fierce, dnstwist, curl).
        - app_action(action, target, message): control the Reforge app.
          action="navigate" target in {home, agent, tools, settings};
          action="toast" message="...";
          action="get_status" (returns root + chroot status).
        Guidance:
        - For host/IP port recon, prefer: rustscan -a <t> --ulimit 5000 -b 2000 --no-config -- -sV
        - Always pass non-interactive flags; never run interactive/long-blocking commands.
        - Read each result, then decide the next step. Summarize findings in concise markdown.
    """.trimIndent()

    private fun isDangerous(cmd: String): Boolean = uniffi.skyla_core.commandIsDangerous(cmd)

    private val usedTools = LinkedHashSet<String>()

    private fun emitRunInfo(emit: (AgentEvent) -> Unit) {
        if (usedTools.isNotEmpty()) {
            emit(AgentEvent(AgentEvent.Kind.RUNINFO, usedTools.joinToString(",")))
        }
    }

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
                client.stream(messages, emit)
            } catch (e: Exception) {
                emit(AgentEvent(AgentEvent.Kind.STREAM_END, ""))
                if (cancelled) { emit(AgentEvent(AgentEvent.Kind.ERROR, "Stopped.")); emitRunInfo(emit); return finalAnswer }
                emit(AgentEvent(AgentEvent.Kind.ERROR, "API error: ${e.message}"))
                emitRunInfo(emit)
                return "API error: ${e.message}"
            }
            emit(AgentEvent(AgentEvent.Kind.STREAM_END, ""))
            messages.put(msg)

            val toolCalls = msg.optJSONArray("tool_calls")
            if (toolCalls == null || toolCalls.length() == 0) {
                finalAnswer = msg.optString("content", "").trim()
                emitRunInfo(emit)
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
        if (cancelled) { emit(AgentEvent(AgentEvent.Kind.ERROR, "Stopped.")); emitRunInfo(emit); return finalAnswer }

        messages.put(
            JSONObject().put("role", "user").put(
                "content",
                "You've reached the tool-call budget. Do NOT call any more tools. " +
                    "Summarize your findings so far for the user in concise markdown."
            )
        )
        val summary = try {
            client.stream(messages, emit, "none")
        } catch (e: Exception) {
            emit(AgentEvent(AgentEvent.Kind.STREAM_END, ""))
            emitRunInfo(emit)
            return finalAnswer
        }
        emit(AgentEvent(AgentEvent.Kind.STREAM_END, ""))
        emitRunInfo(emit)
        return summary.optString("content", "").trim().ifBlank { finalAnswer }
    }

    private companion object { const val MAX_STEPS = 16 }

    private fun execChroot(command: String, emit: (AgentEvent) -> Unit): String {
        if (command.isBlank()) return "error: empty command"
        if (isDangerous(command)) {

            if (cancelled) return "cancelled"
            val ok = confirm(command)
            if (!ok) {
                emit(AgentEvent(AgentEvent.Kind.ERROR, "Skipped by you: $command"))
                return "User declined to run this command. Choose a safer approach."
            }
            emit(AgentEvent(AgentEvent.Kind.ERROR, "Approved by you — running: $command"))
        }
        usedTools.addAll(uniffi.skyla_core.detectTools(command))
        emit(AgentEvent(AgentEvent.Kind.COMMAND, command))
        var any = false
        val out = RootShell.chrootStream(command) { chunk ->
            any = true
            emit(AgentEvent(AgentEvent.Kind.OUTPUT_DELTA, chunk))
        }
        if (!any) emit(AgentEvent(AgentEvent.Kind.OUTPUT_DELTA, "(no output)"))
        emit(AgentEvent(AgentEvent.Kind.OUTPUT_END, ""))
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

}
