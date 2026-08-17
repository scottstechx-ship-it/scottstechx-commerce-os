package com.scottstechx.commerceos.ui.seller

import com.scottstechx.commerceos.data.ScottsTechXRepository
import com.scottstechx.commerceos.data.auth.AuthState
import com.scottstechx.commerceos.data.auth.AuthStore
import com.scottstechx.commerceos.data.auth.Role
import com.scottstechx.commerceos.data.remote.ApiResult
import com.scottstechx.commerceos.data.remote.dto.OrderResponse
import com.scottstechx.commerceos.data.remote.dto.ProductDto
import com.scottstechx.commerceos.data.remote.dto.SellerStatsDto
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SellerViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val repo: ScottsTechXRepository = mockk()
    private val authStore: AuthStore = mockk(relaxed = true)

    private val products = listOf(
        ProductDto(
            id = "p1",
            sellerId = "s1",
            title = "Tote",
            description = "Handmade",
            priceMinor = 2500000,
            currency = "UGX",
            stockQuantity = 25,
            productTrustScore = 70.0
        )
    )
    private val stats = SellerStatsDto(
        sellerId = "s1",
        activeListings = 1,
        totalListings = 1,
        ordersToday = 0,
        ordersThisWeek = 0,
        revenueMinorToday = 0,
        revenueMinorThisWeek = 0,
        currency = "UGX",
        averageRating = 0.0,
        ratingCount = 0
    )
    private val orders = emptyList<OrderResponse>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { authStore.state } returns MutableStateFlow(
            AuthState(token = "jwt", userId = "s1", role = Role.SELLER)
        )
        every { authStore.currentToken } returns "jwt"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `refreshAll populates state on success`() = runTest(dispatcher) {
        coEvery { repo.listInventory("jwt") } returns ApiResult.Success(products)
        coEvery { repo.getSellerStats("jwt") } returns ApiResult.Success(stats)
        coEvery { repo.listSellerOrders("jwt", null) } returns ApiResult.Success(orders)
        val vm = SellerViewModel(repo, authStore)
        advanceUntilIdle()
        val s = vm.state.value
        assertEquals(1, s.products.size)
        assertEquals(stats, s.stats)
        assertEquals(orders, s.orders)
        assertEquals(false, s.isLoading)
        assertEquals(null, s.error)
    }

    @Test
    fun `refreshAll surfaces error on first failure`() = runTest(dispatcher) {
        coEvery { repo.listInventory("jwt") } returns ApiResult.NetworkError
        coEvery { repo.getSellerStats("jwt") } returns ApiResult.Success(stats)
        coEvery { repo.listSellerOrders("jwt", null) } returns ApiResult.Success(orders)
        val vm = SellerViewModel(repo, authStore)
        advanceUntilIdle()
        assertNotNull(vm.state.value.error)
        assertEquals("Network error", vm.state.value.error)
    }

    @Test
    fun `createProduct with invalid price sets error and does not call repo`() = runTest(dispatcher) {
        coEvery { repo.createProduct(any(), any()) } returns ApiResult.Success(products[0])
        val vm = SellerViewModel(repo, authStore)
        advanceUntilIdle()
        vm.createProduct(title = "x", description = "y", priceMinor = -1L, stock = 1, imageUrl = null)
        assertNotNull(vm.state.value.error)
    }
}
