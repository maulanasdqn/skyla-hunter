package dev.reforge.ui.screens

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.reforge.core.AgentEvent
import dev.reforge.core.ReconAgent
import dev.reforge.core.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class MsgKind { USER, THOUGHT, COMMAND, OUTPUT, ANSWER, ERROR }

data class ChatMessage(
    val id: Long,
    val kind: MsgKind,
    val text: String,
)

data class AgentState(
    val messages: List<ChatMessage> = listOf(
        ChatMessage(
            0,
            MsgKind.ANSWER,
            "Hi — I'm the **Recon Agent**, powered by DeepSeek.\n\n" +
                "Give me a target or a goal, for example:\n" +
                "- `scan scanme.nmap.org`\n" +
                "- find subdomains of `example.com`\n\n" +
                "Set your DeepSeek key in **Settings** first.",
        )
    ),
    val busy: Boolean = false,
    val streaming: Boolean = false,
)

class AgentViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = mutableStateOf(AgentState())
    val state: State<AgentState> = _state

    private val settings = Settings(app)
    private val history = mutableListOf<JSONObject>()
    private var seq = 1L
    private var streamingId: Long? = null
    private var agent: ReconAgent? = null
    private var job: Job? = null

    fun send(text: String) {
        append(MsgKind.USER, text)
        val key = settings.deepSeekKey
        if (key.isBlank()) {
            append(MsgKind.ERROR, "No DeepSeek API key set. Open **Settings** and add your key.")
            return
        }
        _state.value = _state.value.copy(busy = true)
        job = viewModelScope.launch {
            val agent = ReconAgent(key, settings.model).also { this@AgentViewModel.agent = it }
            val prior = history.toList()
            val answer = withContext(Dispatchers.IO) {
                agent.run(text, prior) { ev -> onEvent(ev) }
            }
            history.add(JSONObject().put("role", "user").put("content", text))
            if (answer.isNotBlank()) {
                history.add(JSONObject().put("role", "assistant").put("content", answer))
            }
            _state.value = _state.value.copy(busy = false, streaming = false)
            streamingId = null
        }
    }

    fun stop() {
        agent?.cancel()
        job?.cancel()
        _state.value = _state.value.copy(busy = false, streaming = false)
        streamingId = null
    }

    fun clear() {
        stop()
        history.clear()
        _state.value = AgentState()
    }

    private fun onEvent(ev: AgentEvent) {
        viewModelScope.launch(Dispatchers.Main) {
            when (ev.kind) {
                AgentEvent.Kind.STREAM_DELTA -> appendDelta(ev.text)
                AgentEvent.Kind.STREAM_END -> {
                    streamingId = null
                    _state.value = _state.value.copy(streaming = false)
                }
                AgentEvent.Kind.COMMAND -> { streamingId = null; append(MsgKind.COMMAND, "$ ${ev.text}") }
                AgentEvent.Kind.OUTPUT -> { streamingId = null; append(MsgKind.OUTPUT, ev.text) }
                AgentEvent.Kind.ANSWER -> { streamingId = null; append(MsgKind.ANSWER, ev.text) }
                AgentEvent.Kind.ERROR -> { streamingId = null; append(MsgKind.ERROR, ev.text) }
            }
        }
    }

    /** Append a streamed token: start a new answer bubble, or grow the active one. */
    private fun appendDelta(delta: String) {
        val id = streamingId
        if (id == null) {
            val msg = ChatMessage(seq++, MsgKind.ANSWER, delta)
            streamingId = msg.id
            _state.value = _state.value.copy(
                messages = _state.value.messages + msg,
                streaming = true,
            )
        } else {
            val updated = _state.value.messages.map {
                if (it.id == id) it.copy(text = it.text + delta) else it
            }
            _state.value = _state.value.copy(messages = updated)
        }
    }

    private fun append(kind: MsgKind, text: String) {
        _state.value = _state.value.copy(
            messages = _state.value.messages + ChatMessage(seq++, kind, text)
        )
    }
}
