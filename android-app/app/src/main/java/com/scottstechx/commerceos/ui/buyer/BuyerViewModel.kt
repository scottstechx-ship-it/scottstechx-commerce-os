package com.scottstechx.commerceos.ui.buyer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scottstechx.commerceos.data.ScottsTechXRepository
import com.scottstechx.commerceos.data.auth.AuthStore
import com.scottstechx.commerceos.data.remote.ApiResult
import com.scottstechx.commerceos.data.remote.dto.CheckoutRequest
import com.scottstechx.commerceos.data.remote.dto.DeliveryAddress
import com.scottstechx.commerceos.data.remote.dto.OrderItemRequest
import com.scottstechx.commerceos.data.remote.dto.OrderResponse
import com.scottstechx.commerceos.data.remote.dto.ProductDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CartLine(val product: ProductDto, val qty: Int) {
    val lineTotalMinor: Long get() = product.priceMinor * qty
}

data class BuyerUiState(
    val products: List<ProductDto> = emptyList(),
    val cart: List<CartLine> = emptyList(),
    val isLoadingProducts: Boolean = false,
    val isCheckingOut: Boolean = false,
    val productsError: String? = null,
    val checkoutError: String? = null,
    val lastOrder: OrderResponse? = null,
    val showCart: Boolean = false,
    val isShowingCached: Boolean = false
) {
    val cartTotalMinor: Long get() = cart.sumOf { it.lineTotalMinor }
    val cartCount: Int get() = cart.sumOf { it.qty }
}

@HiltViewModel
class BuyerViewModel @Inject constructor(
    private val repository: ScottsTechXRepository,
    private val authStore: AuthStore
) : ViewModel() {

    private val _state = MutableStateFlow(BuyerUiState())
    val state: StateFlow<BuyerUiState> = _state.asStateFlow()

    init { warmStart() }

    /**
     * Cold-start: load the on-disk product cache immediately, then
     * fire a network refresh. The cache makes the screen render in
     * <100ms even before the network call returns.
     */
    private fun warmStart() {
        viewModelScope.launch {
            val cached = repository.readCachedProducts()
            if (cached.isNotEmpty()) {
                _state.update { it.copy(products = cached, isShowingCached = true) }
            }
            loadProducts()
        }
    }

    fun loadProducts() {
        val token = authStore.currentToken
        if (token.isNullOrBlank()) return
        _state.update { it.copy(isLoadingProducts = true, productsError = null) }
        viewModelScope.launch {
            when (val res = repository.listProducts(token)) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        isLoadingProducts = false,
                        products = res.value,
                        isShowingCached = false
                    )
                }
                is ApiResult.HttpError -> {
                    // Keep showing the cache if the network fails; just
                    // surface a soft error so the user can retry.
                    val msg = if (_state.value.products.isNotEmpty()) {
                        "Showing cached products. Refresh failed (${res.code})."
                    } else "Failed to load products (${res.code})"
                    _state.update {
                        it.copy(isLoadingProducts = false, productsError = msg)
                    }
                }
                is ApiResult.NetworkError -> {
                    val msg = if (_state.value.products.isNotEmpty()) {
                        "Showing cached products. Network unavailable."
                    } else "Network error. Backend reachable?"
                    _state.update {
                        it.copy(isLoadingProducts = false, productsError = msg)
                    }
                }
            }
        }
    }

    fun addToCart(product: ProductDto) {
        _state.update { current ->
            val existing = current.cart.find { it.product.id == product.id }
            val updated = if (existing != null) {
                current.cart.map {
                    if (it.product.id == product.id) it.copy(qty = it.qty + 1) else it
                }
            } else {
                current.cart + CartLine(product, 1)
            }
            current.copy(cart = updated)
        }
    }

    fun removeFromCart(productId: String) {
        _state.update { it.copy(cart = it.cart.filterNot { line -> line.product.id == productId }) }
    }

    fun setCartVisible(v: Boolean) = _state.update { it.copy(showCart = v) }
    fun dismissOrder() = _state.update { it.copy(lastOrder = null) }
    fun clearCheckoutError() = _state.update { it.copy(checkoutError = null) }
    fun clearProductsError() = _state.update { it.copy(productsError = null) }

    fun signOut() = authStore.clear()

    fun checkout(addressLine1: String, city: String) {
        val token = authStore.currentToken
        if (token.isNullOrBlank()) {
            _state.update { it.copy(checkoutError = "Not signed in") }
            return
        }
        val cart = _state.value.cart
        if (cart.isEmpty()) {
            _state.update { it.copy(checkoutError = "Cart is empty") }
            return
        }
        if (addressLine1.isBlank() || city.isBlank()) {
            _state.update { it.copy(checkoutError = "Delivery address required") }
            return
        }
        _state.update { it.copy(isCheckingOut = true, checkoutError = null) }
        viewModelScope.launch {
            val req = CheckoutRequest(
                items = cart.map { OrderItemRequest(it.product.id, it.qty) },
                deliveryAddress = DeliveryAddress(line1 = addressLine1, city = city)
            )
            when (val res = repository.checkout(token, req)) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        isCheckingOut = false,
                        lastOrder = res.value,
                        cart = emptyList(),
                        showCart = false
                    )
                }
                is ApiResult.HttpError -> _state.update {
                    it.copy(
                        isCheckingOut = false,
                        checkoutError = "Checkout rejected (${res.code})"
                    )
                }
                is ApiResult.NetworkError -> _state.update {
                    it.copy(isCheckingOut = false, checkoutError = "Network error")
                }
            }
        }
    }
}
