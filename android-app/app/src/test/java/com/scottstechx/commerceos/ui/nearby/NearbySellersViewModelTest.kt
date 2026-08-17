package com.scottstechx.commerceos.ui.nearby

import com.scottstechx.commerceos.data.ScottsTechXRepository
import com.scottstechx.commerceos.data.auth.AuthState
import com.scottstechx.commerceos.data.auth.AuthStore
import com.scottstechx.commerceos.data.location.LocationProvider
import com.scottstechx.commerceos.data.remote.ApiResult
import com.scottstechx.commerceos.data.remote.dto.SellerNearbyDto
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NearbySellersViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val repo: ScottsTechXRepository = mockk()
    private val locationProvider: LocationProvider = mockk()
    private val authStore: AuthStore = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { authStore.state } returns MutableStateFlow(
            AuthState(token = "jwt", userId = "u1", role = com.scottstechx.commerceos.data.auth.Role.BUYER)
        )
        every { authStore.currentToken } returns "jwt"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load with Success fix triggers fetch`() = runTest(dispatcher) {
        coEvery { locationProvider.getCurrent() } returns LocationProvider.Fix.Success(0.0, 0.0)
        val seller = SellerNearbyDto(
            sellerId = "s1",
            displayName = "S",
            trustTier = "GOLD",
            trustScore = 70.0,
            distanceMetres = 100.0,
            rankScore = 80.0,
            productCount = 3,
            activeOrderCount = 1,
            ratingAvg = 4.0,
            ratingCount = 10
        )
        coEvery { repo.nearbySellers(any(), 0.0, 0.0, any(), any()) } returns
            ApiResult.Success(listOf(seller))
        val vm = NearbySellersViewModel(repo, locationProvider, authStore)
        advanceUntilIdle()
        assertEquals(1, vm.state.value.sellers.size)
        assertEquals("S", vm.state.value.sellers.first().displayName)
    }

    @Test
    fun `load with Denied fix sets locationError and no fetch`() = runTest(dispatcher) {
        coEvery { locationProvider.getCurrent() } returns LocationProvider.Fix.Denied
        val vm = NearbySellersViewModel(repo, locationProvider, authStore)
        advanceUntilIdle()
        assertEquals("Location permission denied", vm.state.value.locationError)
        assertEquals(0, vm.state.value.sellers.size)
    }

    @Test
    fun `load with network error sets error`() = runTest(dispatcher) {
        coEvery { locationProvider.getCurrent() } returns LocationProvider.Fix.Success(0.0, 0.0)
        coEvery { repo.nearbySellers(any(), any(), any(), any(), any()) } returns
            ApiResult.NetworkError
        val vm = NearbySellersViewModel(repo, locationProvider, authStore)
        advanceUntilIdle()
        assertEquals("Network error", vm.state.value.error)
        assertEquals(0, vm.state.value.sellers.size)
    }
}
