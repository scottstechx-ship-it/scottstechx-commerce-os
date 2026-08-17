package com.scottstechx.commerceos.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistent auth state. Token, userId, role are stored in
 * EncryptedSharedPreferences so the session survives process death and
 * reboot, but the on-disk file is AES256-GCM encrypted with a key
 * wrapped by the AndroidKeyStore.
 */
@Singleton
class AuthStore @Inject constructor(
    private val prefs: SharedPreferences
) {

    private val _state = MutableStateFlow(loadFromDisk())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    val currentToken: String? get() = _state.value.token
    val isSignedIn: Boolean get() = !_state.value.token.isNullOrBlank()
    val role: Role? get() = _state.value.role

    fun setSession(token: String, userId: String, role: Role) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_ROLE, role.name)
            .apply()
        _state.value = AuthState(token = token, userId = userId, role = role)
    }

    fun clear() {
        prefs.edit().clear().apply()
        _state.value = AuthState()
    }

    private fun loadFromDisk(): AuthState {
        val token = prefs.getString(KEY_TOKEN, null)
        val userId = prefs.getString(KEY_USER_ID, null)
        val role = prefs.getString(KEY_ROLE, null)?.let { Role.fromWire(it) }
        return AuthState(token = token, userId = userId, role = role)
    }

    private companion object {
        const val KEY_TOKEN = "auth_token"
        const val KEY_USER_ID = "auth_user_id"
        const val KEY_ROLE = "auth_role"
    }
}

enum class Role {
    BUYER, SELLER, DRIVER, ADMIN, UNKNOWN;
    companion object {
        fun fromWire(s: String?): Role = when (s?.uppercase()) {
            "BUYER" -> BUYER
            "SELLER" -> SELLER
            "DRIVER" -> DRIVER
            "ADMIN" -> ADMIN
            else -> UNKNOWN
        }
    }
}

data class AuthState(
    val token: String? = null,
    val userId: String? = null,
    val role: Role? = null
)

/**
 * Hilt module that provides the encrypted SharedPreferences used by
 * AuthStore. Master key is generated lazily by the AndroidKeyStore on
 * first use; subsequent launches reuse the same key.
 */
@Module
@InstallIn(SingletonComponent::class)
object AuthStoreModule {

    private const val PREFS_FILE = "scottstechx_secure_prefs"

    @Provides
    @Singleton
    fun provideEncryptedSharedPreferences(
        @ApplicationContext context: Context
    ): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}
