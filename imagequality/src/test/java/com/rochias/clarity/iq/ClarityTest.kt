package com.rochias.clarity.iq

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClarityTest {
    @Test
    fun sharperPatternProducesHigherScoreThanSoftPattern() {
        val width = 5
        val height = 5
        val sharp = generatePattern(width, height) { x, y ->
            if ((x + y) % 2 == 0) 32 else 224
        }
        val soft = generatePattern(width, height) { x, y ->
            if ((x + y) % 2 == 0) 96 else 160
        }

        val sharpScore = Clarity.clarityScore(width, height, sharp)
        val softScore = Clarity.clarityScore(width, height, soft)

        assertEquals(ClarityMethod.LAPLACIAN, sharpScore.method)
        assertEquals(ClarityMethod.LAPLACIAN, softScore.method)
        assertTrue(sharpScore.score > softScore.score)
    }

    @Test
    fun identicalPatternsReturnComparableScores() {
        val width = 6
        val height = 6
        val pattern = generatePattern(width, height) { x, y ->
            (x * y * 17 + 64) % 256
        }

        val first = Clarity.clarityScore(width, height, pattern)
        val second = Clarity.clarityScore(width, height, pattern.copyOf())

        assertEquals(ClarityMethod.LAPLACIAN, first.method)
        assertEquals(ClarityMethod.LAPLACIAN, second.method)
        assertTrue(abs(first.score - second.score) < 1e-6)
    }

    @Test
    fun lowDetailBuffersFallbackToTenengrad() {
        val width = 5
        val height = 5
        val flat = generatePattern(width, height) { _, _ -> 128 }
        val subtleEdge = generatePattern(width, height) { x, _ ->
            if (x == 2) 140 else 128
        }

        val flatScore = Clarity.clarityScore(width, height, flat, varianceThreshold = Double.POSITIVE_INFINITY)
        val subtleScore = Clarity.clarityScore(width, height, subtleEdge, varianceThreshold = Double.POSITIVE_INFINITY)

        assertEquals(ClarityMethod.TENENGRAD, flatScore.method)
        assertEquals(ClarityMethod.TENENGRAD, subtleScore.method)
        assertTrue(flatScore.score <= 1e-9)
        assertTrue(subtleScore.score > flatScore.score)
        assertTrue((subtleScore.tenengradScore ?: 0.0) > 0.0)
    }

    private fun generatePattern(width: Int, height: Int, block: (Int, Int) -> Int): IntArray {
        val buffer = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                buffer[y * width + x] = block(x, y)
            }
        }
        return buffer
    }
}
