package com.scottstechx.commerceos.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scottstechx.commerceos.data.ScottsTechXRepository
import com.scottstechx.commerceos.data.auth.AuthStore
import com.scottstechx.commerceos.data.auth.GoogleSignInHelper
import com.scottstechx.commerceos.data.auth.Role
import com.scottstechx.commerceos.data.remote.ApiResult
import com.scottstechx.commerceos.data.remote.dto.GoogleAuthRequest
import com.scottstechx.commerceos.data.remote.dto.LoginRequest
import com.scottstechx.commerceos.security.PlayIntegrityClient
import com.scottstechx.commerceos.security.SecurityLog
import com.scottstechx.commerceos.security.InputValidator
import com.scottstechx.commerceos.security.TamperDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class LoginUiState(
    val phone: String = "",
    val password: String = "",
    val role: Role = Role.BUYER,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val signedInAs: Role? = null,
    val tamper: TamperDetector.Report? = null
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: ScottsTechXRepository,
    private val authStore: AuthStore,
    private val playIntegrity: PlayIntegrityClient,
    private val tamperDetector: TamperDetector,
    private val googleSignInHelper: GoogleSignInHelper
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    init {
        _state.update { it.copy(tamper = tamperDetector.inspect()) }
    }

    fun onPhoneChange(v: String) = _state.update { it.copy(phone = v, error = null) }
    fun onPasswordChange(v: String) = _state.update { it.copy(password = v, error = null) }
    fun onRoleChange(r: Role) = _state.update { it.copy(role = r, error = null) }

    fun submit() {
        val current = _state.value
        if (current.phone.isBlank() || current.password.isBlank()) {
            _state.update { it.copy(error = "Phone and password are required") }
            return
        }
        val phoneCheck = InputValidator.validatePhone(current.phone.trim())
        if (phoneCheck is InputValidator.Result.Invalid) {
            _state.update { it.copy(error = phoneCheck.reason) }
            return
        }
        if (current.password.length < 4) {
            _state.update { it.copy(error = "Password is too short") }
            return
        }
        if (current.isSubmitting) return
        _state.update { it.copy(isSubmitting = true, error = null) }
        SecurityLog.info("login_attempt", mapOf("role" to current.role.name))
        viewModelScope.launch {
            // Request an integrity token. Null is fine — server tolerates
            // missing token (the integrity score is one of many signals).
            val nonce = UUID.randomUUID().toString()
            val integrityToken = playIntegrity.requestToken(nonce)

            val req = LoginRequest(
                phone = current.phone.trim(),
                password = current.password,
                role = current.role.name,
                integrityToken = integrityToken,
                deviceFingerprint = current.tamper?.tags?.joinToString(",")
            )
            when (val res = repository.login(req)) {
                is ApiResult.Success -> {
                    val role = Role.fromWire(res.value.role)
                    authStore.setSession(
                        token = res.value.token,
                        userId = res.value.userId,
                        role = role
                    )
                    _state.update {
                        it.copy(isSubmitting = false, signedInAs = role)
                    }
                }
                is ApiResult.HttpError -> {
                    val msg = if (res.code == 401) "Invalid phone or password"
                    else "Server error (${res.code})"
                    _state.update { it.copy(isSubmitting = false, error = msg) }
                }
                is ApiResult.NetworkError -> {
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            error = "Cannot reach server. Is 12_Backend running on 10.0.2.2:3000?"
                        )
                    }
                }
            }
        }
    }

    fun consumeSignedIn() {
        _state.update { it.copy(signedInAs = null) }
    }

    /**
     * Receives a Google id_token from [GoogleSignInHelper] and exchanges
     * it with the backend for our own JWT. Surfaces a friendly error if
     * the backend doesn't have Google OAuth configured (503 'google_auth_disabled').
     */
    fun signInWithGoogle(idToken: String) {
        if (_state.value.isSubmitting) return
        _state.update { it.copy(isSubmitting = true, error = null) }
        SecurityLog.info("google_login_attempt", mapOf("role" to _state.value.role.name))
        viewModelScope.launch {
            val req = GoogleAuthRequest(idToken = idToken, role = _state.value.role.name)
            when (val res = repository.googleAuth(req)) {
                is ApiResult.Success -> {
                    val role = Role.fromWire(res.value.role)
                    authStore.setSession(
                        token = res.value.token,
                        userId = res.value.userId,
                        role = role
                    )
                    _state.update {
                        it.copy(isSubmitting = false, signedInAs = role)
                    }
                }
                is ApiResult.HttpError -> {
                    val msg = when (res.code) {
                        503 -> "Google sign-in is not configured on the server yet."
                        401 -> "Google did not accept this account. Try again."
                        else -> "Server error (${res.code})"
                    }
                    _state.update { it.copy(isSubmitting = false, error = msg) }
                }
                is ApiResult.NetworkError -> _state.update {
                    it.copy(
                        isSubmitting = false,
                        error = "Cannot reach server. Is the backend running?"
                    )
                }
            }
        }
    }

    fun cancelGoogleSignIn() {
        _state.update {
            it.copy(
                isSubmitting = false,
                error = "Google sign-in was cancelled."
            )
        }
    }
}
