package com.scottstechx.commerceos.data.remote

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
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * ScottsTechX Commerce OS — API surface.
 * Endpoints match the 12_Backend contracts. /api/v1 prefix is the public
 * contract; auth header is added by an OkHttp interceptor.
 */
interface ScottsTechXApi {

    @POST("api/v1/auth/login")
    suspend fun login(@Body req: LoginRequest): Response<LoginResponse>

    @GET("api/v1/products")
    suspend fun listProducts(
        @Header("Authorization") bearer: String
    ): Response<List<ProductDto>>

    @POST("api/v1/orders/checkout")
    suspend fun checkout(
        @Header("Authorization") bearer: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body req: CheckoutRequest
    ): Response<OrderResponse>

    @GET("api/v1/logistics/assigned")
    suspend fun listAssigned(
        @Header("Authorization") bearer: String
    ): Response<List<OrderResponse>>

    @POST("api/v1/logistics/pod")
    suspend fun submitPod(
        @Header("Authorization") bearer: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body req: PodRequest
    ): Response<PodResponse>

    // ---- Marketplace: buyer side ----

    /**
     * Returns sellers ranked near the buyer's location. Server is the
     * source of truth for ranking math; client only renders.
     */
    @GET("api/v1/sellers/nearby")
    suspend fun nearbySellers(
        @Header("Authorization") bearer: String,
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("radiusKm") radiusKm: Double = 25.0,
        @Query("limit") limit: Int = 50
    ): Response<List<SellerNearbyDto>>

    @GET("api/v1/sellers/{sellerId}")
    suspend fun getSeller(
        @Header("Authorization") bearer: String,
        @Path("sellerId") sellerId: String
    ): Response<SellerDetailDto>

    // ---- Marketplace: seller side ----

    @GET("api/v1/seller/inventory")
    suspend fun listInventory(
        @Header("Authorization") bearer: String
    ): Response<List<ProductDto>>

    @POST("api/v1/seller/inventory")
    suspend fun createProduct(
        @Header("Authorization") bearer: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body req: CreateProductRequest
    ): Response<ProductDto>

    @PATCH("api/v1/seller/inventory/{productId}")
    suspend fun updateProduct(
        @Header("Authorization") bearer: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Path("productId") productId: String,
        @Body req: UpdateProductRequest
    ): Response<ProductDto>

    @DELETE("api/v1/seller/inventory/{productId}")
    suspend fun deleteProduct(
        @Header("Authorization") bearer: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Path("productId") productId: String
    ): Response<Unit>

    @GET("api/v1/seller/stats")
    suspend fun getSellerStats(
        @Header("Authorization") bearer: String
    ): Response<SellerStatsDto>

    @GET("api/v1/seller/orders")
    suspend fun listSellerOrders(
        @Header("Authorization") bearer: String,
        @Query("status") status: String? = null
    ): Response<List<OrderResponse>>

    // ---- Auth: Google Sign-In ----

    @POST("api/v1/auth/google")
    suspend fun googleAuth(@Body req: GoogleAuthRequest): Response<GoogleAuthResponse>

    // ---- AI ----

    @GET("api/v1/ai/status")
    suspend fun aiStatus(): Response<AiStatusResponse>

    @POST("api/v1/ai/seller-suggest")
    suspend fun aiSellerSuggest(
        @Header("Authorization") bearer: String,
        @Body req: AiSellerSuggestRequest
    ): Response<AiSellerSuggestResponse>

    @POST("api/v1/ai/customer-chat")
    suspend fun aiCustomerChat(
        @Header("Authorization") bearer: String,
        @Body req: AiCustomerChatRequest
    ): Response<AiCustomerChatResponse>

    @POST("api/v1/ai/reason")
    suspend fun aiReason(
        @Header("Authorization") bearer: String,
        @Body req: AiReasonRequest
    ): Response<AiReasonResponse>

    // ---- Reviews ----

    @POST("api/v1/reviews")
    suspend fun createReview(
        @Header("Authorization") bearer: String,
        @Body req: CreateReviewRequest
    ): Response<ReviewDto>

    // ---- Chat ----

    @GET("api/v1/chat/messages")
    suspend fun listChatMessages(
        @Header("Authorization") bearer: String,
        @Query("sessionId") sessionId: String,
        @Query("since") since: String? = null
    ): Response<List<ChatMessageDto>>

    @POST("api/v1/chat/messages")
    suspend fun postChatMessage(
        @Header("Authorization") bearer: String,
        @Body req: PostChatMessageRequest
    ): Response<ChatMessageDto>
}
