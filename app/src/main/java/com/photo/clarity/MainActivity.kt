package com.photo.clarity

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.photo.clarity.R
import com.photo.clarity.ui.CompareScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val context = LocalContext.current
                    var photoA by remember { mutableStateOf<Bitmap?>(null) }
                    var photoB by remember { mutableStateOf<Bitmap?>(null) }
                    var clarityResult by remember { mutableStateOf<ClarityResult?>(null) }
                    var pendingSlot by remember { mutableStateOf<PhotoSlot?>(null) }
                    var isComparing by remember { mutableStateOf(false) }
                    val coroutineScope = rememberCoroutineScope()
                    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
                        val slot = pendingSlot
                        if (bitmap != null && slot != null) {
                            when (slot) {
                                PhotoSlot.A -> photoA = bitmap
                                PhotoSlot.B -> photoB = bitmap
                            }
                            clarityResult = null
                        }
                        pendingSlot = null
                    }
                    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                        if (granted) {
                            if (pendingSlot != null) {
                                cameraLauncher.launch(null)
                            }
                        } else {
                            Toast.makeText(context, context.getString(R.string.camera_permission_required), Toast.LENGTH_SHORT).show()
                            pendingSlot = null
                        }
                    }
                    fun launchCamera(slot: PhotoSlot) {
                        pendingSlot = slot
                        val status = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                        if (status == PackageManager.PERMISSION_GRANTED) {
                            cameraLauncher.launch(null)
                        } else {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                    CompareScreen(
                        photoA = photoA,
                        photoB = photoB,
                        clarityResult = clarityResult,
                        isComparing = isComparing,
                        onTakePhoto = { slot -> launchCamera(slot) },
                        onClearPhoto = { slot ->
                            when (slot) {
                                PhotoSlot.A -> photoA = null
                                PhotoSlot.B -> photoB = null
                            }
                            clarityResult = null
                        },
                        onCompare = {
                            val first = photoA
                            val second = photoB
                            if (first != null && second != null && !isComparing) {
                                isComparing = true
                                clarityResult = null
                                coroutineScope.launch(Dispatchers.Default) {
                                    val result = calculateClarity(first, second)
                                    withContext(Dispatchers.Main) {
                                        clarityResult = result
                                        isComparing = false
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun calculateClarity(bitmapA: Bitmap, bitmapB: Bitmap): ClarityResult {
    val varianceA = varianceOfLaplacian(bitmapA)
    val varianceB = varianceOfLaplacian(bitmapB)
    val useFallback = varianceA <= 1e-6 && varianceB <= 1e-6
    val scoreA = if (useFallback) tenengrad(bitmapA) else varianceA
    val scoreB = if (useFallback) tenengrad(bitmapB) else varianceB
    val total = scoreA + scoreB
    return if (total <= 0.0) {
        ClarityResult(50, 50)
    } else {
        val rawPercentA = ((scoreA / total) * 100.0).roundToInt().coerceIn(0, 100)
        val percentB = (100 - rawPercentA).coerceIn(0, 100)
        ClarityResult(rawPercentA, percentB)
    }
}

private fun varianceOfLaplacian(bitmap: Bitmap): Double {
    val processed = bitmap.downscale(1024)
    val width = processed.width
    val height = processed.height
    if (width < 3 || height < 3) {
        if (processed !== bitmap) {
            processed.recycle()
        }
        return 0.0
    }
    val pixels = IntArray(width * height)
    processed.getPixels(pixels, 0, width, 0, 0, width, height)
    val luminance = DoubleArray(pixels.size)
    for (i in pixels.indices) {
        luminance[i] = toLuma(pixels[i])
    }
    var sum = 0.0
    var sumSquares = 0.0
    var count = 0
    for (y in 1 until height - 1) {
        for (x in 1 until width - 1) {
            val index = y * width + x
            val laplacian = 4 * luminance[index] - luminance[index - width] - luminance[index + width] - luminance[index - 1] - luminance[index + 1]
            sum += laplacian
            sumSquares += laplacian * laplacian
            count++
        }
    }
    val mean = sum / count
    val variance = sumSquares / count - mean * mean
    if (processed !== bitmap) {
        processed.recycle()
    }
    return variance
}

private fun tenengrad(bitmap: Bitmap): Double {
    val processed = bitmap.downscale(1024)
    val width = processed.width
    val height = processed.height
    if (width < 3 || height < 3) {
        if (processed !== bitmap) {
            processed.recycle()
        }
        return 0.0
    }
    val pixels = IntArray(width * height)
    processed.getPixels(pixels, 0, width, 0, 0, width, height)
    val luminance = DoubleArray(pixels.size)
    for (i in pixels.indices) {
        luminance[i] = toLuma(pixels[i])
    }
    var sum = 0.0
    for (y in 1 until height - 1) {
        for (x in 1 until width - 1) {
            val index = y * width + x
            val gx = -luminance[index - width - 1] - 2 * luminance[index - 1] - luminance[index + width - 1] + luminance[index - width + 1] + 2 * luminance[index + 1] + luminance[index + width + 1]
            val gy = -luminance[index - width - 1] - 2 * luminance[index - width] - luminance[index - width + 1] + luminance[index + width - 1] + 2 * luminance[index + width] + luminance[index + width + 1]
            sum += gx * gx + gy * gy
        }
    }
    val average = sum / ((width - 2) * (height - 2))
    if (processed !== bitmap) {
        processed.recycle()
    }
    return average
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

private fun toLuma(pixel: Int): Double {
    val r = (pixel shr 16) and 0xFF
    val g = (pixel shr 8) and 0xFF
    val b = pixel and 0xFF
    return 0.299 * r + 0.587 * g + 0.114 * b
}
