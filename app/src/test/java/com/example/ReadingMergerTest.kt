package com.example

import com.example.reading.MergeResult
import com.example.reading.RawReading
import com.example.reading.ReadingMerger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class ReadingMergerTest {

    @Test
    fun testCase1_treeValid_ocrAgrees_acceptsTree() {
        // Case 1: Tree valid and OCR agrees -> accept tree values
        val tree = RawReading(price = BigDecimal("100"), pickupKm = BigDecimal("2.0"), tripKm = BigDecimal("20.0"))
        val ocr = RawReading(price = BigDecimal("100"), pickupKm = BigDecimal("2.0"), tripKm = BigDecimal("20.0"))

        val result = ReadingMerger.merge(tree, ocr)
        assertTrue(result is MergeResult.Accepted)
        val accepted = result as MergeResult.Accepted
        assertEquals(BigDecimal("100"), accepted.price)
        assertEquals(BigDecimal("20.0"), accepted.tripKm)
    }

    @Test
    fun testCase2_treeValid_ocrMissing_acceptsTree() {
        // Case 2: Tree valid, OCR missing -> accept tree
        val tree = RawReading(price = BigDecimal("150"), pickupKm = BigDecimal("1.5"), tripKm = BigDecimal("35.0"))
        val result = ReadingMerger.merge(tree, null)

        assertTrue(result is MergeResult.Accepted)
        val accepted = result as MergeResult.Accepted
        assertEquals(BigDecimal("150"), accepted.price)
        assertEquals(BigDecimal("35.0"), accepted.tripKm)
    }

    @Test
    fun testCase3_treeInvalid_ocrValid_usesOcrOnly() {
        // Case 3: Tree empty/invalid -> use OCR only
        val tree = RawReading(price = null, pickupKm = null, tripKm = null)
        val ocr = RawReading(price = BigDecimal("85"), pickupKm = BigDecimal("3.0"), tripKm = BigDecimal("18.0"))

        val result = ReadingMerger.merge(tree, ocr)
        assertTrue(result is MergeResult.Accepted)
        val accepted = result as MergeResult.Accepted
        assertEquals(BigDecimal("85"), accepted.price)
        assertEquals(BigDecimal("18.0"), accepted.tripKm)
    }

    @Test
    fun testCase4_disagreementOnPrice_returnsSuspicious() {
        // Both valid but disagree on price by even 1 EGP -> Suspicious
        val tree = RawReading(price = BigDecimal("100"), pickupKm = BigDecimal("2.0"), tripKm = BigDecimal("20.0"))
        val ocr = RawReading(price = BigDecimal("101"), pickupKm = BigDecimal("2.0"), tripKm = BigDecimal("20.0"))

        val result = ReadingMerger.merge(tree, ocr)
        assertTrue(result is MergeResult.Suspicious)
    }

    @Test
    fun testCase4_disagreementOnKm_greaterThan0_1_returnsSuspicious() {
        // Disagreement on km > 0.1 -> Suspicious
        val tree = RawReading(price = BigDecimal("100"), pickupKm = BigDecimal("2.0"), tripKm = BigDecimal("20.0"))
        val ocr = RawReading(price = BigDecimal("100"), pickupKm = BigDecimal("2.0"), tripKm = BigDecimal("20.15"))

        val result = ReadingMerger.merge(tree, ocr)
        assertTrue(result is MergeResult.Suspicious)
    }

    @Test
    fun testKmToleranceEdge_exact0_1_agrees() {
        // Disagreement on km <= 0.1 -> Agree!
        val tree = RawReading(price = BigDecimal("100"), pickupKm = BigDecimal("2.0"), tripKm = BigDecimal("20.0"))
        val ocr = RawReading(price = BigDecimal("100"), pickupKm = BigDecimal("2.0"), tripKm = BigDecimal("20.1"))

        val result = ReadingMerger.merge(tree, ocr)
        assertTrue(result is MergeResult.Accepted)
    }

    @Test
    fun testSpeedPlausibility_impliedSpeedOutSide5to130_returnsSuspicious() {
        // 10 km in 2 minutes -> 300 km/h -> Suspicious!
        val tree = RawReading(price = BigDecimal("50"), pickupKm = null, tripKm = BigDecimal("10.0"), minutes = 2)
        val result = ReadingMerger.merge(tree, null)
        assertTrue(result is MergeResult.Suspicious)

        // 10 km in 20 minutes -> 30 km/h -> Accepted
        val normalTree = RawReading(price = BigDecimal("50"), pickupKm = null, tripKm = BigDecimal("10.0"), minutes = 20)
        val normalResult = ReadingMerger.merge(normalTree, null)
        assertTrue(normalResult is MergeResult.Accepted)
    }
}
