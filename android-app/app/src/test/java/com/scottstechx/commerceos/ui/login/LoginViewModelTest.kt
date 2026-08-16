package com.scottstechx.commerceos.ui.login

import app.cash.turbine.test
import com.scottstechx.commerceos.data.ScottsTechXRepository
import com.scottstechx.commerceos.data.auth.AuthStore
import com.scottstechx.commerceos.data.auth.GoogleSignInHelper
import com.scottstechx.commerceos.data.auth.Role
import com.scottstechx.commerceos.data.remote.ApiResult
import com.scottstechx.commerceos.data.remote.dto.GoogleAuthResponse
import com.scottstechx.commerceos.data.remote.dto.GoogleAuthRequest
import com.scottstechx.commerceos.data.remote.dto.LoginRequest
import com.scottstechx.commerceos.data.remote.dto.LoginResponse
import com.scottstechx.commerceos.security.PlayIntegrityClient
import com.scottstechx.commerceos.security.TamperDetector
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
class LoginViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val repo: ScottsTechXRepository = mockk()
    private val authStore: AuthStore = mockk(relaxed = true)
    private val playIntegrity: PlayIntegrityClient = mockk()
    private val tamperDetector: TamperDetector = mockk()
    private val googleSignInHelper: GoogleSignInHelper = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { tamperDetector.inspect() } returns TamperDetector.Report()
        every { authStore.state } returns kotlinx.coroutines.flow.MutableStateFlow(
            com.scottstechx.commerceos.data.auth.AuthState()
        )
        coEvery { playIntegrity.requestToken(any()) } returns null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `submit with blank phone sets error`() = runTest(dispatcher) {
        val vm = LoginViewModel(repo, authStore, playIntegrity, tamperDetector, googleSignInHelper)
        vm.submit()
        assertEquals("Phone and password are required", vm.state.value.error)
    }

    @Test
    fun `submit with short password sets error`() = runTest(dispatcher) {
        val vm = LoginViewModel(repo, authStore, playIntegrity, tamperDetector, googleSignInHelper)
        vm.onPhoneChange("0700000000")
        vm.onPasswordChange("12")
        vm.submit()
        assertNotNull(vm.state.value.error)
        assertEquals("Password is too short", vm.state.value.error)
    }

    @Test
    fun `submit success signs in the user`() = runTest(dispatcher) {
        coEvery { repo.login(any()) } returns ApiResult.Success(
            LoginResponse(
                token = "jwt",
                userId = "u1",
                role = "buyer",
                expiresAt = "2030-01-01"
            )
        )
        val vm = LoginViewModel(repo, authStore, playIntegrity, tamperDetector, googleSignInHelper)
        vm.onPhoneChange("0700000000")
        vm.onPasswordChange("password")
        vm.submit()
        advanceUntilIdle()
        assertEquals(Role.BUYER, vm.state.value.signedInAs)
        verify { authStore.setSession(token = "jwt", userId = "u1", role = Role.BUYER) }
    }

    @Test
    fun `submit 401 surfaces friendly error`() = runTest(dispatcher) {
        coEvery { repo.login(any()) } returns ApiResult.HttpError(401, "invalid")
        val vm = LoginViewModel(repo, authStore, playIntegrity, tamperDetector, googleSignInHelper)
        vm.onPhoneChange("0700000000")
        vm.onPasswordChange("password")
        vm.submit()
        advanceUntilIdle()
        assertEquals("Invalid phone or password", vm.state.value.error)
        assertNull(vm.state.value.signedInAs)
    }

    @Test
    fun `signInWithGoogle 503 surfaces friendly error`() = runTest(dispatcher) {
        coEvery { repo.googleAuth(any()) } returns ApiResult.HttpError(503, "google_auth_disabled")
        val vm = LoginViewModel(repo, authStore, playIntegrity, tamperDetector, googleSignInHelper)
        vm.signInWithGoogle("fake-id-token")
        advanceUntilIdle()
        assertEquals("Google sign-in is not configured on the server yet.", vm.state.value.error)
        assertNull(vm.state.value.signedInAs)
    }

    @Test
    fun `signInWithGoogle success signs in`() = runTest(dispatcher) {
        coEvery { repo.googleAuth(any()) } returns ApiResult.Success(
            GoogleAuthResponse(
                token = "jwt",
                userId = "g1",
                role = "seller",
                email = "u@example.com",
                expiresAt = "2030-01-01"
            )
        )
        val vm = LoginViewModel(repo, authStore, playIntegrity, tamperDetector, googleSignInHelper)
        vm.signInWithGoogle("fake-id-token")
        advanceUntilIdle()
        assertEquals(Role.SELLER, vm.state.value.signedInAs)
    }
}
