package com.photo.clarity.processing

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.roundToInt

data class LuminanceImage(
    val width: Int,
    val height: Int,
    val luminance: ByteArray
)

fun extractLuminance(bitmap: Bitmap, maxDimension: Int = 1024): LuminanceImage {
    val processed = bitmap.downscale(maxDimension)
    val width = processed.width
    val height = processed.height
    val pixels = IntArray(width * height)
    processed.getPixels(pixels, 0, width, 0, 0, width, height)
    val buffer = ByteArray(pixels.size)
    for (index in pixels.indices) {
        val pixel = pixels[index]
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        val value = ((77 * r + 150 * g + 29 * b) shr 8).coerceIn(0, 255)
        buffer[index] = value.toByte()
    }
    if (processed !== bitmap) {
        processed.recycle()
    }
    return LuminanceImage(width, height, buffer)
}

private fun Bitmap.downscale(maxDimension: Int): Bitmap {
    val largest = max(width, height)
    if (largest <= maxDimension) {
        return this
    }
    val scale = maxDimension.toFloat() / largest.toFloat()
    val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
    val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
}
