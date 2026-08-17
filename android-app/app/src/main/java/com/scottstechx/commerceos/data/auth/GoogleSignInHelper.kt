package com.scottstechx.commerceos.data.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Thin wrapper over Credential Manager + Google Identity for the
 * One-Tap sign-in flow. The actual web idToken is returned; the caller
 * sends it to the backend POST /api/v1/auth/google for our own JWT.
 *
 * No SDK lock-in beyond googleid + credentials-play-services-auth; both
 * are official AndroidX/Google libraries. If a future release changes
 * the API, this is the only file to update.
 */
@Singleton
class GoogleSignInHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Triggers the Google One-Tap / Sign-In bottom sheet and returns the
     * raw id_token string on success. Returns null if the user cancelled
     * or no Google account is available on the device.
     *
     * @param activity needed by Credential Manager to attach the bottom sheet.
     * @param webClientId the OAuth 2.0 web client ID. When null, falls back
     *  to default-from-google-services. Pass your real client ID in prod.
     */
    suspend fun signIn(activity: Activity, webClientId: String? = null): String? {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .apply {
                if (!webClientId.isNullOrBlank()) {
                    setServerClientId(webClientId)
                }
            }
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        val manager = CredentialManager.create(activity)
        return try {
            val result = manager.getCredential(activity, request)
            val credential = result.credential
            when (credential.type) {
                GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL -> {
                    val googleIdTokenCredential =
                        GoogleIdTokenCredential.createFrom(credential.data)
                    googleIdTokenCredential.idToken
                }
                else -> null
            }
        } catch (_: NoCredentialException) {
            null
        } catch (e: Exception) {
            // Cancellation, network, or no account configured → treat as "user cancelled".
            null
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object GoogleSignInModule {
    @Provides
    @Singleton
    fun provideGoogleSignInHelper(
        @ApplicationContext context: Context
    ): GoogleSignInHelper = GoogleSignInHelper(context)
}
