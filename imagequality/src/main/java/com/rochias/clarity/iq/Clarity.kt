package com.rochias.clarity.iq

import android.graphics.Bitmap
import kotlin.math.pow
import kotlin.math.sqrt

object Clarity {
    fun varianceOfLaplacian(bitmap: Bitmap): Double {
        val width = bitmap.width
        val height = bitmap.height
        val grayscale = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16 and 0xFF)
                val g = (pixel shr 8 and 0xFF)
                val b = (pixel and 0xFF)
                grayscale[y * width + x] = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            }
        }
        var sum = 0.0
        var sumSq = 0.0
        var count = 0
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val center = grayscale[y * width + x] * 4
                val value = center - grayscale[y * width + x - 1] - grayscale[y * width + x + 1] - grayscale[(y - 1) * width + x] - grayscale[(y + 1) * width + x]
                sum += value
                sumSq += value.toDouble().pow(2.0)
                count++
            }
        }
        if (count == 0) return 0.0
        val mean = sum / count
        val variance = sumSq / count - mean.pow(2.0)
        return if (variance.isNaN() || variance.isInfinite()) 0.0 else variance
    }

    fun tenengrad(bitmap: Bitmap): Double {
        val width = bitmap.width
        val height = bitmap.height
        val grayscale = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16 and 0xFF)
                val g = (pixel shr 8 and 0xFF)
                val b = (pixel and 0xFF)
                grayscale[y * width + x] = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            }
        }
        var sum = 0.0
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val gx = -grayscale[(y - 1) * width + x - 1] - 2 * grayscale[y * width + x - 1] - grayscale[(y + 1) * width + x - 1] + grayscale[(y - 1) * width + x + 1] + 2 * grayscale[y * width + x + 1] + grayscale[(y + 1) * width + x + 1]
                val gy = -grayscale[(y - 1) * width + x - 1] - 2 * grayscale[(y - 1) * width + x] - grayscale[(y - 1) * width + x + 1] + grayscale[(y + 1) * width + x - 1] + 2 * grayscale[(y + 1) * width + x] + grayscale[(y + 1) * width + x + 1]
                val magnitude = sqrt(gx.toDouble().pow(2.0) + gy.toDouble().pow(2.0))
                sum += magnitude
            }
        }
        return if (sum.isNaN() || sum.isInfinite()) 0.0 else sum / ((width - 2) * (height - 2))
    }
}
