package com.uvtracker.app.utils

import com.uvtracker.app.model.EnvironmentUV
import com.uvtracker.app.model.ExposureType
import com.uvtracker.app.model.UVBreakdown
import com.uvtracker.app.model.UVData
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * UVCalculator
 * ============
 * All surface-physics and environment UV computations live here.
 *
 * ─── Photobiology foundations ───────────────────────────────────────────────
 *
 * 1. UV INDEX (UVI)
 *    Dimensionless, linear scale that quantifies UV erythema-weighted irradiance
 *    at the Earth's surface. Defined by WHO/WMO (CIE s007).
 *    1 UVI ≈ 25 mW/m² of erythemal UV.
 *
 * 2. DIRECT vs DIFFUSE SPLIT
 *    On a clear day at midday:
 *      Direct (beam) UV    ≈ 55–65 % of surface UV
 *      Diffuse (sky) UV    ≈ 35–45 % of surface UV
 *    We use 60/40 as the reference clear-sky split and adjust for cloud cover:
 *      cloudFraction = 1 - (uvIndex / uvIndexClearSky)   [clamped 0–1]
 *      directUV = uvIndex × (0.60 - 0.50 × cloudFraction)   [min 0]
 *      diffuseUV = uvIndex - directUV                         [always ≥ 0]
 *    Under heavy overcast (cloudFraction→1) essentially all UV is diffuse.
 *
 * 3. SURFACE REFLECTIVITY (UV ALBEDO)
 *    UV albedo values from Blumthaler & Ambach (1988), Feister & Grewe (1995),
 *    and UNEP/WHO Environmental Health Criteria 160:
 *      Fresh snow        80–90 %   → use 85 %
 *      Sand / beach      15–25 %   → use 20 %
 *      Concrete/asphalt   5–9  %   → use  8 %
 *      Water (flat)       5–10 %   → use  8 %  (much higher at glancing angles)
 *      Grass / soil        1–3 %   → use  3 %
 *    Reflected UV = albedo × (directUV + diffuseUV)
 *
 * 4. GLASS TRANSMISSION
 *    Standard soda-lime glass (architectural, car windows):
 *      UVA (315–400 nm) transmission ≈ 63 %   (tempered side glass)
 *      UVB (280–315 nm) transmission ≈  2 %   (virtually opaque)
 *    Laminated windshield:
 *      UVA ≈ 42 %,  UVB ≈  0.7 %
 *    UV-protective film / tint:
 *      UVA ≈  1 %,  UVB ≈  0.1 %
 *    Sources: Moehrle (2008), Hampton et al. (2004), IARC Monograph 100D.
 *
 *    Because UVA ≈ 95 % and UVB ≈ 5 % of erythema-weighted UV:
 *      eff_transmission = 0.95 × UVA_trans + 0.05 × UVB_trans
 *    Side window : 0.95×0.63 + 0.05×0.02 ≈ 0.599 + 0.001 = 0.60  → ~60 %
 *    Windshield  : 0.95×0.42 + 0.05×0.007         ≈ 0.40  → ~40 %
 *    UV film     : 0.95×0.01 + 0.05×0.001         ≈ 0.010 → ~1  %
 *
 * 5. MINIMAL ERYTHEMAL DOSE (MED) → BURN TIME
 *    MED for Fitzpatrick skin Type II (fair/burns easily) ≈ 250 J/m² erythemal
 *    Irradiance at UVI=1 ≈ 25 mW/m²
 *    Time to 1 MED (minutes) = 250 / (uvIndex × 25 × 60) × 1000
 *                             = 200 / (uvIndex × 3)        [simplified]
 *    Valid for unprotected Type II skin outdoors.
 *
 * ────────────────────────────────────────────────────────────────────────────
 */
object UVCalculator {

    // ── UV component split ────────────────────────────────────────────────────

    /**
     * Compute direct and diffuse UV components from total UV index and
     * the clear-sky ceiling.
     */
    fun computeComponents(uvIndex: Double, uvIndexClearSky: Double): Pair<Double, Double> {
        val safeBase = if (uvIndexClearSky <= 0) 1.0 else uvIndexClearSky
        val cloudFraction = (1.0 - uvIndex / safeBase).coerceIn(0.0, 1.0)

        // Direct beam fraction decreases as cloud cover rises
        val directFraction = (0.60 - 0.50 * cloudFraction).coerceAtLeast(0.0)
        val directUV = uvIndex * directFraction
        val diffuseUV = max(0.0, uvIndex - directUV)
        return Pair(directUV, diffuseUV)
    }

    // ── Burn time ─────────────────────────────────────────────────────────────

    private fun burnTime(effectiveUVI: Double): Int =
        if (effectiveUVI <= 0.0) Int.MAX_VALUE
        else (200.0 / (effectiveUVI * 3.0)).roundToInt().coerceAtLeast(1)

    // ── Environment profiles ──────────────────────────────────────────────────

    /**
     * Returns the full list of environment-specific UV profiles,
     * ordered from highest effective exposure to lowest.
     */
    fun computeAllEnvironments(data: UVData): List<EnvironmentUV> {
        val uvi = data.uvIndex
        val d = data.directUV
        val f = data.diffuseUV

        return listOf(
            computeSnow(uvi, d, f),
            computeSand(uvi, d, f),
            computeOpenGrass(uvi, d, f),
            computeConcrete(uvi, d, f),
            computeNearWater(uvi, d, f),
            computeCarSideWindow(uvi, d, f),
            computeCarWindshield(uvi, d, f),
            computeShade(uvi, d, f),
            computeTreeCanopy(uvi, d, f),
            computeCarUVFilm(uvi, d, f),
            computeIndoorsNearWindow(uvi, d, f),
            computeIndoorsAwayFromWindow(uvi, d, f),
        ).sortedByDescending { it.effectiveUVIndex }
    }

    // ── Individual environment calculators ────────────────────────────────────

    /** Open area on grass / dark soil – reference/baseline scenario. */
    private fun computeOpenGrass(uvi: Double, direct: Double, diffuse: Double): EnvironmentUV {
        val albedo = 0.03
        val reflected = albedo * (direct + diffuse)
        val effective = direct + diffuse + reflected
        return EnvironmentUV(
            name = "Open Sky (Grass)",
            description = "Full sun, standing on grass or soil. Low surface reflection.",
            icon = "🌿",
            effectiveUVIndex = effective,
            percentageOfBase = pct(effective, uvi),
            exposureType = ExposureType.BASELINE,
            breakdown = UVBreakdown(direct, diffuse, reflected, reflectionSource = "Grass (~3 %)"),
            burnTimeMinutes = burnTime(effective),
            protectionTip = "SPF 30+ sunscreen. Reapply every 2 hours."
        )
    }

    /** Open concrete, pavement, or asphalt surfaces. */
    private fun computeConcrete(uvi: Double, direct: Double, diffuse: Double): EnvironmentUV {
        val albedo = 0.08
        val reflected = albedo * (direct + diffuse)
        val effective = direct + diffuse + reflected
        return EnvironmentUV(
            name = "Open Concrete / Pavement",
            description = "Standing on concrete or asphalt. Reflective surface adds ~8 % UV.",
            icon = "🏙️",
            effectiveUVIndex = effective,
            percentageOfBase = pct(effective, uvi),
            exposureType = ExposureType.AMPLIFIED,
            breakdown = UVBreakdown(direct, diffuse, reflected, reflectionSource = "Concrete (~8 %)"),
            burnTimeMinutes = burnTime(effective),
            protectionTip = "Urban environments feel cooler but UV is higher than grass. SPF 50+ recommended."
        )
    }

    /** Beach or desert sand environment. */
    private fun computeSand(uvi: Double, direct: Double, diffuse: Double): EnvironmentUV {
        val albedo = 0.20
        val reflected = albedo * (direct + diffuse)
        val effective = direct + diffuse + reflected
        return EnvironmentUV(
            name = "Beach / Desert Sand",
            description = "Dry sand reflects up to 20 % UV. Combined with water glare, risk is very high.",
            icon = "🏖️",
            effectiveUVIndex = effective,
            percentageOfBase = pct(effective, uvi),
            exposureType = ExposureType.AMPLIFIED,
            breakdown = UVBreakdown(direct, diffuse, reflected, reflectionSource = "Sand (~20 %)"),
            burnTimeMinutes = burnTime(effective),
            protectionTip = "SPF 50+ every 90 min. Rash guard recommended. Umbrella halves diffuse UV."
        )
    }

    /** Fresh snow – highest natural UV amplifier. */
    private fun computeSnow(uvi: Double, direct: Double, diffuse: Double): EnvironmentUV {
        val albedo = 0.85
        val reflected = albedo * (direct + diffuse)
        val effective = direct + diffuse + reflected
        return EnvironmentUV(
            name = "Snow / Ski Slope",
            description = "Fresh snow reflects up to 85 % UV. Risk can nearly double the base UVI.",
            icon = "⛷️",
            effectiveUVIndex = effective,
            percentageOfBase = pct(effective, uvi),
            exposureType = ExposureType.AMPLIFIED,
            breakdown = UVBreakdown(direct, diffuse, reflected, reflectionSource = "Snow (~85 %)"),
            burnTimeMinutes = burnTime(effective),
            protectionTip = "SPF 50+ on all exposed skin including under chin and nose. UV goggles mandatory. Altitude multiplies risk further."
        )
    }

    /** Near water bodies – significant reflection especially at glancing angles. */
    private fun computeNearWater(uvi: Double, direct: Double, diffuse: Double): EnvironmentUV {
        val albedo = 0.10  // calm water; can reach 40 % at low sun angles
        val reflected = albedo * (direct + diffuse)
        val effective = direct + diffuse + reflected
        return EnvironmentUV(
            name = "Near Water (Lake / Sea)",
            description = "Calm water reflects ~10 % UV. Glare at low sun angles can reach 40 %.",
            icon = "🌊",
            effectiveUVIndex = effective,
            percentageOfBase = pct(effective, uvi),
            exposureType = ExposureType.AMPLIFIED,
            breakdown = UVBreakdown(direct, diffuse, reflected, reflectionSource = "Water (~10 %)"),
            burnTimeMinutes = burnTime(effective),
            protectionTip = "UV-protective sunglasses essential. Water cools you but does not block UV."
        )
    }

    /**
     * Car — side window (tempered glass, no UV film).
     *
     * Tempered soda-lime glass:
     *   UVA transmission  ≈ 63 %
     *   UVB transmission  ≈  2 %
     * eff = 0.95 × 0.63 + 0.05 × 0.02 = 0.5985 + 0.001 ≈ 0.60
     *
     * Note: UVA causes tanning, photoaging, and some DNA damage, even though
     * it does NOT cause immediate sunburn. Long commutes lead to asymmetric aging.
     */
    private fun computeCarSideWindow(uvi: Double, direct: Double, diffuse: Double): EnvironmentUV {
        // Only diffuse UV enters from the sky above; direct beam mostly blocked by roof
        // Side window sees: full diffuse sky + fraction of direct from near-horizontal beam
        // Conservatively: total incident = 0.55 × uvi (geometry; side gets less sky dome)
        val windowIncident = (direct * 0.30 + diffuse * 0.70)  // side geometry weight
        val uvaTrans = 0.63
        val uvbTrans = 0.02
        val effTrans = 0.95 * uvaTrans + 0.05 * uvbTrans
        val effective = windowIncident * effTrans
        val filtered = windowIncident - effective
        return EnvironmentUV(
            name = "Car — Side Window Seat",
            description = "Tempered glass blocks UVB almost entirely but transmits ~63 % of UVA — the aging and DNA-damaging ray.",
            icon = "🚗",
            effectiveUVIndex = effective,
            percentageOfBase = pct(effective, uvi),
            exposureType = ExposureType.WARNING,
            breakdown = UVBreakdown(direct * 0.30 * effTrans, diffuse * 0.70 * effTrans, 0.0, filtered, "Side glass (UVA ~63 %, UVB ~2 %)"),
            burnTimeMinutes = burnTime(effective),
            protectionTip = "You CAN get photoaged and sunburned through an untinted side window. Apply SPF 30+ on arms and face for drives >20 min. UV-blocking window film eliminates this risk."
        )
    }

    /**
     * Car — windshield (laminated safety glass).
     *
     * Laminated PVB interlayer blocks nearly all UVB and ~58 % of UVA:
     *   UVA transmission  ≈ 42 %
     *   UVB transmission  ≈  0.7 %
     * eff = 0.95 × 0.42 + 0.05 × 0.007 ≈ 0.399 + 0.0004 ≈ 0.40
     */
    private fun computeCarWindshield(uvi: Double, direct: Double, diffuse: Double): EnvironmentUV {
        val windowIncident = direct * 0.6 + diffuse * 0.5  // front window sees more direct beam
        val uvaTrans = 0.42
        val uvbTrans = 0.007
        val effTrans = 0.95 * uvaTrans + 0.05 * uvbTrans
        val effective = windowIncident * effTrans
        val filtered = windowIncident - effective
        return EnvironmentUV(
            name = "Car — Windshield (Laminated)",
            description = "Laminated windshield's PVB layer blocks ~60 % of UVA and almost all UVB vs side windows.",
            icon = "🚙",
            effectiveUVIndex = effective,
            percentageOfBase = pct(effective, uvi),
            exposureType = ExposureType.REDUCED,
            breakdown = UVBreakdown(direct * 0.6 * effTrans, diffuse * 0.5 * effTrans, 0.0, filtered, "Windshield laminate (UVA ~42 %, UVB ~0.7 %)"),
            burnTimeMinutes = burnTime(effective),
            protectionTip = "Windshields offer better UV protection than side windows. Still use sun visor and SPF on long trips."
        )
    }

    /**
     * Car with UV-protective window film / aftermarket tint.
     * Blocks >99 % of both UVA and UVB.
     */
    private fun computeCarUVFilm(uvi: Double, direct: Double, diffuse: Double): EnvironmentUV {
        val windowIncident = direct * 0.30 + diffuse * 0.70
        val effTrans = 0.01  // ~99 % blocked
        val effective = windowIncident * effTrans
        val filtered = windowIncident - effective
        return EnvironmentUV(
            name = "Car — UV-Protective Window Film",
            description = "Quality UV-blocking film or ceramic tint cuts UV transmission to ~1 %. Excellent protection.",
            icon = "🪟",
            effectiveUVIndex = effective,
            percentageOfBase = pct(effective, uvi),
            exposureType = ExposureType.MINIMAL,
            breakdown = UVBreakdown(0.0, effective, 0.0, filtered, "UV film (<1 % UVA & UVB)"),
            burnTimeMinutes = burnTime(effective),
            protectionTip = "UV film is one of the best investments for daily driving. Look for 3M Crystalline, LLumar, or similar NFRC-rated products."
        )
    }

    /** Open shade – direct beam blocked, sky diffuse still reaches you. */
    private fun computeShade(uvi: Double, direct: Double, diffuse: Double): EnvironmentUV {
        // In open shade the direct beam is blocked; sky diffuse + horizon scatter still arrive
        // Typically ~50 % of total surface UV in shade (ICNIRP, WHO 2002)
        val shadeAlbedo = 0.03
        val effective = diffuse * 1.0 + diffuse * shadeAlbedo   // reflected from ground in shade area
        return EnvironmentUV(
            name = "Open Shade (Building / Wall)",
            description = "Direct sun blocked; scattered sky UV still reaches you — typically ~50 % of full-sun UVI.",
            icon = "🏛️",
            effectiveUVIndex = effective,
            percentageOfBase = pct(effective, uvi),
            exposureType = ExposureType.REDUCED,
            breakdown = UVBreakdown(0.0, diffuse, diffuse * shadeAlbedo, 0.0, "Wall / structure shadow"),
            burnTimeMinutes = burnTime(effective),
            protectionTip = "Shade is the first line of defence, but not a complete shield. SPF 30 still recommended in open shade."
        )
    }

    /** Under dense tree canopy. */
    private fun computeTreeCanopy(uvi: Double, direct: Double, diffuse: Double): EnvironmentUV {
        // Dense tree canopy reduces UV by 50–75 % (Parisi & Kimlin 2004)
        // Sparse canopy ≈ 0.50 reduction; dense ≈ 0.25
        val canopyFactor = 0.22   // dense forest average
        val effective = uvi * canopyFactor
        return EnvironmentUV(
            name = "Dense Tree Canopy",
            description = "Dense foliage filters ~75–80 % of UV. Effective UVI is roughly 20–25 % of open sky.",
            icon = "🌳",
            effectiveUVIndex = effective,
            percentageOfBase = pct(effective, uvi),
            exposureType = ExposureType.REDUCED,
            breakdown = UVBreakdown(direct * canopyFactor * 0.3, diffuse * canopyFactor * 0.7, 0.0, uvi * (1 - canopyFactor), "Leaf canopy (~75–80 % attenuation)"),
            burnTimeMinutes = burnTime(effective),
            protectionTip = "Excellent natural protection. Gap in canopy ('sun fleck') can deliver a brief high-dose pulse — move to avoid."
        )
    }

    /**
     * Indoors near a standard window (soda-lime glass, no UV film).
     * Similar transmission to a car side window.
     */
    private fun computeIndoorsNearWindow(uvi: Double, direct: Double, diffuse: Double): EnvironmentUV {
        val windowIncident = diffuse * 0.60  // window typically sees sky diffuse, not full direct beam
        val uvaTrans = 0.63
        val uvbTrans = 0.02
        val effTrans = 0.95 * uvaTrans + 0.05 * uvbTrans
        val effective = windowIncident * effTrans
        val filtered = windowIncident - effective
        return EnvironmentUV(
            name = "Indoors Near Window",
            description = "Standard window glass blocks UVB but transmits up to 63 % of UVA into the room.",
            icon = "🪟",
            effectiveUVIndex = effective,
            percentageOfBase = pct(effective, uvi),
            exposureType = ExposureType.WARNING,
            breakdown = UVBreakdown(0.0, effective, 0.0, filtered, "Window glass (UVA ~63 %)"),
            burnTimeMinutes = burnTime(effective),
            protectionTip = "Prolonged exposure near a sun-facing window can cause photoaging. SPF 30 moisturiser on face/hands is good practice."
        )
    }

    /** Away from windows, indoors – negligible UV. */
    private fun computeIndoorsAwayFromWindow(uvi: Double, @Suppress("UNUSED_PARAMETER") direct: Double, @Suppress("UNUSED_PARAMETER") diffuse: Double): EnvironmentUV {
        val effective = uvi * 0.01   // residual scatter, fluorescent sources negligible vs sun
        return EnvironmentUV(
            name = "Indoors (Away from Windows)",
            description = "Walls, floors and ceilings block virtually all UV. Artificial lighting contributes negligible UV.",
            icon = "🏠",
            effectiveUVIndex = effective,
            percentageOfBase = pct(effective, uvi),
            exposureType = ExposureType.MINIMAL,
            breakdown = UVBreakdown(0.0, effective, 0.0, uvi * 0.99, "Structural walls (>99 %)"),
            burnTimeMinutes = Int.MAX_VALUE,
            protectionTip = "No UV protection needed. Vitamin D synthesis requires going outside."
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun pct(effective: Double, base: Double): Int =
        if (base <= 0) 0 else (effective / base * 100).roundToInt()
}
