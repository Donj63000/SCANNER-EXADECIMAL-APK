package com.photo.clarity.camera

import android.graphics.Bitmap
import android.graphics.ImageFormat
import androidx.camera.core.ImageProxy

class YuvToRgbConverter {
    private var buffer: IntArray? = null

    @Synchronized
    fun convert(image: ImageProxy, output: Bitmap) {
        if (image.format != ImageFormat.YUV_420_888) {
            throw IllegalArgumentException("Unsupported image format")
        }
        if (output.config != Bitmap.Config.ARGB_8888) {
            throw IllegalArgumentException("Unsupported bitmap config")
        }
        if (output.width != image.width || output.height != image.height) {
            throw IllegalArgumentException("Bitmap size mismatch")
        }
        val width = image.width
        val height = image.height
        val pixels = obtainBuffer(width * height)
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        yBuffer.rewind()
        uBuffer.rewind()
        vBuffer.rewind()
        val yRowStride = yPlane.rowStride
        val uRowStride = uPlane.rowStride
        val vRowStride = vPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vPixelStride = vPlane.pixelStride
        var index = 0
        for (row in 0 until height) {
            val yRowStart = row * yRowStride
            val uRowStart = (row shr 1) * uRowStride
            val vRowStart = (row shr 1) * vRowStride
            for (col in 0 until width) {
                val y = yBuffer.get(yRowStart + col).toInt() and 0xFF
                val u = uBuffer.get(uRowStart + (col shr 1) * uPixelStride).toInt() and 0xFF
                val v = vBuffer.get(vRowStart + (col shr 1) * vPixelStride).toInt() and 0xFF
                val yValue = (y - 16).coerceAtLeast(0)
                val uValue = u - 128
                val vValue = v - 128
                val r = (1192 * yValue + 1634 * vValue).coerceIn(0, 262143) shr 10
                val g = (1192 * yValue - 833 * vValue - 400 * uValue).coerceIn(0, 262143) shr 10
                val b = (1192 * yValue + 2066 * uValue).coerceIn(0, 262143) shr 10
                pixels[index] = -0x1000000 or (r shl 16) or (g shl 8) or b
                index++
            }
        }
        output.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    private fun obtainBuffer(size: Int): IntArray {
        val current = buffer
        if (current == null || current.size < size) {
            val created = IntArray(size)
            buffer = created
            return created
        }
        return current
    }
}
