package com.example.reading

import java.math.BigDecimal

data class FrameReading(
    val price: BigDecimal,
    val pickupKm: BigDecimal?,
    val tripKm: BigDecimal
)

sealed class GateStatus {
    data object Idle : GateStatus()
    data object Unconfirmed : GateStatus() // Neutral "…"
    data class Confirmed(val reading: FrameReading) : GateStatus()
}

/**
 * Two-frame stability confirmation gate.
 * WHY TWO FRAMES: Prevents displaying flickering or half-rendered transient numbers
 * during dynamic UI transitions in the driver app.
 * Pure Kotlin, zero Android dependencies.
 */
class StabilityGate {

    private var previousReading: FrameReading? = null
    private var matchCount: Int = 0

    fun feed(current: FrameReading?): GateStatus {
        if (current == null) {
            reset()
            return GateStatus.Idle
        }

        val prev = previousReading
        if (prev != null && isSameReading(prev, current)) {
            matchCount++
            return if (matchCount >= 2) {
                GateStatus.Confirmed(current)
            } else {
                GateStatus.Unconfirmed
            }
        } else {
            // New request or reading changed -> reset count to 1 and wait for confirmation
            previousReading = current
            matchCount = 1
            return GateStatus.Unconfirmed
        }
    }

    fun reset() {
        previousReading = null
        matchCount = 0
    }

    private fun isSameReading(a: FrameReading, b: FrameReading): Boolean {
        val priceMatch = a.price.compareTo(b.price) == 0
        val tripMatch = a.tripKm.compareTo(b.tripKm) == 0
        val pickupMatch = when {
            a.pickupKm == null && b.pickupKm == null -> true
            a.pickupKm != null && b.pickupKm != null -> a.pickupKm.compareTo(b.pickupKm) == 0
            else -> false
        }
        return priceMatch && tripMatch && pickupMatch
    }
}
