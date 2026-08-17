package com.scottstechx.commerceos.data.auth

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.scottstechx.commerceos.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Firebase Auth client — signup + sign-in (email/password) and Google Sign-In.
 *
 * Returns the Firebase ID token; the caller sends it to the backend
 * POST /api/v1/auth/firebase, which verifies it with the Admin SDK and issues
 * the app's own JWT.
 *
 * Firebase is initialized programmatically from BuildConfig fields rather than
 * a generated google-services.json, so the project builds without a generated
 * file. Populate the values via -PfirebaseApiKey=... etc. or gradle.properties
 * (the web-app config from the Firebase console: Project settings -> General ->
 * Your apps -> Web).
 */
@Singleton
class FirebaseAuthClient @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private var firebaseApp: FirebaseApp? = null

    private fun app(): FirebaseApp {
        firebaseApp?.let { return it }
        val options = FirebaseOptions.Builder()
            .setApiKey(BuildConfig.FIREBASE_API_KEY)
            .setApplicationId(BuildConfig.FIREBASE_APP_ID)
            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
            .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
            .build()
        val app = FirebaseApp.initializeApp(context, options, "scottstechx")
        firebaseApp = app
        return app
    }

    private fun auth(): FirebaseAuth = FirebaseAuth.getInstance(app())

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) {
                cont.resume(task.result)
            } else {
                cont.resumeWithException(task.exception ?: RuntimeException("Firebase Auth failed"))
            }
        }
    }

    private suspend fun idToken(): String? =
        auth().currentUser?.getIdToken(false)?.await()?.token

    /** Create a new email/password account; returns the Firebase ID token. */
    suspend fun signUpWithEmail(email: String, password: String): String? {
        auth().createUserWithEmailAndPassword(email, password).await()
        return idToken()
    }

    /** Sign in an existing email/password account; returns the Firebase ID token. */
    suspend fun signInWithEmail(email: String, password: String): String? {
        auth().signInWithEmailAndPassword(email, password).await()
        return idToken()
    }

    /** Exchange a Google ID token (from Credential Manager) for a Firebase ID token. */
    suspend fun signInWithGoogle(googleIdToken: String): String? {
        val credential = GoogleAuthProvider.getCredential(googleIdToken, null)
        auth().signInWithCredential(credential).await()
        return idToken()
    }

    fun signOut() {
        auth().signOut()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object FirebaseAuthModule {
    @Provides
    @Singleton
    fun provideFirebaseAuthClient(
        @ApplicationContext context: Context
    ): FirebaseAuthClient = FirebaseAuthClient(context)
}
