package com.uvtracker.app.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * UV exposure profile for a specific environment or surface condition.
 *
 * The physics behind each scenario:
 *
 * TOTAL UV at a point = Direct_UV + Diffuse_UV + Reflected_UV
 *
 * Reflected_UV = surface_albedo * (Direct_UV + Diffuse_UV)
 *
 * For enclosed environments (car, indoors) we apply a transmission factor
 * that depends on the glazing type — glass blocks nearly all UVB but lets
 * through a significant fraction of UVA.
 *
 * UVA / UVB split at Earth's surface (clear sky):
 *   UVA ≈ 95 % of total UV energy
 *   UVB ≈  5 % of total UV energy
 *
 * This split shifts with solar elevation angle and cloud cover but using
 * 95/5 as a representative midday figure is standard in photobiology.
 */
@Parcelize
data class EnvironmentUV(
    /** Short display name ("Open Concrete", "Car Window", …). */
    val name: String,

    /** One-line context ("Reflected UV from concrete adds ~8 %"). */
    val description: String,

    /** Icon emoji for quick visual scanning. */
    val icon: String,

    /** Effective UV index the body actually receives in this environment. */
    val effectiveUVIndex: Double,

    /** Percentage of the base UV index this environment delivers (0–200 %). */
    val percentageOfBase: Int,

    /** Exposure type tag for colour-coding. */
    val exposureType: ExposureType,

    /** Breakdown of how the effective UV was computed. */
    val breakdown: UVBreakdown,

    /** Minutes to potential skin damage for Type II (fair) skin. */
    val burnTimeMinutes: Int,

    /** SPF / protection guidance specific to this environment. */
    val protectionTip: String
) : Parcelable

@Parcelize
data class UVBreakdown(
    val directUV: Double,
    val diffuseUV: Double,
    val reflectedUV: Double,
    /** UV blocked / filtered (negative contribution, e.g. glass). */
    val filteredUV: Double = 0.0,
    /** Human label for the reflection source ("Concrete", "Snow", "Glass"). */
    val reflectionSource: String = ""
) : Parcelable

enum class ExposureType(val colorHex: String) {
    AMPLIFIED("#FF5722"),   // higher than base (snow, sand, concrete)
    BASELINE("#FF9800"),    // roughly equal to base (open grass)
    REDUCED("#4CAF50"),     // meaningfully lower (shade, indoors)
    MINIMAL("#2196F3"),     // very low (UV-film glass, deep indoors)
    WARNING("#9C27B0")      // unusual / counterintuitive risk (car window UVA)
}
