package com.scottstechx.commerceos.security

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Play Integrity wrapper. The underlying
 * `com.google.android.play.core.integrity.IntegrityManagerFactory` API is
 * part of the `play-services-integrity` artifact, which has been removed
 * from this app's build because no recent coordinate (17.x, 18.x, 19.x,
 * 20.x) is currently published on Google Maven.
 *
 * Without the library, [requestToken] returns null — the login flow
 * continues without the integrity signal and the server's own trust
 * score absorbs the gap. To re-enable:
 *   1. Find a real coordinate (check https://maven.google.com)
 *   2. Add it to [versions] and [libraries] in gradle/libs.versions.toml
 *   3. Add implementation(libs.play.services.integrity) in app/build.gradle.kts
 *   4. Replace this stub with the previous IntegrityManagerFactory-based impl
 */
@Singleton
class PlayIntegrityClient @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun requestToken(nonce: String): String? = null
}

@Module
@InstallIn(SingletonComponent::class)
object PlayIntegrityModule {
    @Provides
    @Singleton
    fun providePlayIntegrity(
        @ApplicationContext context: Context
    ): PlayIntegrityClient = PlayIntegrityClient(context)
}
