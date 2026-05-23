package com.uvtracker.app.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Core UV measurement data fetched from the Open-Meteo API.
 *
 * Open-Meteo provides:
 *   - uv_index        : actual UV index at surface (accounts for clouds, ozone, altitude)
 *   - uv_index_clear_sky: UV index under perfectly clear skies (theoretical max)
 *
 * From these two values we derive:
 *   - cloudAttenuation = 1 - (uv_index / uv_index_clear_sky)
 *   - directUV         ≈ 60% of surface UV on a clear day (rest is diffuse sky scatter)
 *   - diffuseUV        ≈ 40% of surface UV (increases as clouds thicken)
 */
@Parcelize
data class UVData(
    /** UV index at the Earth's surface (cloud-attenuated). Range 0–20+. */
    val uvIndex: Double,

    /** UV index under perfectly clear skies (theoretical ceiling). */
    val uvIndexClearSky: Double,

    /** Latitude of measurement point. */
    val latitude: Double,

    /** Longitude of measurement point. */
    val longitude: Double,

    /** Human-readable location name (reverse-geocoded or coordinate string). */
    val locationName: String,

    /** Unix epoch millis when this reading was fetched. */
    val timestamp: Long,

    /** Cloud-cover fraction 0.0–1.0 (derived: 1 - uvIndex/uvIndexClearSky, clamped). */
    val cloudFraction: Double,

    /**
     * Direct-beam UV component.
     *
     * On a clear day roughly 60% of surface UV arrives as a direct beam from the sun.
     * As cloud cover increases the direct beam weakens and diffuse scatter grows.
     * Formula: directUV = uvIndex * max(0.0, (1.0 - cloudFraction * 0.7)) * 0.6
     */
    val directUV: Double,

    /**
     * Diffuse (indirect / sky-scattered) UV component.
     *
     * Scattered from the atmosphere in all directions — reaches you even in shade.
     * Formula: diffuseUV = uvIndex - directUV
     */
    val diffuseUV: Double,

    /** ISO-8601 UTC timestamp returned by the API ("2024-06-01T12:00"). */
    val apiTime: String,

    /** Hourly UV forecast for the rest of the day. */
    val hourlyForecast: List<HourlyUV> = emptyList()
) : Parcelable {

    /** Risk category derived from WHO UV index scale. */
    val riskLevel: UVRiskLevel
        get() = UVRiskLevel.fromIndex(uvIndex)

    /** Minutes of unprotected fair-skin exposure before sunburn begins. */
    val burnTimeMinutes: Int
        get() = if (uvIndex <= 0) Int.MAX_VALUE
                else (200.0 / (uvIndex * 3)).toInt().coerceAtLeast(1)
}

@Parcelize
data class HourlyUV(
    val hour: Int,
    val uvIndex: Double,
    val uvIndexClearSky: Double
) : Parcelable

enum class UVRiskLevel(
    val label: String,
    val colorHex: String,
    val recommendation: String,
    val minIndex: Double,
    val maxIndex: Double
) {
    LOW(
        label = "Low",
        colorHex = "#4CAF50",
        recommendation = "Minimal protection needed. Safe to be outside.",
        minIndex = 0.0,
        maxIndex = 2.9
    ),
    MODERATE(
        label = "Moderate",
        colorHex = "#FFEB3B",
        recommendation = "Seek shade during midday. Wear sunscreen SPF 30+, hat, and sunglasses.",
        minIndex = 3.0,
        maxIndex = 5.9
    ),
    HIGH(
        label = "High",
        colorHex = "#FF9800",
        recommendation = "Cover up, use SPF 50+ sunscreen. Reduce time in the sun 10am–4pm.",
        minIndex = 6.0,
        maxIndex = 7.9
    ),
    VERY_HIGH(
        label = "Very High",
        colorHex = "#F44336",
        recommendation = "Extra protection essential. Avoid sun 10am–4pm. SPF 50+ every 2 hours.",
        minIndex = 8.0,
        maxIndex = 10.9
    ),
    EXTREME(
        label = "Extreme",
        colorHex = "#9C27B0",
        recommendation = "Stay indoors during peak hours. If outside: SPF 50+, full cover, shade only.",
        minIndex = 11.0,
        maxIndex = Double.MAX_VALUE
    );

    companion object {
        fun fromIndex(index: Double): UVRiskLevel =
            entries.firstOrNull { index >= it.minIndex && index <= it.maxIndex } ?: EXTREME
    }
}
