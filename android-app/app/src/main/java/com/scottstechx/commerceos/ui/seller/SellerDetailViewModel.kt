package com.scottstechx.commerceos.ui.seller

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scottstechx.commerceos.data.ScottsTechXRepository
import com.scottstechx.commerceos.data.auth.AuthStore
import com.scottstechx.commerceos.data.remote.ApiResult
import com.scottstechx.commerceos.data.remote.dto.SellerDetailDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SellerDetailUiState(
    val isLoading: Boolean = true,
    val seller: SellerDetailDto? = null,
    val error: String? = null
)

@HiltViewModel
class SellerDetailViewModel @Inject constructor(
    private val repository: ScottsTechXRepository,
    private val authStore: AuthStore,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val sellerId: String = savedStateHandle.get<String>("sellerId") ?: ""
    private val _state = MutableStateFlow(SellerDetailUiState())
    val state: StateFlow<SellerDetailUiState> = _state.asStateFlow()

    init {
        if (sellerId.isNotBlank()) load()
    }

    fun load() {
        val token = authStore.currentToken ?: return
        if (sellerId.isBlank()) {
            _state.update { it.copy(isLoading = false, error = "Missing seller id") }
            return
        }
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val res = repository.getSeller(token, sellerId)) {
                is ApiResult.Success -> _state.update {
                    it.copy(isLoading = false, seller = res.value)
                }
                is ApiResult.HttpError -> _state.update {
                    it.copy(
                        isLoading = false,
                        error = if (res.code == 404) "Seller not found"
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
