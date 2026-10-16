package com.example.reading

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

data class OcrReading(
    val fare: BigDecimal?,
    val pickupKm: BigDecimal?,
    val tripKm: BigDecimal?,
    val minutes: Int?,
    val rawText: String = "",
    val isBlackScreen: Boolean = false
)

/**
 * On-device OCR extraction using Google ML Kit.
 * Strictly operates on cropped card regions with black-screen detection and memory safety.
 */
object OcrReader {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun processScreenshot(
        screenshot: Bitmap,
        cropBounds: Rect?
    ): OcrReading = withContext(Dispatchers.Default) {
        var croppedBitmap: Bitmap? = null
        try {
            // Check if screenshot is black (e.g. secure surface or blank display)
            if (isBlackBitmap(screenshot)) {
                return@withContext OcrReading(
                    fare = null,
                    pickupKm = null,
                    tripKm = null,
                    minutes = null,
                    isBlackScreen = true
                )
            }

            val targetBitmap = if (cropBounds != null &&
                cropBounds.left >= 0 && cropBounds.top >= 0 &&
                cropBounds.right <= screenshot.width && cropBounds.bottom <= screenshot.height &&
                cropBounds.width() > 10 && cropBounds.height() > 10
            ) {
                croppedBitmap = Bitmap.createBitmap(
                    screenshot,
                    cropBounds.left,
                    cropBounds.top,
                    cropBounds.width(),
                    cropBounds.height()
                )
                croppedBitmap
            } else {
                screenshot
            }

            val inputImage = InputImage.fromBitmap(targetBitmap, 0)
            val task = recognizer.process(inputImage)
            val visionText = Tasks.await(task, 1500, TimeUnit.MILLISECONDS)
            val fullText = visionText.text

            var detectedFare: BigDecimal? = null
            var detectedPickup: BigDecimal? = null
            var detectedTrip: BigDecimal? = null
            var detectedMinutes: Int? = null

            for (block in visionText.textBlocks) {
                for (line in block.lines) {
                    val text = line.text
                    if (detectedPickup == null) {
                        val pickup = NumberParser.parsePickupKm(text)
                        if (pickup != null) detectedPickup = pickup
                    }
                    if (detectedTrip == null) {
                        val trip = NumberParser.parseKm(text)
                        if (trip != null) detectedTrip = trip
                    }
                    if (detectedMinutes == null) {
                        val min = NumberParser.parseMinutes(text)
                        if (min != null) detectedMinutes = min
                    }
                    if (detectedFare == null) {
                        val price = NumberParser.parsePrice(text, isStrictCurrency = true)
                        if (price != null) detectedFare = price
                    }
                }
            }

            OcrReading(
                fare = detectedFare,
                pickupKm = detectedPickup,
                tripKm = detectedTrip,
                minutes = detectedMinutes,
                rawText = fullText,
                isBlackScreen = false
            )
        } catch (_: Exception) {
            OcrReading(
                fare = null,
                pickupKm = null,
                tripKm = null,
                minutes = null,
                rawText = "",
                isBlackScreen = false
            )
        } finally {
            croppedBitmap?.recycle()
        }
    }

    /**
     * Samples a 6x6 pixel grid to detect all-black / protected captures.
     */
    private fun isBlackBitmap(bitmap: Bitmap): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return true

        val stepX = (width / 6).coerceAtLeast(1)
        val stepY = (height / 6).coerceAtLeast(1)

        for (x in 0 until width step stepX) {
            for (y in 0 until height step stepY) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                if (r > 15 || g > 15 || b > 15) {
                    return false
                }
            }
        }
        return true
    }
}
