package com.example

import com.example.reading.NumberParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class NumberParserTest {

    @Test
    fun testNormalizeDigitsAndSeparators() {
        val arabicText = "السعر ٥٥٫٥ جنيه"
        val normalized = NumberParser.normalize(arabicText)
        assertEquals("السعر 55.5 جنيه", normalized)

        val persianDigits = "۴ ۵ ۶"
        assertEquals("4 5 6", NumberParser.normalize(persianDigits))
    }

    @Test
    fun testParsePickupKmWithTilde() {
        val pickup = NumberParser.parsePickupKm("~٣٫٥ كم")
        assertNotNull(pickup)
        assertEquals(BigDecimal("3.5"), pickup)

        val pickupLatin = NumberParser.parsePickupKm("~ 1.4 km")
        assertNotNull(pickupLatin)
        assertEquals(BigDecimal("1.4"), pickupLatin)
    }

    @Test
    fun testParseTripKmWithoutTilde() {
        val trip = NumberParser.parseKm("50.3 كم")
        assertNotNull(trip)
        assertEquals(BigDecimal("50.3"), trip)

        // Text with tilde should NOT be parsed as trip km
        val tildeText = NumberParser.parseKm("~ 3.5 كم")
        assertNull(tildeText)
    }

    @Test
    fun testParsePriceWithCurrency() {
        val p1 = NumberParser.parsePrice("179 ج.م")
        assertEquals(BigDecimal("179"), p1)

        val p2 = NumberParser.parsePrice("٥٥ جنيه")
        assertEquals(BigDecimal("55"), p2)

        val p3 = NumberParser.parsePrice("200 EGP")
        assertEquals(BigDecimal("200"), p3)
    }

    @Test
    fun testDoNotMistakeKmOrMinutesForPrice() {
        // Pure km or min node should never be parsed as price
        val kmNode = NumberParser.parsePrice("15.7 كم")
        assertNull(kmNode)

        val minNode = NumberParser.parsePrice("25 دقيقة")
        assertNull(minNode)
    }

    @Test
    fun testSanityRanges() {
        // Price < 5 or > 5000 is rejected
        assertNull(NumberParser.parsePrice("2 ج.م"))
        assertNull(NumberParser.parsePrice("99999 ج.م"))

        // Km < 0.1 or > 300 is rejected
        assertNull(NumberParser.parseKm("0.01 كم"))
        assertNull(NumberParser.parseKm("500 كم"))
    }
}
