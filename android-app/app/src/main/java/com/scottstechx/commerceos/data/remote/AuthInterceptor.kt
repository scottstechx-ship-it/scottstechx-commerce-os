package com.scottstechx.commerceos.data.remote

import com.scottstechx.commerceos.data.auth.AuthStore

/**
 * OkHttp interceptor that attaches "Authorization: Bearer <token>" to every
 * outbound request, and clears the local token store on 401 so the UI can
 * route the user back to login.
 */
class AuthInterceptor(
    private val authStore: AuthStore
) : okhttp3.Interceptor {

    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        val original = chain.request()
        val token = authStore.currentToken
        val request = if (!token.isNullOrBlank()) {
            original.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            original
        }
        val response = chain.proceed(request)
        if (response.code == 401) {
            authStore.clear()
        }
        return response
    }
}
