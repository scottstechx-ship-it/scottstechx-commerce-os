package com.scottstechx.commerceos.ui.nearby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scottstechx.commerceos.data.ScottsTechXRepository
import com.scottstechx.commerceos.data.auth.AuthStore
import com.scottstechx.commerceos.data.location.LocationProvider
import com.scottstechx.commerceos.data.remote.ApiResult
import com.scottstechx.commerceos.data.remote.dto.SellerNearbyDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NearbyUiState(
    val isRequestingLocation: Boolean = false,
    val isLoadingSellers: Boolean = false,
    val lat: Double? = null,
    val lng: Double? = null,
    val locationError: String? = null,
    val sellers: List<SellerNearbyDto> = emptyList(),
    val error: String? = null,
    val hasFetched: Boolean = false
)

@HiltViewModel
class NearbySellersViewModel @Inject constructor(
    private val repository: ScottsTechXRepository,
    private val locationProvider: LocationProvider,
    private val authStore: AuthStore
) : ViewModel() {

    private val _state = MutableStateFlow(NearbyUiState())
    val state: StateFlow<NearbyUiState> = _state.asStateFlow()

    /**
     * Request a fresh GPS fix, then load nearby sellers ranked by the
     * server. If the device doesn't have a fix (emulator without GPS
     * configured), fall back to the last cached coordinates so the
     * "loading" experience still produces a list.
     */
    fun load() {
        val token = authStore.currentToken ?: return
        _state.update { it.copy(isRequestingLocation = true, locationError = null) }
        viewModelScope.launch {
            when (val fix = locationProvider.getCurrent()) {
                is LocationProvider.Fix.Success -> {
                    _state.update {
                        it.copy(
                            isRequestingLocation = false,
                            lat = fix.lat, lng = fix.lng
                        )
                    }
                    fetch(token, fix.lat, fix.lng)
                }
                is LocationProvider.Fix.Denied -> {
                    _state.update {
                        it.copy(
                            isRequestingLocation = false,
                            locationError = "Location permission denied"
                        )
                    }
                }
                is LocationProvider.Fix.Unavailable -> {
                    _state.update {
                        it.copy(
                            isRequestingLocation = false,
                            locationError = "Could not get a GPS fix"
                        )
                    }
                }
            }
        }
    }

    private fun fetch(token: String, lat: Double, lng: Double) {
        _state.update { it.copy(isLoadingSellers = true, error = null) }
        viewModelScope.launch {
            val res = repository.nearbySellers(token, lat, lng)
            when (res) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        isLoadingSellers = false,
                        sellers = res.value.sortedByDescending { s -> s.rankScore },
                        hasFetched = true
                    )
                }
                is ApiResult.HttpError -> _state.update {
                    it.copy(
                        isLoadingSellers = false,
                        error = "Server error (${res.code})",
                        hasFetched = true
                    )
                }
                is ApiResult.NetworkError -> _state.update {
                    it.copy(
                        isLoadingSellers = false,
                        error = "Network error",
                        hasFetched = true
                    )
                }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}
