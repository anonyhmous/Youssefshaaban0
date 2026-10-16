package com.example.reading

import java.math.BigDecimal

/**
 * Parses numbers, prices, distances, and times from raw UI text.
 * Pure Kotlin, zero Android dependencies.
 */
object NumberParser {

    private val MIN_PRICE = BigDecimal("5")
    private val MAX_PRICE = BigDecimal("5000")
    private val MIN_KM = BigDecimal("0.1")
    private val MAX_KM = BigDecimal("300")

    /**
     * Normalises Arabic-Indic, Persian digits, punctuation, and bidi marks.
     */
    fun normalize(rawText: String): String {
        val sb = StringBuilder(rawText.length)
        for (ch in rawText) {
            when (ch) {
                // Arabic-Indic digits ٠ - ٩
                '٠' -> sb.append('0')
                '١' -> sb.append('1')
                '٢' -> sb.append('2')
                '٣' -> sb.append('3')
                '٤' -> sb.append('4')
                '٥' -> sb.append('5')
                '٦' -> sb.append('6')
                '٧' -> sb.append('7')
                '٨' -> sb.append('8')
                '٩' -> sb.append('9')
                // Persian/Urdu digits
                '۴' -> sb.append('4')
                '۵' -> sb.append('5')
                '۶' -> sb.append('6')
                // Decimal separators
                '٫', '،', ',' -> sb.append('.')
                // Strip bidi control marks
                '\u200E', '\u200F', '\u202A', '\u202B', '\u202C', '\u202D', '\u202E',
                '\u2066', '\u2067', '\u2068', '\u2069' -> { /* ignore */ }
                // Normalise non-breaking and special spaces
                '\u00A0', '\u202F', '\u2007', '\uFEFF' -> sb.append(' ')
                else -> sb.append(ch)
            }
        }
        return sb.toString().trim()
    }

    /**
     * Detects if the string contains a pickup indicator (~ followed by distance).
     */
    fun parsePickupKm(rawText: String): BigDecimal? {
        val norm = normalize(rawText)
        if (!norm.contains('~')) return null

        // Match patterns like ~ 3.5 km or ~3.5كم or ~3.5
        val regex = Regex("""~\s*(\d+(?:\.\d+)?)\s*(?:كم|km)?""", RegexOption.IGNORE_CASE)
        val match = regex.find(norm) ?: return null
        val numStr = match.groupValues[1]
        val value = safeBigDecimal(numStr) ?: return null
        return if (value in MIN_KM..MAX_KM) value else null
    }

    /**
     * Detects trip distance (km without tilde).
     */
    fun parseKm(rawText: String): BigDecimal? {
        val norm = normalize(rawText)
        // If it starts with or contains ~, it's pickup, not trip km
        if (norm.contains('~')) return null

        // Must explicitly contain km or كم
        val regex = Regex("""(\d+(?:\.\d+)?)\s*(?:كم|km)(?:\s|$|[^\p{L}\d])""", RegexOption.IGNORE_CASE)
        val match = regex.find(norm) ?: return null
        val numStr = match.groupValues[1]
        val value = safeBigDecimal(numStr) ?: return null
        return if (value in MIN_KM..MAX_KM) value else null
    }

    /**
     * Parses price in EGP.
     * Never mistakes a km or minutes node for a price.
     */
    fun parsePrice(rawText: String, isStrictCurrency: Boolean = true): BigDecimal? {
        val norm = normalize(rawText)

        // If the text contains km or minutes keywords and NO currency, reject to prevent mistaking km/min for fare
        val hasKm = norm.contains("كم") || norm.contains("km", ignoreCase = true)
        val hasMin = norm.contains("دقيقة") || norm.contains("دقائق") || norm.contains("min", ignoreCase = true)
        val hasCurrency = norm.contains("ج.م") || norm.contains("جنيه") || norm.contains("ج") ||
                norm.contains("egp", ignoreCase = true) || norm.contains("le", ignoreCase = true)

        if ((hasKm || hasMin) && !hasCurrency) {
            return null
        }

        if (hasCurrency) {
            // Find number adjacent to currency symbol
            val currencyRegex = Regex("""(?:ج\.م|جنيه|ج|egp|le)\s*(\d+(?:\.\d+)?)|(\d+(?:\.\d+)?)\s*(?:ج\.م|جنيه|ج|egp|le)""", RegexOption.IGNORE_CASE)
            val match = currencyRegex.find(norm)
            if (match != null) {
                val numStr = match.groupValues[1].ifEmpty { match.groupValues[2] }
                val value = safeBigDecimal(numStr) ?: return null
                return if (value in MIN_PRICE..MAX_PRICE) value else null
            }
        }

        // If strict currency is disabled (e.g. for pure digit chips or accept buttons)
        if (!isStrictCurrency && !hasKm && !hasMin) {
            val numRegex = Regex("""^(\d+(?:\.\d+)?)$""")
            val match = numRegex.find(norm.trim())
            if (match != null) {
                val value = safeBigDecimal(match.groupValues[1]) ?: return null
                return if (value in MIN_PRICE..MAX_PRICE) value else null
            }
        }

        return null
    }

    /**
     * Parses minutes from duration text (د, دقيقة, min).
     */
    fun parseMinutes(rawText: String): Int? {
        val norm = normalize(rawText)
        val minRegex = Regex("""(\d+)\s*(?:دقيقة|دقائق|د|min)(?:\s|$|[^\p{L}\d])""", RegexOption.IGNORE_CASE)
        val match = minRegex.find(norm) ?: return null
        val num = match.groupValues[1].toIntOrNull() ?: return null
        return if (num in 1..720) num else null
    }

    /**
     * Parses star rating (e.g. 4.85).
     */
    fun parseRating(rawText: String): BigDecimal? {
        val norm = normalize(rawText)
        val ratingRegex = Regex("""(?:★|⭐)?\s*([1-5](?:\.\d{1,2})?)\s*(?:★|⭐)?""")
        val match = ratingRegex.find(norm) ?: return null
        val num = safeBigDecimal(match.groupValues[1]) ?: return null
        return if (num in BigDecimal("1.0")..BigDecimal("5.0")) num else null
    }

    private fun safeBigDecimal(value: String): BigDecimal? {
        return try {
            BigDecimal(value)
        } catch (_: Exception) {
            null
        }
    }
}
