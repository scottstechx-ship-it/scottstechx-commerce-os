package com.scottstechx.commerceos.data

import com.scottstechx.commerceos.data.cache.ProductCache
import com.scottstechx.commerceos.data.remote.ApiResult
import com.scottstechx.commerceos.data.remote.ScottsTechXApi
import com.scottstechx.commerceos.data.remote.dto.AiCustomerChatRequest
import com.scottstechx.commerceos.data.remote.dto.AiCustomerChatResponse
import com.scottstechx.commerceos.data.remote.dto.AiReasonRequest
import com.scottstechx.commerceos.data.remote.dto.AiReasonResponse
import com.scottstechx.commerceos.data.remote.dto.AiSellerSuggestRequest
import com.scottstechx.commerceos.data.remote.dto.AiSellerSuggestResponse
import com.scottstechx.commerceos.data.remote.dto.AiStatusResponse
import com.scottstechx.commerceos.data.remote.dto.ChatMessageDto
import com.scottstechx.commerceos.data.remote.dto.CheckoutRequest
import com.scottstechx.commerceos.data.remote.dto.CreateProductRequest
import com.scottstechx.commerceos.data.remote.dto.CreateReviewRequest
import com.scottstechx.commerceos.data.remote.dto.GoogleAuthRequest
import com.scottstechx.commerceos.data.remote.dto.GoogleAuthResponse
import com.scottstechx.commerceos.data.remote.dto.LoginRequest
import com.scottstechx.commerceos.data.remote.dto.LoginResponse
import com.scottstechx.commerceos.data.remote.dto.OrderResponse
import com.scottstechx.commerceos.data.remote.dto.PodRequest
import com.scottstechx.commerceos.data.remote.dto.PodResponse
import com.scottstechx.commerceos.data.remote.dto.PostChatMessageRequest
import com.scottstechx.commerceos.data.remote.dto.ProductDto
import com.scottstechx.commerceos.data.remote.dto.ReviewDto
import com.scottstechx.commerceos.data.remote.dto.SellerDetailDto
import com.scottstechx.commerceos.data.remote.dto.SellerNearbyDto
import com.scottstechx.commerceos.data.remote.dto.SellerStatsDto
import com.scottstechx.commerceos.data.remote.dto.UpdateProductRequest
import com.scottstechx.commerceos.data.remote.safeCall
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin facade over the Retrofit interface. Idempotency keys are minted
 * here (client-side UUIDs) so callers don't have to remember. The
 * product list write-through-caches into [ProductCache] so the Buyer
 * screen can render offline.
 */
@Singleton
class ScottsTechXRepository @Inject constructor(
    private val api: ScottsTechXApi,
    private val productCache: ProductCache
) {

    // ---- Auth + Buyer ----

    suspend fun login(req: LoginRequest): ApiResult<LoginResponse> =
        safeCall { api.login(req) }

    suspend fun listProducts(token: String): ApiResult<List<ProductDto>> {
        val res = safeCall { api.listProducts("Bearer $token") }
        if (res is ApiResult.Success) {
            // Fire-and-forget cache write; don't block the response.
            productCache.write(res.value)
        }
        return res
    }

    suspend fun readCachedProducts(): List<ProductDto> = productCache.read()

    suspend fun checkout(token: String, req: CheckoutRequest): ApiResult<OrderResponse> {
        val key = UUID.randomUUID().toString()
        return safeCall { api.checkout("Bearer $token", key, req) }
    }

    // ---- Driver ----

    suspend fun listAssigned(token: String): ApiResult<List<OrderResponse>> =
        safeCall { api.listAssigned("Bearer $token") }

    suspend fun submitPod(token: String, req: PodRequest): ApiResult<PodResponse> {
        val key = UUID.randomUUID().toString()
        return safeCall { api.submitPod("Bearer $token", key, req) }
    }

    // ---- Marketplace: buyer side ----

    suspend fun nearbySellers(
        token: String,
        lat: Double,
        lng: Double,
        radiusKm: Double = 25.0,
        limit: Int = 50
    ): ApiResult<List<SellerNearbyDto>> =
        safeCall { api.nearbySellers("Bearer $token", lat, lng, radiusKm, limit) }

    suspend fun getSeller(token: String, sellerId: String): ApiResult<SellerDetailDto> =
        safeCall { api.getSeller("Bearer $token", sellerId) }

    // ---- Marketplace: seller side ----

    suspend fun listInventory(token: String): ApiResult<List<ProductDto>> =
        safeCall { api.listInventory("Bearer $token") }

    suspend fun createProduct(
        token: String,
        req: CreateProductRequest
    ): ApiResult<ProductDto> {
        val key = UUID.randomUUID().toString()
        return safeCall { api.createProduct("Bearer $token", key, req) }
    }

    suspend fun updateProduct(
        token: String,
        productId: String,
        req: UpdateProductRequest
    ): ApiResult<ProductDto> {
        val key = UUID.randomUUID().toString()
        return safeCall { api.updateProduct("Bearer $token", key, productId, req) }
    }

    suspend fun deleteProduct(token: String, productId: String): ApiResult<Unit> {
        val key = UUID.randomUUID().toString()
        return safeCall { api.deleteProduct("Bearer $token", key, productId) }
    }

    suspend fun getSellerStats(token: String): ApiResult<SellerStatsDto> =
        safeCall { api.getSellerStats("Bearer $token") }

    suspend fun listSellerOrders(token: String, status: String? = null): ApiResult<List<OrderResponse>> =
        safeCall { api.listSellerOrders("Bearer $token", status) }

    // ---- Auth: Google ----

    suspend fun googleAuth(req: GoogleAuthRequest): ApiResult<GoogleAuthResponse> =
        safeCall { api.googleAuth(req) }

    // ---- AI ----

    suspend fun aiStatus(): ApiResult<AiStatusResponse> = safeCall { api.aiStatus() }

    suspend fun aiSellerSuggest(
        token: String,
        req: AiSellerSuggestRequest
    ): ApiResult<AiSellerSuggestResponse> =
        safeCall { api.aiSellerSuggest("Bearer $token", req) }

    suspend fun aiCustomerChat(
        token: String,
        req: AiCustomerChatRequest
    ): ApiResult<AiCustomerChatResponse> =
        safeCall { api.aiCustomerChat("Bearer $token", req) }

    suspend fun aiReason(
        token: String,
        req: AiReasonRequest
    ): ApiResult<AiReasonResponse> =
        safeCall { api.aiReason("Bearer $token", req) }

    // ---- Reviews ----

    suspend fun createReview(
        token: String,
        req: CreateReviewRequest
    ): ApiResult<ReviewDto> = safeCall { api.createReview("Bearer $token", req) }

    // ---- Chat ----

    suspend fun listChatMessages(
        token: String,
        sessionId: String,
        since: String? = null
    ): ApiResult<List<ChatMessageDto>> =
        safeCall { api.listChatMessages("Bearer $token", sessionId, since) }

    suspend fun postChatMessage(
        token: String,
        req: PostChatMessageRequest
    ): ApiResult<ChatMessageDto> =
        safeCall { api.postChatMessage("Bearer $token", req) }
}
