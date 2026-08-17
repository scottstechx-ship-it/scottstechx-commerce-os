package com.scottstechx.commerceos.data.remote

/**
 * Result wrapper that surfaces either a successful payload or a typed
 * failure. Keeps the UI free of Retrofit/HTTP-specific types.
 */
sealed class ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>()
    data class HttpError(val code: Int, val message: String) : ApiResult<Nothing>()
    data class NetworkError(val cause: Throwable) : ApiResult<Nothing>()

    inline fun <R> map(transform: (T) -> R): ApiResult<R> = when (this) {
        is Success -> Success(transform(value))
        is HttpError -> this
        is NetworkError -> this
    }

    fun getOrNull(): T? = (this as? Success)?.value
}

internal suspend inline fun <T : Any> safeCall(
    crossinline block: suspend () -> retrofit2.Response<T>
): ApiResult<T> = try {
    val response = block()
    if (response.isSuccessful) {
        val body = response.body()
        if (body != null) ApiResult.Success(body)
        else ApiResult.HttpError(response.code(), "Empty body")
    } else {
        ApiResult.HttpError(response.code(), response.errorBody()?.string().orEmpty())
    }
} catch (t: Throwable) {
    ApiResult.NetworkError(t)
}
