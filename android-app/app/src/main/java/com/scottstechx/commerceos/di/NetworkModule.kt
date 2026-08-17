package com.scottstechx.commerceos.di

import com.scottstechx.commerceos.BuildConfig
import com.scottstechx.commerceos.data.auth.AuthStore
import com.scottstechx.commerceos.data.remote.AuthInterceptor
import com.scottstechx.commerceos.data.remote.ScottsTechXApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttp(authStore: AuthStore): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
            else HttpLoggingInterceptor.Level.NONE
        }
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(authStore))
            .addInterceptor(logging)
        // Certificate pinning for the production API host only.
        // In debug, we don't pin so the emulator's MITM proxy works.
        if (!BuildConfig.DEBUG) {
            // SHA-256 pin placeholder. Replace at release-cut time with
            // the real pin from `openssl s_client -connect host:443 | openssl x509 -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | base64`.
            // Two pins are required (primary + backup) so a key rotation doesn't brick clients.
            val pinner = okhttp3.CertificatePinner.Builder()
                .add("api.scottstechx.example", "sha256/REPLACE_WITH_REAL_PIN=")
                .add("api.scottstechx.example", "sha256/REPLACE_WITH_BACKUP_PIN=")
                .build()
            builder.certificatePinner(pinner)
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit {
        val mediaType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(mediaType))
            .build()
    }

    @Provides
    @Singleton
    fun provideApi(retrofit: Retrofit): ScottsTechXApi =
        retrofit.create(ScottsTechXApi::class.java)
}
