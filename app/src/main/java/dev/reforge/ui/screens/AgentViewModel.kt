package dev.reforge.ui.screens

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.reforge.core.AgentEvent
import dev.reforge.core.ContextManager
import dev.reforge.core.ReconAgent
import dev.reforge.core.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class MsgKind { USER, THOUGHT, COMMAND, OUTPUT, ANSWER, ERROR, RUNINFO }

data class ChatMessage(
    val id: Long,
    val kind: MsgKind,
    val text: String,
)

data class AgentState(
    val messages: List<ChatMessage> = emptyList(),
    val busy: Boolean = false,
    val streaming: Boolean = false,
    val title: String = "New chat",
    val contextTokens: Int = 0,
    val sessions: List<SessionMeta> = emptyList(),
    // A denylisted command awaiting the user's Run anyway / Skip decision.
    val pendingConfirm: String? = null,
)

private const val GREETING =
    "Hi — I'm the **Recon Agent**, powered by DeepSeek.\n\n" +
        "Give me a target or a goal, for example:\n" +
        "- `scan scanme.nmap.org`\n" +
        "- find subdomains of `example.com`\n\n" +
        "Set your DeepSeek key in **Settings** first."

class AgentViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = mutableStateOf(AgentState())
    val state: State<AgentState> = _state

    private val settings = Settings(app)
    private val store = ChatStore(app)

    private lateinit var current: Session
    private var seq = 1L
    private var streamingId: Long? = null
    private var outputId: Long? = null
    private var agent: ReconAgent? = null
    private var job: Job? = null

    // Hands the user's Run anyway / Skip decision from the UI thread back to the
    // blocked agent worker thread.
    private val confirmChannel = java.util.concurrent.LinkedBlockingQueue<Boolean>()

    init {
        val metas = store.list()
        current = if (metas.isNotEmpty()) store.load(metas.first().id) ?: freshSession() else freshSession()
        seq = (current.ui.maxOfOrNull { it.id } ?: 0L) + 1
        pushState()
    }

    // ---- session management ----

    fun newChat() {
        saveCurrent()
        current = freshSession()
        seq = 1L
        store.save(current)
        pushState()
    }

    fun openSession(id: String) {
        if (id == current.id) return
        saveCurrent()
        current = store.load(id) ?: return
        seq = (current.ui.maxOfOrNull { it.id } ?: 0L) + 1
        pushState()
    }

    fun deleteSession(id: String) {
        store.delete(id)
        if (id == current.id) {
            val metas = store.list()
            current = if (metas.isNotEmpty()) store.load(metas.first().id) ?: freshSession() else freshSession()
            seq = (current.ui.maxOfOrNull { it.id } ?: 0L) + 1
        }
        pushState()
    }

    private fun freshSession(): Session = Session(
        id = store.newId(),
        title = "New chat",
        updatedAt = System.currentTimeMillis(),
        ui = mutableListOf(ChatMessage(0, MsgKind.ANSWER, GREETING)),
        turns = mutableListOf(),
    )

    // ---- chat ----

    fun send(text: String) {
        append(MsgKind.USER, text)
        if (current.title == "New chat") current.title = text.take(40)

        val key = settings.deepSeekKey
        if (key.isBlank()) {
            append(MsgKind.ERROR, "No DeepSeek API key set. Open **Settings** and add your key.")
            return
        }
        _state.value = _state.value.copy(busy = true)
        confirmChannel.clear()
        job = viewModelScope.launch {
            val agent = ReconAgent(
                key, settings.model, confirm = ::awaitConfirm, baseUrl = settings.baseUrl,
                extraHeaders = settings.customHeaders, extraBody = settings.extraBody,
            ).also { this@AgentViewModel.agent = it }
            val windowed = ContextManager.window(current.turns).first
            val answer = withContext(Dispatchers.IO) {
                agent.run(text, windowed) { ev -> onEvent(ev) }
            }
            current.turns.add(JSONObject().put("role", "user").put("content", text))
            if (answer.isNotBlank()) {
                current.turns.add(JSONObject().put("role", "assistant").put("content", answer))
            }
            _state.value = _state.value.copy(busy = false, streaming = false)
            streamingId = null
            outputId = null
            saveCurrent()
        }
    }

    fun stop() {
        if (_state.value.pendingConfirm != null) confirmChannel.offer(false)
        agent?.cancel()
        job?.cancel()
        _state.value = _state.value.copy(busy = false, streaming = false, pendingConfirm = null)
        streamingId = null
        outputId = null
        saveCurrent()
    }

    /** Called from the agent worker thread; blocks until the user decides. */
    private fun awaitConfirm(cmd: String): Boolean {
        viewModelScope.launch(Dispatchers.Main) {
            _state.value = _state.value.copy(pendingConfirm = cmd)
        }
        val ok = try { confirmChannel.take() } catch (e: InterruptedException) { false }
        viewModelScope.launch(Dispatchers.Main) {
            _state.value = _state.value.copy(pendingConfirm = null)
        }
        return ok
    }

    /** UI taps Run anyway (true) / Skip (false). */
    fun resolveConfirm(run: Boolean) {
        _state.value = _state.value.copy(pendingConfirm = null)
        confirmChannel.offer(run)
    }

    // ---- internals ----

    private fun onEvent(ev: AgentEvent) {
        viewModelScope.launch(Dispatchers.Main) {
            when (ev.kind) {
                AgentEvent.Kind.STREAM_DELTA -> appendDelta(ev.text)
                AgentEvent.Kind.STREAM_END -> {
                    streamingId = null
                    _state.value = _state.value.copy(streaming = false)
                }
                AgentEvent.Kind.COMMAND -> { streamingId = null; outputId = null; append(MsgKind.COMMAND, "$ ${ev.text}") }
                AgentEvent.Kind.OUTPUT_DELTA -> { streamingId = null; appendOutputDelta(ev.text) }
                AgentEvent.Kind.OUTPUT_END -> { outputId = null }
                AgentEvent.Kind.OUTPUT -> { streamingId = null; outputId = null; append(MsgKind.OUTPUT, ev.text) }
                AgentEvent.Kind.ANSWER -> { streamingId = null; outputId = null; append(MsgKind.ANSWER, ev.text) }
                AgentEvent.Kind.ERROR -> { streamingId = null; outputId = null; append(MsgKind.ERROR, ev.text) }
                AgentEvent.Kind.RUNINFO -> { streamingId = null; outputId = null; append(MsgKind.RUNINFO, ev.text) }
            }
        }
    }

    private fun appendDelta(delta: String) {
        val id = streamingId
        if (id == null) {
            val msg = ChatMessage(seq++, MsgKind.ANSWER, delta)
            current.ui.add(msg)
            streamingId = msg.id
            _state.value = _state.value.copy(messages = current.ui.toList(), streaming = true)
        } else {
            val idx = current.ui.indexOfLast { it.id == id }
            if (idx >= 0) {
                current.ui[idx] = current.ui[idx].copy(text = current.ui[idx].text + delta)
                _state.value = _state.value.copy(messages = current.ui.toList())
            }
        }
    }

    /** Grow the live tool-output bubble, keeping only the tail so huge scans stay light. */
    private fun appendOutputDelta(delta: String) {
        val id = outputId
        if (id == null) {
            val msg = ChatMessage(seq++, MsgKind.OUTPUT, delta)
            current.ui.add(msg)
            outputId = msg.id
            _state.value = _state.value.copy(messages = current.ui.toList())
        } else {
            val idx = current.ui.indexOfLast { it.id == id }
            if (idx >= 0) {
                var t = current.ui[idx].text + delta
                if (t.length > 8000) t = "…\n" + t.takeLast(8000)
                current.ui[idx] = current.ui[idx].copy(text = t)
                _state.value = _state.value.copy(messages = current.ui.toList())
            }
        }
    }

    private fun append(kind: MsgKind, text: String) {
        current.ui.add(ChatMessage(seq++, kind, text))
        pushState()
    }

    private fun saveCurrent() {
        current.updatedAt = System.currentTimeMillis()
        store.save(current)
        pushState()
    }

    private fun pushState() {
        _state.value = _state.value.copy(
            messages = current.ui.toList(),
            title = current.title,
            contextTokens = ContextManager.estimateTokens(current.turns),
            sessions = store.list(),
        )
    }
}
