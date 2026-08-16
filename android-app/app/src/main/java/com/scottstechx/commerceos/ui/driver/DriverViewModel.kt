package com.scottstechx.commerceos.ui.driver

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scottstechx.commerceos.data.ScottsTechXRepository
import com.scottstechx.commerceos.data.auth.AuthStore
import com.scottstechx.commerceos.data.capture.PodCapture
import com.scottstechx.commerceos.data.location.LocationProvider
import com.scottstechx.commerceos.data.remote.ApiResult
import com.scottstechx.commerceos.data.remote.dto.OrderResponse
import com.scottstechx.commerceos.data.remote.dto.PodRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PodAction(val wire: String) {
    PICKUP("pickup"),
    DELIVER("deliver")
}

data class DriverUiState(
    val assigned: List<OrderResponse> = emptyList(),
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val isFetchingLocation: Boolean = false,
    val loadError: String? = null,
    val podError: String? = null,
    val lastSubmittedOrderId: String? = null,
    val pendingCaptureUri: Uri? = null,
    val pendingCaptureOrderId: String? = null
)

@HiltViewModel
class DriverViewModel @Inject constructor(
    private val repository: ScottsTechXRepository,
    private val authStore: AuthStore,
    private val locationProvider: LocationProvider,
    private val podCapture: PodCapture
) : ViewModel() {

    private val _state = MutableStateFlow(DriverUiState())
    val state: StateFlow<DriverUiState> = _state.asStateFlow()

    init { loadAssigned() }

    fun loadAssigned() {
        val token = authStore.currentToken
        if (token.isNullOrBlank()) return
        _state.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            when (val res = repository.listAssigned(token)) {
                is ApiResult.Success -> _state.update {
                    it.copy(isLoading = false, assigned = res.value)
                }
                is ApiResult.HttpError -> _state.update {
                    it.copy(
                        isLoading = false,
                        loadError = "Failed to load assigned (${res.code})"
                    )
                }
                is ApiResult.NetworkError -> _state.update {
                    it.copy(isLoading = false, loadError = "Network error")
                }
            }
        }
    }

    /**
     * Kick off a real GPS fix for the given order. The result is
     * delivered through [onLocationResolved] which the screen passes
     * in to update its local lat/lng text fields.
     */
    fun fetchLocationForOrder(orderId: String, onLocationResolved: (Double, Double) -> Unit) {
        if (!locationProvider.hasPermission()) {
            _state.update { it.copy(podError = "Location permission denied") }
            return
        }
        _state.update { it.copy(isFetchingLocation = true, podError = null) }
        viewModelScope.launch {
            val loc = locationProvider.currentLocation()
            _state.update { it.copy(isFetchingLocation = false) }
            if (loc == null) {
                _state.update { it.copy(podError = "Could not get a GPS fix") }
            } else {
                onLocationResolved(loc.latitude, loc.longitude)
            }
        }
    }

    /**
     * Create a new FileProvider Uri for the camera intent. The
     * returned [Uri] is what the system camera writes to, and what
     * the caller passes to [submitPodForOrder] via the resolved photo.
     */
    fun startPhotoCapture(orderId: String): Uri {
        val uri = podCapture.newPhotoUri()
        _state.update {
            it.copy(pendingCaptureUri = uri, pendingCaptureOrderId = orderId)
        }
        return uri
    }

    fun cancelPendingCapture() {
        _state.update {
            it.copy(pendingCaptureUri = null, pendingCaptureOrderId = null)
        }
    }

    fun submitPod(
        orderId: String,
        action: PodAction,
        gpsLat: Double,
        gpsLng: Double,
        notes: String?,
        photoUri: Uri?
    ) {
        val token = authStore.currentToken
        if (token.isNullOrBlank()) {
            _state.update { it.copy(podError = "Not signed in") }
            return
        }
        if (gpsLat !in -90.0..90.0 || gpsLng !in -180.0..180.0) {
            _state.update { it.copy(podError = "Invalid GPS coordinates") }
            return
        }
        _state.update { it.copy(isSubmitting = true, podError = null) }
        viewModelScope.launch {
            val base64 = photoUri?.let { podCapture.encodeJpeg(it) }
            val req = PodRequest(
                orderId = orderId,
                action = action.wire,
                gpsLat = gpsLat,
                gpsLng = gpsLng,
                notes = notes?.takeIf { it.isNotBlank() },
                signaturePngBase64 = base64
            )
            when (val res = repository.submitPod(token, req)) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            lastSubmittedOrderId = res.value.orderId,
                            pendingCaptureUri = null,
                            pendingCaptureOrderId = null
                        )
                    }
                    loadAssigned()
                }
                is ApiResult.HttpError -> _state.update {
                    it.copy(
                        isSubmitting = false,
                        podError = "POD rejected (${res.code})"
                    )
                }
                is ApiResult.NetworkError -> _state.update {
                    it.copy(isSubmitting = false, podError = "Network error")
                }
            }
        }
    }

    fun clearPodError() = _state.update { it.copy(podError = null) }
    fun consumeLastSubmitted() = _state.update { it.copy(lastSubmittedOrderId = null) }
    fun signOut() = authStore.clear()
}
