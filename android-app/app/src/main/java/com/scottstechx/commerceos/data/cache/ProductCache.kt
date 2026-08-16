package com.scottstechx.commerceos.data.cache

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.scottstechx.commerceos.data.remote.dto.ProductDto
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.productCacheStore by preferencesDataStore(name = "product_cache")

/**
 * Disk-backed cache of the buyer's product list. Read on cold start so
 * the Buyer screen can render immediately while the network call
 * resolves in the background. Written on every successful network
 * fetch. If the disk read fails, returns an empty list and the screen
 * shows the "no products" state.
 */
@Singleton
class ProductCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) {
    private val key = stringPreferencesKey("products_json")

    suspend fun read(): List<ProductDto> {
        val raw = runCatching {
            context.productCacheStore.data.first()[key]
        }.getOrNull() ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(ProductDto.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    suspend fun write(products: List<ProductDto>) {
        val encoded = json.encodeToString(
            ListSerializer(ProductDto.serializer()), products
        )
        runCatching {
            context.productCacheStore.edit { it[key] = encoded }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object ProductCacheModule {
    @Provides
    @Singleton
    fun provideProductCache(
        @ApplicationContext context: Context,
        json: Json
    ): ProductCache = ProductCache(context, json)
}
