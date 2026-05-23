package com.uvtracker.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class LocationHelper(context: Context) {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    /**
     * One-shot: get the best available last location quickly, then request a
     * single fresh update if the last location is stale (>5 minutes).
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location? {
        return try {
            val last = fusedClient.lastLocation.await()
            val isStale = last == null ||
                    System.currentTimeMillis() - last.time > STALE_THRESHOLD_MS
            if (isStale) {
                // Request a fresh high-accuracy fix
                val fresh = fusedClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    null
                ).await()
                Log.d(TAG, "Fresh location: ${fresh?.latitude}, ${fresh?.longitude}")
                fresh ?: last
            } else {
                Log.d(TAG, "Using cached location: ${last.latitude}, ${last.longitude}")
                last
            }
        } catch (e: Exception) {
            Log.e(TAG, "Location fetch failed", e)
            null
        }
    }

    /**
     * Continuous location updates as a Flow.
     * Updates every [intervalMs] milliseconds. Used for live tracking mode.
     */
    @SuppressLint("MissingPermission")
    fun locationUpdates(intervalMs: Long = UPDATE_INTERVAL_MS): Flow<Location> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .setMaxUpdateDelayMillis(intervalMs * 2)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    Log.d(TAG, "Location update: ${loc.latitude}, ${loc.longitude} acc=${loc.accuracy}m")
                    trySend(loc)
                }
            }
        }

        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())

        awaitClose {
            Log.d(TAG, "Stopping location updates")
            fusedClient.removeLocationUpdates(callback)
        }
    }

    companion object {
        private const val TAG = "LocationHelper"
        private const val STALE_THRESHOLD_MS = 5 * 60 * 1000L  // 5 minutes
        const val UPDATE_INTERVAL_MS = 5 * 60 * 1000L          // refresh UV every 5 min
    }
}
