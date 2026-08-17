package com.scottstechx.commerceos.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scottstechx.commerceos.data.ScottsTechXRepository
import com.scottstechx.commerceos.data.auth.AuthStore
import com.scottstechx.commerceos.data.remote.ApiResult
import com.scottstechx.commerceos.data.remote.dto.AiSellerSuggestRequest
import com.scottstechx.commerceos.data.remote.dto.AiSellerSuggestResponse
import com.scottstechx.commerceos.data.remote.dto.AiStatusResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SuggestionType(val wire: String, val display: String, val placeholder: String) {
    PRODUCT_DESCRIPTION(
        wire = "product_description",
        display = "Write a product description",
        placeholder = "e.g. Bark cloth tote, handwoven in Kampala"
    ),
    AUTO_PRICE(
        wire = "auto_price",
        display = "Suggest a price",
        placeholder = "e.g. Bark cloth tote, similar items sell for 25,000 UGX"
    ),
    CATEGORY(
        wire = "category",
        display = "Pick a category",
        placeholder = "e.g. Bark cloth tote bag"
    ),
    INVENTORY_WARNING(
        wire = "inventory_warning",
        display = "Inventory check",
        placeholder = "e.g. 25 tote bags in stock, last 30 days"
    )
}

data class SuggestionDraft(
    val type: SuggestionType,
    val input: String,
    val result: AiSellerSuggestResponse? = null
)

data class SellerAssistantUiState(
    val aiEnabled: Boolean = false,
    val aiProvider: String? = null,
    val isLoading: Boolean = true,
    val current: SuggestionDraft? = null,
    val history: List<SuggestionDraft> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class SellerAssistantViewModel @Inject constructor(
    private val repository: ScottsTechXRepository,
    private val authStore: AuthStore
) : ViewModel() {

    private val _state = MutableStateFlow(SellerAssistantUiState())
    val state: StateFlow<SellerAssistantUiState> = _state.asStateFlow()

    init {
        checkStatus()
    }

    private fun checkStatus() {
        viewModelScope.launch {
            val res = repository.aiStatus()
            when (res) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        aiEnabled = res.value.enabled,
                        aiProvider = res.value.provider,
                        isLoading = false
                    )
                }
                is ApiResult.HttpError, is ApiResult.NetworkError -> _state.update {
                    it.copy(isLoading = false)
                }
            }
        }
    }

    fun startDraft(type: SuggestionType) {
        _state.update { it.copy(current = SuggestionDraft(type, ""), error = null) }
    }

    fun cancelDraft() {
        _state.update { it.copy(current = null, error = null) }
    }

    fun updateDraftInput(value: String) {
        _state.update { it.copy(current = it.current?.copy(input = value)) }
    }

    fun submitDraft() {
        val draft = _state.value.current ?: return
        val token = authStore.currentToken ?: return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val res = repository.aiSellerSuggest(
                token,
                AiSellerSuggestRequest(
                    type = draft.type.wire,
                    draft = mapOf("text" to draft.input)
                )
            )
            when (res) {
                is ApiResult.Success -> {
                    val updated = draft.copy(result = res.value)
                    _state.update {
                        it.copy(
                            isLoading = false,
                            current = null,
                            history = listOf(updated) + it.history
                        )
                    }
                }
                is ApiResult.HttpError -> _state.update {
                    it.copy(
                        isLoading = false,
                        error = if (res.code == 503) "AI is not configured on the server yet"
                        else "Server error (${res.code})"
                    )
                }
                is ApiResult.NetworkError -> _state.update {
                    it.copy(isLoading = false, error = "Network error")
                }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}
