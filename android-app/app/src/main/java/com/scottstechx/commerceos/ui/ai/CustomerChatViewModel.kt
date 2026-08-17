package com.scottstechx.commerceos.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scottstechx.commerceos.data.ScottsTechXRepository
import com.scottstechx.commerceos.data.auth.AuthStore
import com.scottstechx.commerceos.data.remote.ApiResult
import com.scottstechx.commerceos.data.remote.dto.AiChatHistoryMessage
import com.scottstechx.commerceos.data.remote.dto.AiCustomerChatRequest
import com.scottstechx.commerceos.data.remote.dto.AiStatusResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ChatUiMessage(
    val id: String,
    val role: String,    // "user" | "ai"
    val content: String,
    val pending: Boolean = false
)

data class CustomerChatUiState(
    val aiEnabled: Boolean = false,
    val aiProvider: String? = null,
    val messages: List<ChatUiMessage> = emptyList(),
    val isSending: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class CustomerChatViewModel @Inject constructor(
    private val repository: ScottsTechXRepository,
    private val authStore: AuthStore
) : ViewModel() {

    private val _state = MutableStateFlow(CustomerChatUiState())
    val state: StateFlow<CustomerChatUiState> = _state.asStateFlow()

    // sessionId = userId (or anonymous UUID if not signed in) — gives the
    // backend a stable per-user chat bucket.
    private val sessionId: String =
        authStore.state.value.userId ?: UUID.randomUUID().toString()

    init {
        viewModelScope.launch {
            when (val res = repository.aiStatus()) {
                is ApiResult.Success -> _state.update {
                    it.copy(aiEnabled = (res.value as AiStatusResponse).enabled, aiProvider = res.value.provider)
                }
                else -> Unit
            }
        }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _state.value.isSending) return
        val token = authStore.currentToken ?: run {
            _state.update { it.copy(error = "Please sign in to chat") }
            return
        }
        val userMsg = ChatUiMessage(
            id = UUID.randomUUID().toString(),
            role = "user",
            content = trimmed
        )
        val pendingAi = ChatUiMessage(
            id = UUID.randomUUID().toString(),
            role = "ai",
            content = "...",
            pending = true
        )
        _state.update {
            it.copy(
                messages = it.messages + userMsg + pendingAi,
                isSending = true,
                error = null
            )
        }
        // Build the last-20 history for the backend context.
        val history = _state.value.messages
            .filter { !it.pending }
            .takeLast(20)
            .map { AiChatHistoryMessage(role = it.role, content = it.content) }
        viewModelScope.launch {
            val res = repository.aiCustomerChat(
                token,
                AiCustomerChatRequest(
                    sessionId = sessionId,
                    message = trimmed,
                    history = history
                )
            )
            when (res) {
                is ApiResult.Success -> _state.update {
                    val updated = it.messages.dropLast(1) +
                        pendingAi.copy(content = res.value.reply, pending = false)
                    it.copy(messages = updated, isSending = false)
                }
                is ApiResult.HttpError -> _state.update {
                    val updated = it.messages.dropLast(1) +
                        pendingAi.copy(
                            content = if (res.code == 503)
                                "AI is not configured on the server yet."
                            else "Server error (${res.code})",
                            pending = false
                        )
                    it.copy(messages = updated, isSending = false)
                }
                is ApiResult.NetworkError -> _state.update {
                    val updated = it.messages.dropLast(1) +
                        pendingAi.copy(content = "Network error", pending = false)
                    it.copy(messages = updated, isSending = false)
                }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}
