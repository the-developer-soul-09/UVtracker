package com.uvtracker.app.data

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

// ─── Open-Meteo API Response Models ─────────────────────────────────────────

data class OpenMeteoResponse(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("timezone") val timezone: String,
    @SerializedName("current") val current: CurrentUV?,
    @SerializedName("hourly") val hourly: HourlyData?,
    @SerializedName("current_units") val currentUnits: CurrentUnits?
)

data class CurrentUV(
    @SerializedName("time") val time: String,
    @SerializedName("uv_index") val uvIndex: Double?,
    @SerializedName("uv_index_clear_sky") val uvIndexClearSky: Double?
)

data class HourlyData(
    @SerializedName("time") val time: List<String>?,
    @SerializedName("uv_index") val uvIndex: List<Double?>?,
    @SerializedName("uv_index_clear_sky") val uvIndexClearSky: List<Double?>?
)

data class CurrentUnits(
    @SerializedName("uv_index") val uvIndex: String?,
    @SerializedName("uv_index_clear_sky") val uvIndexClearSky: String?
)

// ─── Retrofit Interface ───────────────────────────────────────────────────────

/**
 * Open-Meteo API — completely free, no API key required.
 * Documentation: https://open-meteo.com/en/docs
 *
 * UV index is a WMO-standard erythema-weighted irradiance value
 * calculated by ECMWF/GFS models and validated against ground stations.
 */
interface UVApiService {

    @GET("v1/forecast")
    suspend fun getUVForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "uv_index,uv_index_clear_sky",
        @Query("hourly") hourly: String = "uv_index,uv_index_clear_sky",
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 1
    ): Response<OpenMeteoResponse>
}
