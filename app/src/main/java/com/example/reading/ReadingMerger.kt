package com.example.reading

import java.math.BigDecimal

data class RawReading(
    val price: BigDecimal?,
    val pickupKm: BigDecimal?,
    val tripKm: BigDecimal?,
    val minutes: Int? = null
) {
    val isValid: Boolean
        get() {
            if (price == null || tripKm == null) return false
            if (price < BigDecimal("5") || price > BigDecimal("5000")) return false
            if (tripKm < BigDecimal("0.1") || tripKm > BigDecimal("300")) return false
            if (pickupKm != null && (pickupKm < BigDecimal.ZERO || pickupKm > BigDecimal("300"))) return false
            return true
        }
}

sealed class MergeResult {
    data class Accepted(
        val price: BigDecimal,
        val pickupKm: BigDecimal?,
        val tripKm: BigDecimal,
        val minutes: Int?
    ) : MergeResult()

    data class Suspicious(val reason: String = "قراءة مشكوك فيها") : MergeResult()
    data class Missing(val reason: String = "؟") : MergeResult()
}

/**
 * Pure Kotlin merger combining accessibility-tree readings and OCR readings.
 * Zero Android dependencies.
 */
object ReadingMerger {

    private val KM_TOLERANCE = BigDecimal("0.1")

    fun merge(
        tree: RawReading?,
        ocr: RawReading?
    ): MergeResult {
        val treeValid = tree != null && tree.isValid
        val ocrValid = ocr != null && ocr.isValid

        val result = when {
            // Case 1 & 4: Both valid -> check agreement
            treeValid && ocrValid -> {
                val treeVal = tree!!
                val ocrVal = ocr!!

                // Price must match exactly (any difference is a conflict)
                val priceAgrees = treeVal.price!!.compareTo(ocrVal.price!!) == 0

                // Km difference must be <= 0.1
                val tripDiff = (treeVal.tripKm!!.subtract(ocrVal.tripKm!!)).abs()
                val tripAgrees = tripDiff <= KM_TOLERANCE

                val pickupAgrees = if (treeVal.pickupKm != null && ocrVal.pickupKm != null) {
                    val pickupDiff = (treeVal.pickupKm.subtract(ocrVal.pickupKm)).abs()
                    pickupDiff <= KM_TOLERANCE
                } else {
                    true
                }

                if (priceAgrees && tripAgrees && pickupAgrees) {
                    // Case 1: Tree valid and OCR agrees -> accept tree values
                    MergeResult.Accepted(
                        price = treeVal.price,
                        pickupKm = treeVal.pickupKm,
                        tripKm = treeVal.tripKm,
                        minutes = treeVal.minutes ?: ocrVal.minutes
                    )
                } else {
                    // Case 4: Both valid but disagree -> Suspicious
                    MergeResult.Suspicious("اختلاف بين الشجرة وOCR")
                }
            }

            // Case 2: Tree valid, OCR missing/invalid -> accept tree
            treeValid -> {
                val treeVal = tree!!
                MergeResult.Accepted(
                    price = treeVal.price!!,
                    pickupKm = treeVal.pickupKm,
                    tripKm = treeVal.tripKm!!,
                    minutes = treeVal.minutes
                )
            }

            // Case 3: Tree empty/invalid, OCR valid -> use OCR
            ocrValid -> {
                val ocrVal = ocr!!
                MergeResult.Accepted(
                    price = ocrVal.price!!,
                    pickupKm = ocrVal.pickupKm,
                    tripKm = ocrVal.tripKm!!,
                    minutes = ocrVal.minutes
                )
            }

            // Neither valid
            else -> MergeResult.Missing()
        }

        // Additional plausibility check: implied speed must be 5–130 km/h
        if (result is MergeResult.Accepted && result.minutes != null && result.minutes > 0) {
            val impliedSpeed = (result.tripKm.toDouble() * 60.0) / result.minutes.toDouble()
            if (impliedSpeed < 5.0 || impliedSpeed > 130.0) {
                return MergeResult.Suspicious("سرعة غير معقولة ($impliedSpeed كم/س)")
            }
        }

        return result
    }
}
