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

    val pendingConfirm: String? = null,
)

private const val GREETING =
    "Hi — I'm the **Recon Agent**, powered by DeepSeek.\n\n" +
        "Give me a target or a goal, for example:\n" +
        "- `scan scanme.nmap.org`\n" +
        "- find subdomains of `example.com`\n\n" +
        "Set your DeepSeek key in **Settings** first."


fun freshSession(store: ChatStore): Session = Session(
    id = store.newId(),
    title = "New chat",
    updatedAt = System.currentTimeMillis(),
    ui = mutableListOf(ChatMessage(0, MsgKind.ANSWER, GREETING)),
    turns = mutableListOf(),
)
