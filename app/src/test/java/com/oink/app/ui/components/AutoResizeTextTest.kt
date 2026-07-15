package com.oink.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [fitFontSizeSp], the pure fit-to-width algorithm behind
 * [AutoResizeText]. A fake linear measurer (width proportional to
 * characters x font size) stands in for the real [androidx.compose.ui.text.TextMeasurer],
 * so the shrink logic is verified without the Compose framework.
 *
 * This is the regression lock for issue #108: a long hero balance must resolve to
 * a size that fits its column rather than being ellipsized.
 */
class AutoResizeTextTest {

    /** width = chars x sp x [pxPerSpPerChar], the shape of a monospace-ish measure. */
    private fun linearMeasurer(text: String, pxPerSpPerChar: Double): (Float) -> Int =
        { sp -> Math.round(text.length * sp * pxPerSpPerChar).toInt() }

    @Test
    fun returnsMaxWhenTextFitsAtMaxSize() {
        val fitted = fitFontSizeSp(
            maxSp = 44f,
            minSp = 24f,
            stepSp = 1f,
            maxWidthPx = 1000,
            measureWidthPx = linearMeasurer("\$1,097.28", pxPerSpPerChar = 1.5)
        )
        assertEquals(44f, fitted, 0.001f)
    }

    @Test
    fun shrinksToLargestFittingSizeForLongBalance() {
        // "$1,097.28" (9 chars) at 44sp measures 9*44*1.5 = 594px, overflowing a
        // 500px column; the largest whole-sp size that fits is 37sp (499.5 -> 500).
        val text = "\$1,097.28"
        val measurer = linearMeasurer(text, pxPerSpPerChar = 1.5)
        val fitted = fitFontSizeSp(
            maxSp = 44f,
            minSp = 24f,
            stepSp = 1f,
            maxWidthPx = 500,
            measureWidthPx = measurer
        )

        assertEquals(37f, fitted, 0.001f)
        // Whatever it returns must actually fit (it is not the floor here).
        assertTrue(measurer(fitted) <= 500)
        // And one step larger must overflow - it is the LARGEST fitting size.
        assertTrue(measurer(fitted + 1f) > 500)
    }

    @Test
    fun neverReturnsBelowFloorEvenWhenNothingFits() {
        val fitted = fitFontSizeSp(
            maxSp = 44f,
            minSp = 24f,
            stepSp = 1f,
            maxWidthPx = 10, // absurdly narrow: even the floor overflows
            measureWidthPx = linearMeasurer("\$12,345.67", pxPerSpPerChar = 1.5)
        )
        assertEquals(24f, fitted, 0.001f)
    }

    @Test
    fun neverReturnsAboveMaxForVeryWideConstraint() {
        val fitted = fitFontSizeSp(
            maxSp = 44f,
            minSp = 24f,
            stepSp = 1f,
            maxWidthPx = 100_000,
            measureWidthPx = linearMeasurer("\$12", pxPerSpPerChar = 1.5)
        )
        assertEquals(44f, fitted, 0.001f)
    }

    @Test
    fun unboundedWidthUsesMaxSize() {
        val fitted = fitFontSizeSp(
            maxSp = 44f,
            minSp = 24f,
            stepSp = 1f,
            maxWidthPx = Int.MAX_VALUE,
            measureWidthPx = { error("must not measure when width is unbounded") }
        )
        assertEquals(44f, fitted, 0.001f)
    }

    @Test
    fun longerStringNeverResolvesLargerThanShorterOneAtSameWidth() {
        val width = 500
        val short = fitFontSizeSp(44f, 24f, 1f, width, linearMeasurer("\$12", 1.5))
        val long = fitFontSizeSp(44f, 24f, 1f, width, linearMeasurer("\$12,345.67", 1.5))
        assertTrue("longer balance must shrink at least as much", long <= short)
    }
}
