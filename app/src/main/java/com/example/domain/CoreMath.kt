package com.example.domain

import java.math.BigDecimal
import java.math.RoundingMode

enum class DistanceMode {
    PICKUP_AND_TRIP, // totalKm = pickup + trip
    TRIP_ONLY        // totalKm = trip
}

enum class JudgeBasis {
    GROSS,
    NET
}

enum class IndicatorColor {
    GREEN,
    ORANGE,
    RED,
    GREY
}

data class ChipResult(
    val price: BigDecimal,
    val perKm: BigDecimal,
    val color: IndicatorColor
)

sealed class CoreMathResult {
    data class Success(
        val judgedPerKm: BigDecimal,
        val otherPerKm: BigDecimal?,
        val color: IndicatorColor,
        val minAcceptablePrice: Int,
        val chips: List<ChipResult>,
        val totalKm: BigDecimal,
        val displayText: String
    ) : CoreMathResult()

    data class Missing(val reason: String = "؟") : CoreMathResult()
    data class Suspicious(val reason: String = "قراءة مشكوك فيها") : CoreMathResult()
}

/**
 * Pure Kotlin mathematical calculations for price-per-km.
 * Strict zero-Android dependency rule.
 * All monetary and distance figures use BigDecimal built from Strings.
 */
object CoreMath {
    private val ZERO = BigDecimal.ZERO
    private val HUNDRED = BigDecimal("100")
    private val MAX_GROSS_RATE = BigDecimal("60.0000")

    fun calculate(
        priceStr: String?,
        pickupKmStr: String?,
        tripKmStr: String?,
        myMinimumPerKm: BigDecimal = BigDecimal("3.0"),
        tolerancePercent: Int = 10,
        distanceMode: DistanceMode = DistanceMode.PICKUP_AND_TRIP,
        deductCommission: Boolean = false,
        commissionPercent: Int = 10,
        judgeBy: JudgeBasis = JudgeBasis.GROSS,
        suggestedPrices: List<BigDecimal> = emptyList()
    ): CoreMathResult {
        val price = priceStr?.let { safeBigDecimal(it) }
        val pickupKm = pickupKmStr?.let { safeBigDecimal(it) }
        val tripKm = tripKmStr?.let { safeBigDecimal(it) }
        return calculate(
            price = price,
            pickupKm = pickupKm,
            tripKm = tripKm,
            myMinimumPerKm = myMinimumPerKm,
            tolerancePercent = tolerancePercent,
            distanceMode = distanceMode,
            deductCommission = deductCommission,
            commissionPercent = commissionPercent,
            judgeBy = judgeBy,
            suggestedPrices = suggestedPrices
        )
    }

    fun calculate(
        price: BigDecimal?,
        pickupKm: BigDecimal?,
        tripKm: BigDecimal?,
        myMinimumPerKm: BigDecimal = BigDecimal("3.0"),
        tolerancePercent: Int = 10,
        distanceMode: DistanceMode = DistanceMode.PICKUP_AND_TRIP,
        deductCommission: Boolean = false,
        commissionPercent: Int = 10,
        judgeBy: JudgeBasis = JudgeBasis.GROSS,
        suggestedPrices: List<BigDecimal> = emptyList()
    ): CoreMathResult {
        // Missing required inputs check
        if (price == null || tripKm == null) {
            return CoreMathResult.Missing()
        }
        if (distanceMode == DistanceMode.PICKUP_AND_TRIP && pickupKm == null) {
            return CoreMathResult.Missing()
        }

        // Validity checks: non-positive price or trip
        if (price <= ZERO || tripKm <= ZERO) {
            return CoreMathResult.Suspicious()
        }

        // Negative pickup is invalid
        if (pickupKm != null && pickupKm < ZERO) {
            return CoreMathResult.Suspicious()
        }

        // Calculate total distance based on mode
        val totalKm = if (distanceMode == DistanceMode.PICKUP_AND_TRIP) {
            (pickupKm ?: ZERO).add(tripKm)
        } else {
            tripKm
        }

        if (totalKm <= ZERO) {
            return CoreMathResult.Suspicious()
        }

        // Implied gross check at scale 4: prices > 60 EGP/km are suspicious
        val impliedGrossRate = price.divide(totalKm, 4, RoundingMode.HALF_UP)
        if (impliedGrossRate > MAX_GROSS_RATE) {
            return CoreMathResult.Suspicious()
        }

        val clampedCommission = commissionPercent.coerceIn(0, 50)

        // Gross per-km: price / totalKm with ONE division, scale 1, HALF_UP
        // WHY HALF_UP: Standard rounding ensuring values like 2.95 correctly round up to 3.0
        val grossPerKm = price.divide(totalKm, 1, RoundingMode.HALF_UP)

        // Net per-km: price * (100 - commissionPercent) / (totalKm * 100), ONE division scale 1 HALF_UP
        val netNumerator = price.multiply(BigDecimal(100 - clampedCommission))
        val netDenominator = totalKm.multiply(HUNDRED)
        val netPerKm = netNumerator.divide(netDenominator, 1, RoundingMode.HALF_UP)

        // Determine effective judged basis
        // When deductCommission is false, behavior is identical to Gross and Net is not shown
        val effectiveJudgeBy = if (deductCommission) judgeBy else JudgeBasis.GROSS
        val judgedPerKm = if (effectiveJudgeBy == JudgeBasis.NET) netPerKm else grossPerKm
        val otherPerKm = if (deductCommission) {
            if (effectiveJudgeBy == JudgeBasis.NET) grossPerKm else netPerKm
        } else {
            null
        }

        // Minimum threshold rounded to scale 1
        val minRounded = myMinimumPerKm.setScale(1, RoundingMode.HALF_UP)
        val color = determineColor(judgedPerKm, minRounded, tolerancePercent)

        // Smallest integer price yielding judgedPerKm >= minRounded
        val minAcceptablePrice = computeMinAcceptablePrice(
            totalKm = totalKm,
            minRounded = minRounded,
            useNet = (effectiveJudgeBy == JudgeBasis.NET),
            commissionPercent = clampedCommission
        )

        // Process up to 3 suggested price chips
        val chipResults = suggestedPrices.take(3).mapNotNull { chipPrice ->
            if (chipPrice <= ZERO) return@mapNotNull null
            val chipGrossImplied = chipPrice.divide(totalKm, 4, RoundingMode.HALF_UP)
            if (chipGrossImplied > MAX_GROSS_RATE) return@mapNotNull null

            val chipRate = if (effectiveJudgeBy == JudgeBasis.NET) {
                val chipNetNum = chipPrice.multiply(BigDecimal(100 - clampedCommission))
                chipNetNum.divide(netDenominator, 1, RoundingMode.HALF_UP)
            } else {
                chipPrice.divide(totalKm, 1, RoundingMode.HALF_UP)
            }
            val chipColor = determineColor(chipRate, minRounded, tolerancePercent)
            ChipResult(price = chipPrice, perKm = chipRate, color = chipColor)
        }

        return CoreMathResult.Success(
            judgedPerKm = judgedPerKm,
            otherPerKm = otherPerKm,
            color = color,
            minAcceptablePrice = minAcceptablePrice,
            chips = chipResults,
            totalKm = totalKm,
            displayText = "$judgedPerKm ج/كم"
        )
    }

    /**
     * Colour decision on displayed rounded value m1:
     * GREEN if m1 >= minRounded;
     * ORANGE if m1 >= minRounded * (1 - tolerance/100);
     * else RED.
     */
    fun determineColor(
        m1: BigDecimal,
        minRounded: BigDecimal,
        tolerancePercent: Int
    ): IndicatorColor {
        if (m1 >= minRounded) {
            return IndicatorColor.GREEN
        }
        val clampedTolerance = tolerancePercent.coerceIn(0, 100)
        val orangeThreshold = minRounded.multiply(BigDecimal(100 - clampedTolerance))
            .divide(HUNDRED, 1, RoundingMode.HALF_UP)
        return if (m1 >= orangeThreshold) {
            IndicatorColor.ORANGE
        } else {
            IndicatorColor.RED
        }
    }

    /**
     * Computes the smallest whole-EGP integer price p such that the rounded per-km rate is >= minRounded.
     */
    fun computeMinAcceptablePrice(
        totalKm: BigDecimal,
        minRounded: BigDecimal,
        useNet: Boolean,
        commissionPercent: Int
    ): Int {
        val commissionFactor = if (useNet) 100.0 / (100 - commissionPercent) else 1.0
        val roughEstimate = (minRounded.toDouble() * totalKm.toDouble() * commissionFactor).toInt()
        var candidate = maxOf(1, roughEstimate - 10)

        val netDenominator = totalKm.multiply(HUNDRED)
        val clampedCommission = commissionPercent.coerceIn(0, 50)

        while (true) {
            val candidateBd = BigDecimal(candidate)
            val rate = if (useNet) {
                val num = candidateBd.multiply(BigDecimal(100 - clampedCommission))
                num.divide(netDenominator, 1, RoundingMode.HALF_UP)
            } else {
                candidateBd.divide(totalKm, 1, RoundingMode.HALF_UP)
            }
            if (rate >= minRounded) {
                return candidate
            }
            candidate++
        }
    }

    private fun safeBigDecimal(value: String): BigDecimal? {
        return try {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) null else BigDecimal(trimmed)
        } catch (_: Exception) {
            null
        }
    }
}
