package com.scottstechx.commerceos.ui

import androidx.lifecycle.ViewModel
import com.scottstechx.commerceos.data.auth.AuthStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Trivial VM used to hand the singleton AuthStore to the top-level
 * composable. Kept separate from feature VMs so its scope can be
 * widened later without touching screen code.
 */
@HiltViewModel
class AuthGateViewModel @Inject constructor(
    val authStore: AuthStore
) : ViewModel()
