package com.scottstechx.commerceos.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire DTOs mirror the 12_Backend response shapes 1:1.
 * Money is BIGINT minor units (UGX) on the wire — never floats.
 */

@Serializable
data class LoginRequest(
    val phone: String,
    val password: String,
    val role: String, // "BUYER" or "DRIVER"
    val integrityToken: String? = null,
    val deviceFingerprint: String? = null
)

@Serializable
data class LoginResponse(
    val token: String,
    val userId: String,
    val role: String,
    val trustTier: String? = null,
    @SerialName("expiresAt") val expiresAt: String? = null
)

@Serializable
data class ProductDto(
    val id: String,
    val sellerId: String,
    val title: String,
    val description: String,
    val priceMinor: Long,
    val currency: String = "UGX",
    val stockQuantity: Int,
    val productTrustScore: Double? = null,
    val imageUrl: String? = null
)

@Serializable
data class OrderItemRequest(
    val productId: String,
    val qty: Int
)

@Serializable
data class CheckoutRequest(
    val items: List<OrderItemRequest>,
    val deliveryAddress: DeliveryAddress
)

@Serializable
data class DeliveryAddress(
    val line1: String,
    val city: String,
    val country: String = "UG"
)

@Serializable
data class OrderResponse(
    val orderId: String,
    val status: String,
    val totalMinor: Long,
    val currency: String,
    val items: List<OrderLineDto> = emptyList()
)

@Serializable
data class OrderLineDto(
    val productId: String,
    val qty: Int,
    val unitPriceMinor: Long
)

@Serializable
data class PodRequest(
    val orderId: String,
    val action: String, // "pickup" or "deliver"
    val gpsLat: Double,
    val gpsLng: Double,
    val notes: String? = null,
    val signaturePngBase64: String? = null
)

@Serializable
data class PodResponse(
    val orderId: String,
    val status: String
)

@Serializable
data class ApiError(
    val error: String,
    val message: String? = null,
    val details: Map<String, String>? = null
)

// ---- Marketplace: sellers ----

/**
 * Seller profile as returned by /api/v1/sellers/nearby. Includes the
 * computed rank score so the buyer UI can sort/label by it without
 * re-running the math client-side. Distance is in metres.
 */
@Serializable
data class SellerNearbyDto(
    val sellerId: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val trustTier: String,                 // "BRONZE" | "SILVER" | "GOLD" | "PLATINUM"
    val trustScore: Double,                // 0..100
    val distanceMetres: Double,
    val rankScore: Double,                 // 0..100 — server-computed rank
    val productCount: Int,
    val activeOrderCount: Int = 0,
    val ratingAvg: Double = 0.0,           // 0..5
    val ratingCount: Int = 0
)

// ---- Marketplace: seller-side inventory ----

@Serializable
data class CreateProductRequest(
    val title: String,
    val description: String,
    val priceMinor: Long,
    val currency: String = "UGX",
    val stockQuantity: Int,
    val imageUrl: String? = null
)

@Serializable
data class UpdateProductRequest(
    val title: String? = null,
    val description: String? = null,
    val priceMinor: Long? = null,
    val stockQuantity: Int? = null,
    val imageUrl: String? = null,
    val isActive: Boolean? = null
)

@Serializable
data class SellerStatsDto(
    val sellerId: String,
    val activeListings: Int,
    val totalListings: Int,
    val ordersToday: Int,
    val ordersThisWeek: Int,
    val revenueMinorToday: Long,
    val revenueMinorThisWeek: Long,
    val currency: String = "UGX",
    val averageRating: Double,
    val ratingCount: Int
)

// ---- Marketplace: seller detail ----

@Serializable
data class SellerDetailDto(
    val sellerId: String,
    val displayName: String,
    val businessName: String,
    val businessDescription: String? = null,
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val address: String? = null,
    val opensAt: String? = null,
    val closesAt: String? = null,
    val isVerified: Boolean = false,
    val trustScore: Double,
    val ratingAvg: Double,
    val ratingCount: Int,
    val totalCompletedOrders: Int,
    val totalDisputes: Int,
    val products: List<SellerDetailProduct> = emptyList(),
    val reviews: List<SellerDetailReview> = emptyList()
)

@Serializable
data class SellerDetailProduct(
    val id: String,
    val title: String,
    val description: String,
    val priceMinor: Long,
    val currency: String,
    val stockQuantity: Int,
    val productTrustScore: Double = 50.0
)

@Serializable
data class SellerDetailReview(
    val id: String,
    val reviewerUserId: String,
    val reviewerDisplayName: String,
    val rating: Int,
    val body: String,
    val createdAt: String
)

// ---- Auth: Google Sign-In ----

@Serializable
data class GoogleAuthRequest(
    val idToken: String,
    val role: String? = null
)

@Serializable
data class GoogleAuthResponse(
    val token: String,
    val userId: String,
    val role: String,
    val email: String,
    val expiresAt: String? = null,
    val trustTier: String? = null
)

// ---- AI ----

@Serializable
data class AiSellerSuggestRequest(
    val type: String,        // "product_description" | "auto_price" | "category" | "inventory_warning"
    val draft: Map<String, String> = emptyMap(),
    val context: Map<String, String>? = null
)

@Serializable
data class AiSellerSuggestResponse(
    val suggestion: String,
    val reasoning: String,
    val confidence: Double,
    val provider: String
)

@Serializable
data class AiCustomerChatRequest(
    val sessionId: String,
    val message: String,
    val history: List<AiChatHistoryMessage> = emptyList()
)

@Serializable
data class AiChatHistoryMessage(
    val role: String,        // "user" | "assistant" | "system"
    val content: String
)

@Serializable
data class AiCustomerChatResponse(
    val reply: String,
    val provider: String
)

@Serializable
data class AiStatusResponse(
    val enabled: Boolean,
    val provider: String? = null
)

@Serializable
data class AiReasonRequest(
    val sellerId: String,
    val context: Map<String, String>? = null
)

@Serializable
data class AiReasonResponse(
    val trustReasoning: String,
    val rankReasoning: String,
    val recommendation: String,
    val provider: String,
    val confidence: Double
)

// ---- Reviews ----

@Serializable
data class CreateReviewRequest(
    val sellerId: String,
    val rating: Int,        // 1..5
    val body: String
)

@Serializable
data class ReviewDto(
    val id: String,
    val sellerId: String,
    val reviewerUserId: String,
    val rating: Int,
    val body: String,
    val createdAt: String
)

// ---- Chat ----

@Serializable
data class ChatMessageDto(
    val id: String,
    val senderUserId: String,
    val recipientUserId: String? = null,
    val role: String,       // "buyer" | "seller" | "ai" | "system"
    val content: String,
    val sessionId: String,
    val createdAt: String
)

@Serializable
data class PostChatMessageRequest(
    val sessionId: String,
    val content: String,
    val recipientUserId: String? = null,
    val role: String = "buyer"
)
