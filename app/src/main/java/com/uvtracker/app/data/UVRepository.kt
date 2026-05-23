package com.uvtracker.app.data

import android.location.Geocoder
import android.util.Log
import com.uvtracker.app.model.HourlyUV
import com.uvtracker.app.model.UVData
import com.uvtracker.app.utils.UVCalculator
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Locale
import java.util.concurrent.TimeUnit

sealed class UVResult {
    data class Success(val data: UVData) : UVResult()
    data class Error(val message: String) : UVResult()
    object Loading : UVResult()
}

class UVRepository(private val geocoder: Geocoder) {

    private val api: UVApiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(UVApiService::class.java)
    }

    /**
     * Fetch UV data from Open-Meteo and transform to app model.
     *
     * Open-Meteo uses ECMWF IFS model at 9 km resolution, updated every hour.
     * UV index values are validated against Brewer spectrophotometer ground stations.
     */
    suspend fun fetchUVData(latitude: Double, longitude: Double): UVResult {
        return try {
            val response = api.getUVForecast(latitude, longitude)

            if (!response.isSuccessful || response.body() == null) {
                return UVResult.Error("API error: HTTP ${response.code()}")
            }

            val body = response.body()!!
            val current = body.current
                ?: return UVResult.Error("No current UV data in response")

            val rawUVI = current.uvIndex ?: 0.0
            val rawClearSky = current.uvIndexClearSky ?: rawUVI

            // Compute cloud fraction and UV component split
            val safeBase = if (rawClearSky <= 0) 1.0 else rawClearSky
            val cloudFraction = (1.0 - rawUVI / safeBase).coerceIn(0.0, 1.0)
            val (directUV, diffuseUV) = UVCalculator.computeComponents(rawUVI, rawClearSky)

            // Reverse-geocode location name (best-effort; falls back to coordinates)
            val locationName = reverseGeocode(latitude, longitude)

            // Parse hourly forecast for today
            val hourlyForecast = parseHourly(body.hourly)

            val uvData = UVData(
                uvIndex = rawUVI,
                uvIndexClearSky = rawClearSky,
                latitude = latitude,
                longitude = longitude,
                locationName = locationName,
                timestamp = System.currentTimeMillis(),
                cloudFraction = cloudFraction,
                directUV = directUV,
                diffuseUV = diffuseUV,
                apiTime = current.time,
                hourlyForecast = hourlyForecast
            )

            Log.d(TAG, "UVI=$rawUVI clear=$rawClearSky cloud=${(cloudFraction*100).toInt()}% " +
                    "direct=${"%.2f".format(directUV)} diffuse=${"%.2f".format(diffuseUV)}")

            UVResult.Success(uvData)
        } catch (e: Exception) {
            Log.e(TAG, "Fetch failed", e)
            UVResult.Error(e.message ?: "Unknown network error")
        }
    }

    private fun reverseGeocode(lat: Double, lon: Double): String {
        return try {
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                when {
                    addr.locality != null && addr.countryCode != null ->
                        "${addr.locality}, ${addr.countryCode}"
                    addr.subAdminArea != null ->
                        addr.subAdminArea
                    addr.adminArea != null ->
                        addr.adminArea
                    else ->
                        "%.4f°, %.4f°".format(lat, lon)
                }
            } else {
                "%.4f°, %.4f°".format(lat, lon)
            }
        } catch (e: Exception) {
            "%.4f°, %.4f°".format(lat, lon)
        }
    }

    private fun parseHourly(hourly: HourlyData?): List<HourlyUV> {
        if (hourly == null) return emptyList()
        val times = hourly.time ?: return emptyList()
        val uviList = hourly.uvIndex ?: return emptyList()
        val uviClearList = hourly.uvIndexClearSky ?: return emptyList()

        return times.indices.mapNotNull { i ->
            val time = times.getOrNull(i) ?: return@mapNotNull null
            val uvi = uviList.getOrNull(i) ?: return@mapNotNull null
            val uviClear = uviClearList.getOrNull(i) ?: uvi

            // Parse hour from ISO-8601 "2024-06-01T14:00"
            val hour = time.substringAfter("T").substringBefore(":").toIntOrNull()
                ?: return@mapNotNull null

            HourlyUV(hour = hour, uvIndex = uvi ?: 0.0, uvIndexClearSky = uviClear ?: 0.0)
        }
    }

    companion object {
        private const val TAG = "UVRepository"
    }
}
