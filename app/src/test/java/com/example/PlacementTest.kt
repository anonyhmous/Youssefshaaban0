package com.example

import com.example.overlay.Placement
import com.example.overlay.Point
import com.example.overlay.SimpleRect
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacementTest {

    @Test
    fun testPlacementLeftOfPrice_whenFree() {
        val priceBounds = SimpleRect(left = 500, top = 200, right = 700, bottom = 260)
        val placement = Placement.findPlacement(
            screenWidth = 1080,
            screenHeight = 2400,
            topInset = 100,
            bottomInset = 150,
            indicatorWidth = 200,
            indicatorHeight = 80,
            targetPriceBounds = priceBounds,
            savedDragPosition = null,
            occupiedBounds = emptyList()
        )

        assertNotNull(placement)
        // Indicator right edge should be <= priceBounds.left
        assertTrue(placement.x + 200 <= priceBounds.left)
    }

    @Test
    fun testCollisionAvoidance_doesNotOverlapObstacles() {
        val obstacle = SimpleRect(left = 280, top = 190, right = 510, bottom = 270)
        val priceBounds = SimpleRect(left = 500, top = 200, right = 700, bottom = 260)

        val placement = Placement.findPlacement(
            screenWidth = 1080,
            screenHeight = 2400,
            topInset = 100,
            bottomInset = 150,
            indicatorWidth = 200,
            indicatorHeight = 80,
            targetPriceBounds = priceBounds,
            savedDragPosition = Point(100, 500),
            occupiedBounds = listOf(obstacle)
        )

        val indicatorRect = SimpleRect(
            left = placement.x,
            top = placement.y,
            right = placement.x + 200,
            bottom = placement.y + 80
        )
        assertFalse(indicatorRect.intersects(obstacle))
    }

    @Test
    fun testHasOverlapCalculation() {
        val r1 = SimpleRect(left = 10, top = 10, right = 50, bottom = 50)
        val r2 = SimpleRect(left = 40, top = 40, right = 80, bottom = 80)
        val r3 = SimpleRect(left = 100, top = 100, right = 200, bottom = 200)

        assertTrue(Placement.hasOverlap(r1, listOf(r2)))
        assertFalse(Placement.hasOverlap(r1, listOf(r3)))
    }
}
