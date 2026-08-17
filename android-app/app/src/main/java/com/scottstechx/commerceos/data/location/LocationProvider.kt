package com.scottstechx.commerceos.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Thin wrapper over FusedLocationProviderClient. The Driver screen calls
 * [currentLocation] when the user taps "Use my location" on the POD card.
 *
 * Returns null if the ACCESS_FINE_LOCATION permission is missing, the
 * device has no location fix, or the request is cancelled.
 */
@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val client: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Convenience wrapper returning a sealed [Fix] so callers don't have
     * to null-check and so the permission-denied case is structurally
     * distinct from "no GPS fix yet". Existing callers can keep using
     * [currentLocation] if they prefer nullable.
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrent(): Fix {
        if (!hasPermission()) return Fix.Denied
        return when (val loc = currentLocation()) {
            null -> Fix.Unavailable
            else -> Fix.Success(loc.latitude, loc.longitude)
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): Location? {
        if (!hasPermission()) return null
        val cts = CancellationTokenSource()
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { cts.cancel() }
            client.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cts.token
            ).addOnSuccessListener { loc -> cont.resume(loc) }
                .addOnFailureListener { cont.resume(null) }
                .addOnCanceledListener { cont.resume(null) }
        }
    }

    /**
     * Result of [getCurrent]. Distinguished from a raw nullable
     * because "permission denied" is a user-actionable outcome and
     * "GPS unavailable" is a transient device state — the UI needs to
     * route them differently.
     */
    sealed class Fix {
        data class Success(val lat: Double, val lng: Double) : Fix()
        object Denied : Fix()
        object Unavailable : Fix()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object LocationModule {
    // LocationProvider is @Inject + @Singleton constructed with the
    // @ApplicationContext, so no explicit @Provides is needed. This
    // module exists so future location-related deps (e.g. a geocoder)
    // can be added without touching LocationProvider's constructor.
    @Provides
    @Singleton
    fun provideLocationProvider(
        @ApplicationContext context: Context
    ): LocationProvider = LocationProvider(context)
}
