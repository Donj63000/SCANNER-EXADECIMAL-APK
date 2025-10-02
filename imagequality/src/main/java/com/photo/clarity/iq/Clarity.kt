package com.photo.clarity.iq

import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class ClarityMethod {
    LAPLACIAN,
    TENENGRAD
}

data class ClarityEvaluation(
    val score: Double,
    val method: ClarityMethod,
    val laplacianScore: Double,
    val tenengradScore: Double?
)

object Clarity {
    private const val NEAR_ZERO_VARIANCE = 1e-6

    fun varianceOfLaplacian(width: Int, height: Int, luminance: ByteArray): Double {
        require(luminance.size == width * height) { "Luminance size mismatch" }
        return varianceOfLaplacian(width, height) { luminance[it].toInt() and 0xFF }
    }

    fun varianceOfLaplacian(width: Int, height: Int, luminance: IntArray): Double {
        require(luminance.size == width * height) { "Luminance size mismatch" }
        return varianceOfLaplacian(width, height) { luminance[it] }
    }

    fun tenengrad(width: Int, height: Int, luminance: ByteArray): Double {
        require(luminance.size == width * height) { "Luminance size mismatch" }
        return tenengrad(width, height) { luminance[it].toInt() and 0xFF }
    }

    fun tenengrad(width: Int, height: Int, luminance: IntArray): Double {
        require(luminance.size == width * height) { "Luminance size mismatch" }
        return tenengrad(width, height) { luminance[it] }
    }

    fun clarityScore(width: Int, height: Int, luminance: ByteArray, varianceThreshold: Double = NEAR_ZERO_VARIANCE): ClarityEvaluation {
        val laplacianValue = sanitizeScore(varianceOfLaplacian(width, height, luminance))
        if (laplacianValue > varianceThreshold) {
            return ClarityEvaluation(laplacianValue, ClarityMethod.LAPLACIAN, laplacianValue, null)
        }
        val tenengradValue = sanitizeScore(tenengrad(width, height, luminance))
        return ClarityEvaluation(tenengradValue, ClarityMethod.TENENGRAD, laplacianValue, tenengradValue)
    }

    fun clarityScore(width: Int, height: Int, luminance: IntArray, varianceThreshold: Double = NEAR_ZERO_VARIANCE): ClarityEvaluation {
        val laplacianValue = sanitizeScore(varianceOfLaplacian(width, height, luminance))
        if (laplacianValue > varianceThreshold) {
            return ClarityEvaluation(laplacianValue, ClarityMethod.LAPLACIAN, laplacianValue, null)
        }
        val tenengradValue = sanitizeScore(tenengrad(width, height, luminance))
        return ClarityEvaluation(tenengradValue, ClarityMethod.TENENGRAD, laplacianValue, tenengradValue)
    }

    fun relativePercentages(scoreA: Double, scoreB: Double): Pair<Int, Int> {
        val sanitizedA = sanitizeScore(scoreA)
        val sanitizedB = sanitizeScore(scoreB)
        val total = sanitizedA + sanitizedB
        if (total <= 0.0) {
            return 50 to 50
        }
        val percentA = ((sanitizedA / total) * 100.0).roundToInt().coerceIn(0, 100)
        val percentB = (100 - percentA).coerceIn(0, 100)
        return percentA to percentB
    }

    private inline fun varianceOfLaplacian(width: Int, height: Int, accessor: (Int) -> Int): Double {
        if (width < 3 || height < 3) return 0.0
        var sum = 0.0
        var sumSq = 0.0
        var count = 0
        val stride = width
        for (y in 1 until height - 1) {
            val row = y * stride
            for (x in 1 until width - 1) {
                val index = row + x
                val laplacian = accessor(index) * 4 - accessor(index - 1) - accessor(index + 1) - accessor(index - stride) - accessor(index + stride)
                val value = laplacian.toDouble()
                sum += value
                sumSq += value * value
                count++
            }
        }
        if (count == 0) return 0.0
        val mean = sum / count
        val variance = sumSq / count - mean * mean
        return if (variance.isFinite()) variance else 0.0
    }

    private inline fun tenengrad(width: Int, height: Int, accessor: (Int) -> Int): Double {
        if (width < 3 || height < 3) return 0.0
        val area = (width - 2) * (height - 2)
        if (area <= 0) return 0.0
        val stride = width
        var sum = 0.0
        for (y in 1 until height - 1) {
            val row = y * stride
            for (x in 1 until width - 1) {
                val index = row + x
                val topLeft = accessor(index - stride - 1)
                val top = accessor(index - stride)
                val topRight = accessor(index - stride + 1)
                val left = accessor(index - 1)
                val right = accessor(index + 1)
                val bottomLeft = accessor(index + stride - 1)
                val bottom = accessor(index + stride)
                val bottomRight = accessor(index + stride + 1)
                val gx = -topLeft - 2 * left - bottomLeft + topRight + 2 * right + bottomRight
                val gy = -topLeft - 2 * top - topRight + bottomLeft + 2 * bottom + bottomRight
                val magnitude = sqrt((gx * gx + gy * gy).toDouble())
                sum += magnitude
            }
        }
        val average = sum / area
        return if (average.isFinite()) average else 0.0
    }

    private fun sanitizeScore(value: Double): Double {
        if (!value.isFinite()) return 0.0
        if (value < 0.0) return 0.0
        return value
    }
}
