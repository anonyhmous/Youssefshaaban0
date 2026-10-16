package com.example.reading

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import java.math.BigDecimal
import java.security.MessageDigest

data class NodeSnapshot(
    val text: String?,
    val contentDescription: String?,
    val className: String?,
    val viewId: String?,
    val bounds: Rect,
    val isClickable: Boolean
) {
    val combinedText: String
        get() = "${text.orEmpty()} ${contentDescription.orEmpty()}".trim()
}

data class CardSnapshot(
    val cardId: String,
    val bounds: Rect,
    val fare: BigDecimal?,
    val pickupKm: BigDecimal?,
    val tripKm: BigDecimal?,
    val minutes: Int?,
    val suggestedChips: List<BigDecimal>,
    val isPriceMismatch: Boolean = false
)

data class TreeReading(
    val fare: BigDecimal?,
    val acceptButtonPrice: BigDecimal?,
    val pickupKm: BigDecimal?,
    val tripKm: BigDecimal?,
    val minutes: Int?,
    val suggestedChips: List<BigDecimal>,
    val cards: List<CardSnapshot>,
    val occupiedBounds: List<Rect>,
    val isRequestScreen: Boolean,
    val hasPriceMismatch: Boolean,
    val fingerprint: String
)

/**
 * Extracts ride parameters from Android AccessibilityNodeInfo tree.
 * Traverses by semantic role, never by hardcoded node index.
 */
object TreeReader {

    private const val MAX_DEPTH = 40
    private const val MAX_NODES = 600

    fun readTree(root: AccessibilityNodeInfo?): TreeReading {
        if (root == null) {
            return TreeReading(
                fare = null,
                acceptButtonPrice = null,
                pickupKm = null,
                tripKm = null,
                minutes = null,
                suggestedChips = emptyList(),
                cards = emptyList(),
                occupiedBounds = emptyList(),
                isRequestScreen = false,
                hasPriceMismatch = false,
                fingerprint = ""
            )
        }

        val flatNodes = mutableListOf<NodeSnapshot>()
        flattenNode(root, 0, flatNodes)

        val occupiedBounds = mutableListOf<Rect>()
        var detectedFare: BigDecimal? = null
        var acceptPrice: BigDecimal? = null
        var detectedPickup: BigDecimal? = null
        var detectedTrip: BigDecimal? = null
        var detectedMinutes: Int? = null
        var hasAcceptButton = false
        val chipsCandidates = mutableListOf<BigDecimal>()
        val fingerprintBuilder = StringBuilder()

        for (node in flatNodes) {
            val combined = node.combinedText
            if (combined.isNotEmpty()) {
                fingerprintBuilder.append(combined).append("|")
                if (node.bounds.width() > 0 && node.bounds.height() > 0) {
                    occupiedBounds.add(Rect(node.bounds))
                }
            }

            val lower = combined.lowercase()
            val idLower = (node.viewId ?: "").lowercase()

            // 1. Detect Accept button and any price embedded in it
            val isAcceptNode = lower.contains("قبول") || lower.contains("accept") ||
                    lower.contains("agree") || idLower.contains("accept")
            if (isAcceptNode) {
                hasAcceptButton = true
                val btnPrice = NumberParser.parsePrice(combined, isStrictCurrency = false)
                if (btnPrice != null) {
                    acceptPrice = btnPrice
                }
            }

            // 2. Pickup distance (~x km)
            if (detectedPickup == null) {
                val pickup = NumberParser.parsePickupKm(combined)
                if (pickup != null) {
                    detectedPickup = pickup
                }
            }

            // 3. Trip distance (km without ~)
            if (detectedTrip == null) {
                val trip = NumberParser.parseKm(combined)
                if (trip != null) {
                    detectedTrip = trip
                }
            }

            // 4. Duration in minutes
            if (detectedMinutes == null) {
                val min = NumberParser.parseMinutes(combined)
                if (min != null) {
                    detectedMinutes = min
                }
            }

            // 5. Big fare node (currency explicit)
            val parsedPrice = NumberParser.parsePrice(combined, isStrictCurrency = true)
            if (parsedPrice != null && detectedFare == null && !isAcceptNode) {
                detectedFare = parsedPrice
            }

            // 6. Suggested price chips (clickable / chip / button with price)
            val isChipType = node.isClickable ||
                    (node.className?.contains("Button", ignoreCase = true) == true) ||
                    (node.className?.contains("Chip", ignoreCase = true) == true)

            if (isChipType && !isAcceptNode) {
                val chipPrice = NumberParser.parsePrice(combined, isStrictCurrency = false)
                if (chipPrice != null) {
                    chipsCandidates.add(chipPrice)
                }
            }
        }

        // Distinct, sorted chips strictly greater than the fare
        val baseFare = detectedFare ?: acceptPrice
        val suggestedChips = if (baseFare != null) {
            chipsCandidates
                .filter { it > baseFare && it <= BigDecimal("5000") }
                .distinct()
                .sorted()
                .take(3)
        } else {
            emptyList()
        }

        val hasPriceMismatch = (detectedFare != null && acceptPrice != null && detectedFare.compareTo(acceptPrice) != 0)
        val finalFare = if (hasPriceMismatch) null else (detectedFare ?: acceptPrice)

        val isRequestScreen = hasAcceptButton ||
                (finalFare != null && (detectedTrip != null || detectedPickup != null || suggestedChips.isNotEmpty()))

        // Build ride card snapshots for multi-request list feeds
        val cards = buildCardSnapshots(flatNodes, finalFare, detectedPickup, detectedTrip, detectedMinutes, suggestedChips)

        val fingerprint = hashString(fingerprintBuilder.toString())

        return TreeReading(
            fare = finalFare,
            acceptButtonPrice = acceptPrice,
            pickupKm = detectedPickup,
            tripKm = detectedTrip,
            minutes = detectedMinutes,
            suggestedChips = suggestedChips,
            cards = cards,
            occupiedBounds = occupiedBounds,
            isRequestScreen = isRequestScreen,
            hasPriceMismatch = hasPriceMismatch,
            fingerprint = fingerprint
        )
    }

    private fun flattenNode(node: AccessibilityNodeInfo, depth: Int, result: MutableList<NodeSnapshot>) {
        if (depth > MAX_DEPTH || result.size >= MAX_NODES) return

        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()
        val className = node.className?.toString()
        val viewId = node.viewIdResourceName

        result.add(
            NodeSnapshot(
                text = text,
                contentDescription = desc,
                className = className,
                viewId = viewId,
                bounds = bounds,
                isClickable = node.isClickable
            )
        )

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i) ?: continue
            flattenNode(child, depth + 1, result)
        }
    }

    private fun buildCardSnapshots(
        nodes: List<NodeSnapshot>,
        singleFare: BigDecimal?,
        singlePickup: BigDecimal?,
        singleTrip: BigDecimal?,
        singleMinutes: Int?,
        singleChips: List<BigDecimal>
    ): List<CardSnapshot> {
        // Group nodes by spatial clustering or build card from detected request screen
        if (singleFare != null && (singleTrip != null || singlePickup != null)) {
            val overallBounds = Rect()
            for (node in nodes) {
                if (node.bounds.width() > 0 && node.bounds.height() > 0) {
                    if (overallBounds.isEmpty) {
                        overallBounds.set(node.bounds)
                    } else {
                        overallBounds.union(node.bounds)
                    }
                }
            }
            if (!overallBounds.isEmpty) {
                return listOf(
                    CardSnapshot(
                        cardId = "primary_card",
                        bounds = overallBounds,
                        fare = singleFare,
                        pickupKm = singlePickup,
                        tripKm = singleTrip,
                        minutes = singleMinutes,
                        suggestedChips = singleChips
                    )
                )
            }
        }
        return emptyList()
    }

    private fun hashString(input: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(input.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            input.hashCode().toString()
        }
    }
}
