package com.example

import com.example.domain.CoreMath
import com.example.domain.CoreMathResult
import com.example.domain.DistanceMode
import com.example.domain.IndicatorColor
import com.example.domain.JudgeBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class CoreMathTest {

    @Test
    fun testCase1_200Egp_pickup1_trip69_5() {
        // 200 EGP, pickup 1.0, trip 69.5 -> 2.8
        val result = CoreMath.calculate(
            priceStr = "200",
            pickupKmStr = "1.0",
            tripKmStr = "69.5"
        )
        assertTrue(result is CoreMathResult.Success)
        val success = result as CoreMathResult.Success
        assertEquals(BigDecimal("2.8"), success.judgedPerKm)
    }

    @Test
    fun testCase2_179Egp_pickup1_4_trip50_3() {
        // 179, 1.4, 50.3 -> 3.5
        val result = CoreMath.calculate(
            priceStr = "179",
            pickupKmStr = "1.4",
            tripKmStr = "50.3"
        )
        assertTrue(result is CoreMathResult.Success)
        val success = result as CoreMathResult.Success
        assertEquals(BigDecimal("3.5"), success.judgedPerKm)
    }

    @Test
    fun testCase3_55Egp_pickup3_5_trip15_7_withChips() {
        // 55, 3.5, 15.7 -> 2.9; min acceptable at 3.0 = 57; chips 58->3.0, 61->3.2, 66->3.4
        val result = CoreMath.calculate(
            priceStr = "55",
            pickupKmStr = "3.5",
            tripKmStr = "15.7",
            myMinimumPerKm = BigDecimal("3.0"),
            suggestedPrices = listOf(BigDecimal("58"), BigDecimal("61"), BigDecimal("66"))
        )
        assertTrue(result is CoreMathResult.Success)
        val success = result as CoreMathResult.Success
        assertEquals(BigDecimal("2.9"), success.judgedPerKm)
        assertEquals(57, success.minAcceptablePrice)

        assertEquals(3, success.chips.size)
        assertEquals(BigDecimal("3.0"), success.chips[0].perKm)
        assertEquals(BigDecimal("3.2"), success.chips[1].perKm)
        assertEquals(BigDecimal("3.4"), success.chips[2].perKm)
    }

    @Test
    fun testCase4_21Egp_pickup5_2_trip2_1() {
        // 21, 5.2, 2.1 -> 2.9
        val result = CoreMath.calculate(
            priceStr = "21",
            pickupKmStr = "5.2",
            tripKmStr = "2.1"
        )
        assertTrue(result is CoreMathResult.Success)
        val success = result as CoreMathResult.Success
        assertEquals(BigDecimal("2.9"), success.judgedPerKm)
    }

    @Test
    fun testCase5_65Egp_pickup2_4_trip17_6() {
        // 65, 2.4, 17.6 -> 3.3; min acceptable at 3.0 = 59
        val result = CoreMath.calculate(
            priceStr = "65",
            pickupKmStr = "2.4",
            tripKmStr = "17.6",
            myMinimumPerKm = BigDecimal("3.0")
        )
        assertTrue(result is CoreMathResult.Success)
        val success = result as CoreMathResult.Success
        assertEquals(BigDecimal("3.3"), success.judgedPerKm)
        assertEquals(59, success.minAcceptablePrice)
    }

    @Test
    fun testCase6_40Egp_commission10Percent() {
        // 40, pickup 2, trip 8, commission 10% -> gross 4.0, net 3.6
        val resultGross = CoreMath.calculate(
            priceStr = "40",
            pickupKmStr = "2",
            tripKmStr = "8",
            deductCommission = true,
            commissionPercent = 10,
            judgeBy = JudgeBasis.GROSS
        )
        assertTrue(resultGross is CoreMathResult.Success)
        val successGross = resultGross as CoreMathResult.Success
        assertEquals(BigDecimal("4.0"), successGross.judgedPerKm)
        assertEquals(BigDecimal("3.6"), successGross.otherPerKm)

        val resultNet = CoreMath.calculate(
            priceStr = "40",
            pickupKmStr = "2",
            tripKmStr = "8",
            deductCommission = true,
            commissionPercent = 10,
            judgeBy = JudgeBasis.NET
        )
        assertTrue(resultNet is CoreMathResult.Success)
        val successNet = resultNet as CoreMathResult.Success
        assertEquals(BigDecimal("3.6"), successNet.judgedPerKm)
        assertEquals(BigDecimal("4.0"), successNet.otherPerKm)
    }

    @Test
    fun testCase7_deductCommissionOff_identicalToGross() {
        // deductCommission off -> identical to gross for all above
        val result = CoreMath.calculate(
            priceStr = "40",
            pickupKmStr = "2",
            tripKmStr = "8",
            deductCommission = false,
            commissionPercent = 10
        )
        assertTrue(result is CoreMathResult.Success)
        val success = result as CoreMathResult.Success
        assertEquals(BigDecimal("4.0"), success.judgedPerKm)
        assertEquals(null, success.otherPerKm)
    }

    @Test
    fun testCase8_validations() {
        // price/km > 60 -> Suspicious
        val highRate = CoreMath.calculate(priceStr = "500", pickupKmStr = "1", tripKmStr = "2")
        assertTrue(highRate is CoreMathResult.Suspicious)

        // missing trip -> Missing
        val missingTrip = CoreMath.calculate(priceStr = "50", pickupKmStr = "2", tripKmStr = null)
        assertTrue(missingTrip is CoreMathResult.Missing)

        // zero price -> Suspicious
        val zeroPrice = CoreMath.calculate(priceStr = "0", pickupKmStr = "2", tripKmStr = "10")
        assertTrue(zeroPrice is CoreMathResult.Suspicious)
    }

    @Test
    fun testCase9_boundary_2_95_roundsTo3_0_andIsGreen() {
        // boundary: 2.95 rounds HALF_UP to 3.0 and is GREEN at min 3.0
        // Price = 59, TotalKm = 20.0 -> 59 / 20.0 = 2.95 -> rounds to 3.0
        val result = CoreMath.calculate(
            priceStr = "59",
            pickupKmStr = "0",
            tripKmStr = "20.0",
            myMinimumPerKm = BigDecimal("3.0")
        )
        assertTrue(result is CoreMathResult.Success)
        val success = result as CoreMathResult.Success
        assertEquals(BigDecimal("3.0"), success.judgedPerKm)
        assertEquals(IndicatorColor.GREEN, success.color)
    }
}
