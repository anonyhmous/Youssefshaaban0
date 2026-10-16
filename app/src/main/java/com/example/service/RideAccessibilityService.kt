package com.example.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import com.example.data.SettingsRepository
import com.example.data.UserSettings
import com.example.domain.CoreMath
import com.example.domain.CoreMathResult
import com.example.overlay.IndicatorOverlay
import com.example.overlay.Point
import com.example.overlay.SimpleRect
import com.example.reading.CardSnapshot
import com.example.reading.FrameReading
import com.example.reading.GateStatus
import com.example.reading.MergeResult
import com.example.reading.OcrReader
import com.example.reading.RawReading
import com.example.reading.ReadingMerger
import com.example.reading.StabilityGate
import com.example.reading.TreeReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.concurrent.Executor

/**
 * Foreground event monitor for inDrive ride-hailing app in Egypt.
 * Strictly passive: NEVER taps, accepts, declines, or inputs anything.
 */
class RideAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var settingsRepo: SettingsRepository
    private var currentSettings: UserSettings = UserSettings()

    private val stabilityGate = StabilityGate()
    private var overlay: IndicatorOverlay? = null

    private var ocrJob: Job? = null
    private var lastTreeFingerprint: String = ""

    companion object {
        val IN_DRIVE_PACKAGES = setOf(
            "sinet.startup.inDriver",
            "com.indriver.drivers",
            "com.indrive.driver"
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        settingsRepo = SettingsRepository.getInstance(applicationContext)

        overlay = IndicatorOverlay(this) { x, y ->
            serviceScope.launch {
                settingsRepo.saveIndicatorPosition(x, y)
            }
        }

        serviceScope.launch {
            settingsRepo.settingsFlow.collect { settings ->
                currentSettings = settings
                if (!settings.isMonitoringActive) {
                    overlay?.hideAll()
                    stabilityGate.reset()
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            // First line: Ignore events from any package not in the inDrive list
            val pkg = event?.packageName?.toString() ?: return
            if (pkg !in IN_DRIVE_PACKAGES) {
                overlay?.hideAll()
                stabilityGate.reset()
                return
            }

            if (!currentSettings.isMonitoringActive) {
                overlay?.hideAll()
                stabilityGate.reset()
                return
            }

            processCurrentScreen()
        } catch (_: Throwable) {
            // Self-protecting event loop: never crash on unexpected external view structures
        }
    }

    private fun processCurrentScreen() {
        val root = rootInActiveWindow ?: run {
            overlay?.hideAll()
            stabilityGate.reset()
            return
        }

        val treeReading = TreeReader.readTree(root)

        // Capture snapshot text for clipboard export without exposing PII
        val dumpSummary = "--- فحص الشاشة الحالية ---\n" +
                "طلب معروض: ${treeReading.isRequestScreen}\n" +
                "السعر المقروء: ${treeReading.fare ?: "غير متوفر"}\n" +
                "سعر زر القبول: ${treeReading.acceptButtonPrice ?: "غير متوفر"}\n" +
                "مسافة التقاط: ${treeReading.pickupKm ?: "غير متوفر"}\n" +
                "مسافة رحلة: ${treeReading.tripKm ?: "غير متوفر"}\n" +
                "دقائق: ${treeReading.minutes ?: "غير متوفر"}\n" +
                "الشرائح المقترحة: ${treeReading.suggestedChips.joinToString(", ")}\n" +
                "عدد البطاقات: ${treeReading.cards.size}\n" +
                "بصمة الشاشة: ${treeReading.fingerprint}\n"
        SettingsRepository.latestScreenDump = dumpSummary

        if (!treeReading.isRequestScreen) {
            overlay?.hideAll()
            stabilityGate.reset()
            lastTreeFingerprint = ""
            return
        }

        // Battery optimization: skip re-computation if tree fingerprint has not changed
        if (treeReading.fingerprint.isNotEmpty() && treeReading.fingerprint == lastTreeFingerprint) {
            return
        }
        lastTreeFingerprint = treeReading.fingerprint

        // If big fare and accept button price differ -> price mismatch -> Suspicious
        if (treeReading.hasPriceMismatch) {
            stabilityGate.reset()
            showResult(CoreMathResult.Suspicious("عدم تطابق في السعر"), treeReading)
            return
        }

        val treeRaw = RawReading(
            price = treeReading.fare,
            pickupKm = treeReading.pickupKm,
            tripKm = treeReading.tripKm,
            minutes = treeReading.minutes
        )

        if (currentSettings.doubleCheckWithOcr) {
            // Cancel running OCR pass and rate-limit to 300ms
            ocrJob?.cancel()
            ocrJob = serviceScope.launch {
                delay(300)
                captureAndMergeOcr(treeRaw, treeReading)
            }
        } else {
            // Single-pass tree processing
            val mergeResult = ReadingMerger.merge(treeRaw, null)
            handleMergeResult(mergeResult, treeReading)
        }
    }

    private fun captureAndMergeOcr(treeRaw: RawReading, treeReading: com.example.reading.TreeReading) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: ScreenshotResult) {
                        val hardwareBitmap = Bitmap.wrapHardwareBuffer(
                            screenshotResult.hardwareBuffer,
                            screenshotResult.colorSpace
                        )
                        if (hardwareBitmap != null) {
                            val softwareBitmap = hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)
                            hardwareBitmap.recycle()
                            screenshotResult.hardwareBuffer.close()

                            if (softwareBitmap != null) {
                                serviceScope.launch(Dispatchers.Default) {
                                    val cardBounds = treeReading.cards.firstOrNull()?.bounds
                                    val ocr = OcrReader.processScreenshot(softwareBitmap, cardBounds)
                                    softwareBitmap.recycle()

                                    val ocrRaw = if (!ocr.isBlackScreen) {
                                        RawReading(ocr.fare, ocr.pickupKm, ocr.tripKm, ocr.minutes)
                                    } else {
                                        null
                                    }

                                    val mergeResult = ReadingMerger.merge(treeRaw, ocrRaw)
                                    launch(Dispatchers.Main) {
                                        handleMergeResult(mergeResult, treeReading)
                                    }
                                }
                                return
                            }
                        }
                        // Fallback to tree if bitmap capture fails
                        val fallbackMerge = ReadingMerger.merge(treeRaw, null)
                        handleMergeResult(fallbackMerge, treeReading)
                    }

                    override fun onFailure(errorCode: Int) {
                        val fallbackMerge = ReadingMerger.merge(treeRaw, null)
                        handleMergeResult(fallbackMerge, treeReading)
                    }
                }
            )
        } else {
            val fallbackMerge = ReadingMerger.merge(treeRaw, null)
            handleMergeResult(fallbackMerge, treeReading)
        }
    }

    private fun handleMergeResult(
        mergeResult: MergeResult,
        treeReading: com.example.reading.TreeReading
    ) {
        when (mergeResult) {
            is MergeResult.Accepted -> {
                val frameReading = FrameReading(
                    price = mergeResult.price,
                    pickupKm = mergeResult.pickupKm,
                    tripKm = mergeResult.tripKm
                )

                when (val gate = stabilityGate.feed(frameReading)) {
                    is GateStatus.Confirmed -> {
                        val mathResult = CoreMath.calculate(
                            price = gate.reading.price,
                            pickupKm = gate.reading.pickupKm,
                            tripKm = gate.reading.tripKm,
                            myMinimumPerKm = currentSettings.myMinimumPerKm,
                            tolerancePercent = currentSettings.tolerancePercent,
                            distanceMode = currentSettings.distanceMode,
                            deductCommission = currentSettings.deductCommission,
                            commissionPercent = currentSettings.commissionPercent,
                            judgeBy = currentSettings.judgeBy,
                            suggestedPrices = treeReading.suggestedChips
                        )
                        showResult(mathResult, treeReading)
                    }
                    is GateStatus.Unconfirmed -> {
                        // First frame shows neutral "…"
                        showResult(CoreMathResult.Missing("…"), treeReading)
                    }
                    is GateStatus.Idle -> {
                        overlay?.hideAll()
                    }
                }
            }
            is MergeResult.Suspicious -> {
                stabilityGate.reset()
                showResult(CoreMathResult.Suspicious(mergeResult.reason), treeReading)
            }
            is MergeResult.Missing -> {
                stabilityGate.reset()
                showResult(CoreMathResult.Missing(mergeResult.reason), treeReading)
            }
        }
    }

    private fun showResult(result: CoreMathResult, treeReading: com.example.reading.TreeReading) {
        val occupied = treeReading.occupiedBounds.map {
            SimpleRect(it.left, it.top, it.right, it.bottom)
        }
        val firstCard = treeReading.cards.firstOrNull()
        val priceBounds = firstCard?.bounds?.let {
            SimpleRect(it.left, it.top, it.right, it.bottom)
        }

        val savedPoint = if (currentSettings.savedIndicatorX >= 0 && currentSettings.savedIndicatorY >= 0) {
            Point(currentSettings.savedIndicatorX, currentSettings.savedIndicatorY)
        } else {
            null
        }

        // Render main floating indicator
        overlay?.updateMainIndicator(
            result = result,
            size = currentSettings.indicatorSize,
            priceBounds = priceBounds,
            savedPosition = savedPoint,
            occupiedBounds = occupied
        )

        // Render suggested chip labels if present and enabled
        if (currentSettings.showSuggestedChips && result is CoreMathResult.Success) {
            val chipBoundsList = treeReading.suggestedChips.indices.mapNotNull { idx ->
                treeReading.occupiedBounds.getOrNull(idx)?.let {
                    SimpleRect(it.left, it.top, it.right, it.bottom)
                }
            }
            overlay?.updateChipLabels(result.chips, chipBoundsList)
        }

        // Render per-card badges if feed contains multiple cards
        if (currentSettings.showCardBadges && treeReading.cards.size > 1) {
            overlay?.updateCardBadges(treeReading.cards) { card ->
                computeForCard(card)
            }
        }
    }

    private fun computeForCard(card: CardSnapshot): CoreMathResult {
        return CoreMath.calculate(
            price = card.fare,
            pickupKm = card.pickupKm,
            tripKm = card.tripKm,
            myMinimumPerKm = currentSettings.myMinimumPerKm,
            tolerancePercent = currentSettings.tolerancePercent,
            distanceMode = currentSettings.distanceMode,
            deductCommission = currentSettings.deductCommission,
            commissionPercent = currentSettings.commissionPercent,
            judgeBy = currentSettings.judgeBy
        )
    }

    override fun onInterrupt() {
        overlay?.hideAll()
        stabilityGate.reset()
    }

    override fun onDestroy() {
        super.onDestroy()
        overlay?.hideAll()
        serviceScope.cancel()
    }
}
