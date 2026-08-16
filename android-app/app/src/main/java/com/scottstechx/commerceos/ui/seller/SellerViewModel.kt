package com.scottstechx.commerceos.ui.seller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scottstechx.commerceos.data.ScottsTechXRepository
import com.scottstechx.commerceos.data.auth.AuthStore
import com.scottstechx.commerceos.data.remote.ApiResult
import com.scottstechx.commerceos.data.remote.dto.CreateProductRequest
import com.scottstechx.commerceos.data.remote.dto.OrderResponse
import com.scottstechx.commerceos.data.remote.dto.ProductDto
import com.scottstechx.commerceos.data.remote.dto.SellerStatsDto
import com.scottstechx.commerceos.data.remote.dto.UpdateProductRequest
import com.scottstechx.commerceos.security.InputValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SellerUiState(
    val isLoading: Boolean = true,
    val products: List<ProductDto> = emptyList(),
    val orders: List<OrderResponse> = emptyList(),
    val stats: SellerStatsDto? = null,
    val error: String? = null,
    val showCreateDialog: Boolean = false,
    val editingProduct: ProductDto? = null,
    val isSaving: Boolean = false
)

@HiltViewModel
class SellerViewModel @Inject constructor(
    private val repository: ScottsTechXRepository,
    private val authStore: AuthStore
) : ViewModel() {

    private val _state = MutableStateFlow(SellerUiState())
    val state: StateFlow<SellerUiState> = _state.asStateFlow()

    init {
        refreshAll()
    }

    fun refreshAll() {
        val token = authStore.currentToken ?: return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val inv = repository.listInventory(token)
            val stats = repository.getSellerStats(token)
            val orders = repository.listSellerOrders(token)
            _state.update { current ->
                current.copy(
                    isLoading = false,
                    products = (inv as? ApiResult.Success)?.value ?: current.products,
                    stats = (stats as? ApiResult.Success)?.value ?: current.stats,
                    orders = (orders as? ApiResult.Success)?.value ?: current.orders,
                    error = firstError(inv, stats, orders) ?: current.error
                )
            }
        }
    }

    fun showCreateDialog() = _state.update { it.copy(showCreateDialog = true) }
    fun hideCreateDialog() = _state.update { it.copy(showCreateDialog = false) }
    fun startEdit(product: ProductDto) = _state.update { it.copy(editingProduct = product) }
    fun cancelEdit() = _state.update { it.copy(editingProduct = null) }

    fun createProduct(title: String, description: String, priceMinor: Long, stock: Int, imageUrl: String?) {
        // Defensive validation. We re-check here even though the UI
        // also gates on these — the repo entry is the trust boundary.
        val t = InputValidator.validateTitle(title)
        if (t is InputValidator.Result.Invalid) {
            _state.update { it.copy(error = t.reason) }; return
        }
        val d = InputValidator.validateDescription(description)
        if (d is InputValidator.Result.Invalid) {
            _state.update { it.copy(error = d.reason) }; return
        }
        val p = InputValidator.validatePriceMinor(priceMinor)
        if (p is InputValidator.Result.Invalid) {
            _state.update { it.copy(error = p.reason) }; return
        }
        val s = InputValidator.validateStock(stock)
        if (s is InputValidator.Result.Invalid) {
            _state.update { it.copy(error = s.reason) }; return
        }
        val token = authStore.currentToken ?: return
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val res = repository.createProduct(
                token,
                CreateProductRequest(
                    title = title.trim(),
                    description = description.trim(),
                    priceMinor = priceMinor,
                    stockQuantity = stock,
                    imageUrl = imageUrl?.trim()?.ifBlank { null }
                )
            )
            _state.update { it.copy(isSaving = false) }
            when (res) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(
                            products = listOf(res.value) + it.products,
                            showCreateDialog = false
                        )
                    }
                }
                is ApiResult.HttpError -> _state.update { it.copy(error = "Server error (${res.code})") }
                is ApiResult.NetworkError -> _state.update { it.copy(error = "Network error") }
            }
        }
    }

    fun updateProduct(
        productId: String,
        title: String? = null,
        description: String? = null,
        priceMinor: Long? = null,
        stock: Int? = null
    ) {
        val token = authStore.currentToken ?: return
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val res = repository.updateProduct(
                token,
                productId,
                UpdateProductRequest(
                    title = title?.trim()?.ifBlank { null },
                    description = description?.trim()?.ifBlank { null },
                    priceMinor = priceMinor,
                    stockQuantity = stock
                )
            )
            _state.update { it.copy(isSaving = false, editingProduct = null) }
            when (res) {
                is ApiResult.Success -> {
                    _state.update { current ->
                        current.copy(
                            products = current.products.map { p ->
                                if (p.id == productId) res.value else p
                            }
                        )
                    }
                }
                is ApiResult.HttpError -> _state.update { it.copy(error = "Server error (${res.code})") }
                is ApiResult.NetworkError -> _state.update { it.copy(error = "Network error") }
            }
        }
    }

    fun deleteProduct(productId: String) {
        val token = authStore.currentToken ?: return
        viewModelScope.launch {
            val res = repository.deleteProduct(token, productId)
            if (res is ApiResult.Success) {
                _state.update { current ->
                    current.copy(products = current.products.filterNot { it.id == productId })
                }
            } else {
                _state.update { it.copy(error = "Could not delete product") }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun signOut() {
        authStore.clear()
    }

    private fun firstError(vararg results: ApiResult<*>): String? {
        results.forEach { r ->
            if (r is ApiResult.NetworkError) return "Network error"
            if (r is ApiResult.HttpError) return "Server error (${r.code})"
        }
        return null
    }
}
