package com.example.overlay

data class SimpleRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top

    fun intersects(other: SimpleRect): Boolean {
        return left < other.right && right > other.left && top < other.bottom && bottom > other.top
    }

    fun padded(padding: Int): SimpleRect {
        return SimpleRect(
            left = left - padding,
            top = top - padding,
            right = right + padding,
            bottom = bottom + padding
        )
    }
}

data class Point(val x: Int, val y: Int)

/**
 * Pure Kotlin collision-avoidance placement engine.
 * Computes non-overlapping positions for floating overlays.
 * Zero Android dependencies.
 */
object Placement {

    private const val PADDING_PX = 4

    fun findPlacement(
        screenWidth: Int,
        screenHeight: Int,
        topInset: Int,
        bottomInset: Int,
        indicatorWidth: Int,
        indicatorHeight: Int,
        targetPriceBounds: SimpleRect?,
        savedDragPosition: Point?,
        occupiedBounds: List<SimpleRect>
    ): Point {
        val paddedOccupied = occupiedBounds.map { it.padded(PADDING_PX) }

        val minX = 16
        val maxX = maxOf(minX, screenWidth - indicatorWidth - 16)
        val minY = topInset + 16
        val maxY = maxOf(minY, screenHeight - bottomInset - indicatorHeight - 16)

        // 1. Try to the left of the price row
        if (targetPriceBounds != null) {
            val candidateX = (targetPriceBounds.left - indicatorWidth - 12).coerceIn(minX, maxX)
            val candidateY = (targetPriceBounds.top + (targetPriceBounds.height - indicatorHeight) / 2)
                .coerceIn(minY, maxY)
            val candidateRect = SimpleRect(
                candidateX,
                candidateY,
                candidateX + indicatorWidth,
                candidateY + indicatorHeight
            )
            if (!hasOverlap(candidateRect, paddedOccupied)) {
                return Point(candidateX, candidateY)
            }
        }

        // 2. Try the user's saved drag position
        if (savedDragPosition != null) {
            val candidateX = savedDragPosition.x.coerceIn(minX, maxX)
            val candidateY = savedDragPosition.y.coerceIn(minY, maxY)
            val candidateRect = SimpleRect(
                candidateX,
                candidateY,
                candidateX + indicatorWidth,
                candidateY + indicatorHeight
            )
            if (!hasOverlap(candidateRect, paddedOccupied)) {
                return Point(candidateX, candidateY)
            }
        }

        // 3. Scan free spots on screen
        val stepX = maxOf(40, indicatorWidth / 2)
        val stepY = maxOf(40, indicatorHeight / 2)

        for (y in minY..maxY step stepY) {
            for (x in minX..maxX step stepX) {
                val candidateRect = SimpleRect(x, y, x + indicatorWidth, y + indicatorHeight)
                if (!hasOverlap(candidateRect, paddedOccupied)) {
                    return Point(x, y)
                }
            }
        }

        // 4. Fallback to clamped saved position or top-right safe position
        val fallbackX = (savedDragPosition?.x ?: maxX).coerceIn(minX, maxX)
        val fallbackY = (savedDragPosition?.y ?: (minY + 40)).coerceIn(minY, maxY)
        return Point(fallbackX, fallbackY)
    }

    fun hasOverlap(target: SimpleRect, obstacles: List<SimpleRect>): Boolean {
        for (obs in obstacles) {
            if (target.intersects(obs)) {
                return true
            }
        }
        return false
    }
}
