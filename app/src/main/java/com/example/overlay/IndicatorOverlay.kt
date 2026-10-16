package com.example.overlay

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.example.data.IndicatorSize
import com.example.domain.ChipResult
import com.example.domain.CoreMathResult
import com.example.domain.IndicatorColor
import java.math.BigDecimal

/**
 * Native Android View overlays rendered via TYPE_ACCESSIBILITY_OVERLAY.
 * Requires zero SYSTEM_ALERT_WINDOW permission.
 * Draggable main pill indicator, chip labels, and per-card badges.
 */
class IndicatorOverlay(
    private val service: AccessibilityService,
    private val onPositionSaved: (Int, Int) -> Unit
) {

    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    // Views
    private var mainIndicatorView: LinearLayout? = null
    private var mainParams: WindowManager.LayoutParams? = null

    private val chipViews = mutableListOf<View>()
    private val cardBadgeViews = mutableMapOf<String, View>()

    // Color palette as mandated by specification
    private val COLOR_GREEN = Color.parseColor("#00C853")
    private val COLOR_ORANGE = Color.parseColor("#FF9100")
    private val COLOR_RED = Color.parseColor("#D50000")
    private val COLOR_GREY = Color.parseColor("#616161")

    private var currentSize = IndicatorSize.MEDIUM
    private var lastPlacedX = -1
    private var lastPlacedY = -1

    fun updateMainIndicator(
        result: CoreMathResult?,
        size: IndicatorSize,
        priceBounds: SimpleRect?,
        savedPosition: Point?,
        occupiedBounds: List<SimpleRect>
    ) {
        if (result == null) {
            hideMainIndicator()
            return
        }

        currentSize = size
        val displayMetrics = service.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // Insets safe zone
        val topInset = (48 * displayMetrics.density).toInt()
        val bottomInset = (64 * displayMetrics.density).toInt()

        val dims = getPillDimensions(size, displayMetrics.density)
        val pillWidth = dims.first
        val pillHeight = dims.second

        val placement = Placement.findPlacement(
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            topInset = topInset,
            bottomInset = bottomInset,
            indicatorWidth = pillWidth,
            indicatorHeight = pillHeight,
            targetPriceBounds = priceBounds,
            savedDragPosition = savedPosition,
            occupiedBounds = occupiedBounds
        )

        lastPlacedX = placement.x
        lastPlacedY = placement.y

        val view = mainIndicatorView ?: createMainIndicatorView().also { mainIndicatorView = it }
        bindMainIndicatorData(view, result, size)

        val params = mainParams ?: WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = placement.x
            y = placement.y
            mainParams = this
        }

        params.x = placement.x
        params.y = placement.y

        if (view.parent == null) {
            try {
                windowManager.addView(view, params)
            } catch (_: Exception) { }
        } else {
            try {
                windowManager.updateViewLayout(view, params)
            } catch (_: Exception) { }
        }
    }

    fun updateChipLabels(chips: List<ChipResult>, chipBoundsList: List<SimpleRect>) {
        hideChipLabels()
        if (chips.isEmpty() || chipBoundsList.isEmpty()) return

        for (i in 0 until minOf(chips.size, chipBoundsList.size)) {
            val chip = chips[i]
            val bounds = chipBoundsList[i]
            val chipView = createChipBadgeView(chip)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = bounds.left + maxOf(0, (bounds.width - 120) / 2)
                y = maxOf(30, bounds.top - 40)
            }

            try {
                windowManager.addView(chipView, params)
                chipViews.add(chipView)
            } catch (_: Exception) { }
        }
    }

    fun updateCardBadges(
        cards: List<com.example.reading.CardSnapshot>,
        calculatePerCard: (com.example.reading.CardSnapshot) -> CoreMathResult
    ) {
        val activeIds = cards.map { it.cardId }.toSet()

        // Remove cards no longer present
        val iterator = cardBadgeViews.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!activeIds.contains(entry.key)) {
                try {
                    windowManager.removeView(entry.value)
                } catch (_: Exception) { }
                iterator.remove()
            }
        }

        val displayMetrics = service.resources.displayMetrics
        val density = displayMetrics.density

        for (card in cards) {
            val result = calculatePerCard(card)
            val badgeView = cardBadgeViews.getOrPut(card.cardId) {
                createCardBadgeView().also { view ->
                    val params = WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT
                    ).apply {
                        gravity = Gravity.TOP or Gravity.START
                    }
                    try {
                        windowManager.addView(view, params)
                    } catch (_: Exception) { }
                }
            }

            bindCardBadgeData(badgeView, card, result)

            val params = badgeView.layoutParams as? WindowManager.LayoutParams ?: continue
            params.x = (card.bounds.right - (140 * density).toInt()).coerceAtLeast(16)
            params.y = (card.bounds.top + 8).coerceAtLeast(40)
            try {
                windowManager.updateViewLayout(badgeView, params)
            } catch (_: Exception) { }
        }
    }

    fun hideAll() {
        hideMainIndicator()
        hideChipLabels()
        hideCardBadges()
    }

    private fun hideMainIndicator() {
        mainIndicatorView?.let { view ->
            if (view.parent != null) {
                try {
                    windowManager.removeView(view)
                } catch (_: Exception) { }
            }
        }
        mainIndicatorView = null
        mainParams = null
    }

    private fun hideChipLabels() {
        for (view in chipViews) {
            if (view.parent != null) {
                try {
                    windowManager.removeView(view)
                } catch (_: Exception) { }
            }
        }
        chipViews.clear()
    }

    private fun hideCardBadges() {
        for (view in cardBadgeViews.values) {
            if (view.parent != null) {
                try {
                    windowManager.removeView(view)
                } catch (_: Exception) { }
            }
        }
        cardBadgeViews.clear()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createMainIndicatorView(): LinearLayout {
        val density = service.resources.displayMetrics.density

        val container = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt())
            elevation = 16 * density
        }

        // Draggable touch listener
        container.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val params = mainParams ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        try {
                            windowManager.updateViewLayout(v, params)
                        } catch (_: Exception) { }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        onPositionSaved(params.x, params.y)
                        return true
                    }
                }
                return false
            }
        })

        val primaryText = TextView(service).apply {
            id = View.generateViewId()
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        container.addView(primaryText)

        val secondaryText = TextView(service).apply {
            id = View.generateViewId()
            setTextColor(Color.parseColor("#E0E0E0"))
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        container.addView(secondaryText)

        val minPriceText = TextView(service).apply {
            id = View.generateViewId()
            setTextColor(Color.parseColor("#FFF9C4"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        container.addView(minPriceText)

        return container
    }

    private fun bindMainIndicatorData(view: LinearLayout, result: CoreMathResult, size: IndicatorSize) {
        val density = service.resources.displayMetrics.density
        val primaryText = view.getChildAt(0) as TextView
        val secondaryText = view.getChildAt(1) as TextView
        val minPriceText = view.getChildAt(2) as TextView

        val textScale = when (size) {
            IndicatorSize.SMALL -> 0.85f
            IndicatorSize.MEDIUM -> 1.0f
            IndicatorSize.LARGE -> 1.25f
        }

        primaryText.textSize = 18f * textScale
        secondaryText.textSize = 12f * textScale
        minPriceText.textSize = 12f * textScale

        val bg = GradientDrawable().apply {
            cornerRadius = 24 * density
        }

        when (result) {
            is CoreMathResult.Success -> {
                bg.setColor(mapColor(result.color))
                primaryText.text = result.displayText

                if (result.otherPerKm != null) {
                    secondaryText.visibility = View.VISIBLE
                    secondaryText.text = "آخر: ${result.otherPerKm} ج/كم"
                } else {
                    secondaryText.visibility = View.GONE
                }

                minPriceText.visibility = View.VISIBLE
                minPriceText.text = "أقل سعر: ${result.minAcceptablePrice} ج"
            }
            is CoreMathResult.Suspicious -> {
                bg.setColor(COLOR_GREY)
                primaryText.text = result.reason
                secondaryText.visibility = View.GONE
                minPriceText.visibility = View.GONE
            }
            is CoreMathResult.Missing -> {
                bg.setColor(COLOR_GREY)
                primaryText.text = result.reason
                secondaryText.visibility = View.GONE
                minPriceText.visibility = View.GONE
            }
        }

        view.background = bg
    }

    private fun createChipBadgeView(chip: ChipResult): View {
        val density = service.resources.displayMetrics.density
        return TextView(service).apply {
            text = "${chip.perKm} ج/كم"
            setTextColor(Color.WHITE)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((6 * density).toInt(), (3 * density).toInt(), (6 * density).toInt(), (3 * density).toInt())
            background = GradientDrawable().apply {
                setColor(mapColor(chip.color))
                cornerRadius = 12 * density
            }
        }
    }

    private fun createCardBadgeView(): LinearLayout {
        val density = service.resources.displayMetrics.density
        return LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((8 * density).toInt(), (4 * density).toInt(), (8 * density).toInt(), (4 * density).toInt())
            elevation = 8 * density

            val label = TextView(service).apply {
                setTextColor(Color.WHITE)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
            }
            addView(label)
        }
    }

    private fun bindCardBadgeData(view: View, card: com.example.reading.CardSnapshot, result: CoreMathResult) {
        val density = service.resources.displayMetrics.density
        val container = view as LinearLayout
        val label = container.getChildAt(0) as TextView

        val bg = GradientDrawable().apply {
            cornerRadius = 16 * density
        }

        when (result) {
            is CoreMathResult.Success -> {
                bg.setColor(mapColor(result.color))
                val qualityWord = when (result.color) {
                    IndicatorColor.GREEN -> "ممتاز"
                    IndicatorColor.ORANGE -> "مقبول"
                    IndicatorColor.RED -> "ضعيف"
                    IndicatorColor.GREY -> "؟"
                }
                label.text = "${card.fare ?: "?"}ج • ${result.totalKm}كم • ${result.judgedPerKm} ($qualityWord)"
            }
            is CoreMathResult.Suspicious -> {
                bg.setColor(COLOR_GREY)
                label.text = "مشكوك فيه"
            }
            is CoreMathResult.Missing -> {
                bg.setColor(COLOR_GREY)
                label.text = "؟"
            }
        }
        view.background = bg
    }

    private fun getPillDimensions(size: IndicatorSize, density: Float): Pair<Int, Int> {
        return when (size) {
            IndicatorSize.SMALL -> Pair((110 * density).toInt(), (52 * density).toInt())
            IndicatorSize.MEDIUM -> Pair((140 * density).toInt(), (64 * density).toInt())
            IndicatorSize.LARGE -> Pair((170 * density).toInt(), (78 * density).toInt())
        }
    }

    private fun mapColor(color: IndicatorColor): Int {
        return when (color) {
            IndicatorColor.GREEN -> COLOR_GREEN
            IndicatorColor.ORANGE -> COLOR_ORANGE
            IndicatorColor.RED -> COLOR_RED
            IndicatorColor.GREY -> COLOR_GREY
        }
    }
}
