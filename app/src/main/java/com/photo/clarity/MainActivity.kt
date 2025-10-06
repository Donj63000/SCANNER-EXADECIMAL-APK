package com.photo.clarity

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.core.content.ContextCompat
import com.photo.clarity.R
import com.photo.clarity.ui.CompareScreen
import com.photo.clarity.PhotoSlot
import com.photo.clarity.camera.CameraCaptureState
import com.photo.clarity.camera.CameraPreview
import com.photo.clarity.camera.rememberCameraCaptureState
import com.photo.clarity.iq.Clarity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
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
                    var activeSlot by remember { mutableStateOf<PhotoSlot?>(null) }
                    val cameraState = rememberCameraCaptureState()
                    var showCamera by remember { mutableStateOf(false) }
                    var isCapturing by remember { mutableStateOf(false) }
                    var photoAUri by remember { mutableStateOf<Uri?>(null) }
                    var photoBUri by remember { mutableStateOf<Uri?>(null) }
                    var isComparing by remember { mutableStateOf(false) }
                    val coroutineScope = rememberCoroutineScope()
                    var pendingStorageSlot by remember { mutableStateOf<PhotoSlot?>(null) }
                    var pendingImportSlot by remember { mutableStateOf<PhotoSlot?>(null) }
                    var deniedCameraSlots by remember { mutableStateOf(setOf<PhotoSlot>()) }
                    fun hideCamera() {
                        showCamera = false
                        activeSlot = null
                    }
                    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                        if (granted) {
                            val slot = activeSlot
                            if (slot != null) {
                                deniedCameraSlots = deniedCameraSlots - slot
                                showCamera = true
                            }
                        } else {
                            Toast.makeText(context, context.getString(R.string.camera_permission_required), Toast.LENGTH_SHORT).show()
                            activeSlot?.let { slot ->
                                deniedCameraSlots = deniedCameraSlots + slot
                            }
                            activeSlot = null
                        }
                    }
                    fun openCamera(slot: PhotoSlot) {
                        activeSlot = slot
                        val status = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                        if (status == PackageManager.PERMISSION_GRANTED) {
                            deniedCameraSlots = deniedCameraSlots - slot
                            showCamera = true
                        } else {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                    val storagePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                        val slot = pendingStorageSlot
                        if (granted) {
                            if (slot != null) {
                                pendingStorageSlot = null
                                openCamera(slot)
                            }
                        } else {
                            Toast.makeText(context, context.getString(R.string.camera_permission_required), Toast.LENGTH_SHORT).show()
                            pendingStorageSlot = null
                            activeSlot = null
                        }
                    }
                    fun launchCamera(slot: PhotoSlot) {
                        if (isCapturing) {
                            return
                        }
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                            val storageStatus = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            if (storageStatus != PackageManager.PERMISSION_GRANTED) {
                                pendingStorageSlot = slot
                                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                return
                            }
                        }
                        openCamera(slot)
                    }
                    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
                        val slot = pendingImportSlot
                        pendingImportSlot = null
                        if (slot != null && uri != null) {
                            coroutineScope.launch {
                                val bitmap = try {
                                    loadBitmapFromUri(context, uri)
                                } catch (error: Throwable) {
                                    null
                                }
                                if (bitmap != null) {
                                    when (slot) {
                                        PhotoSlot.A -> {
                                            photoAUri = uri
                                            photoA = bitmap
                                        }
                                        PhotoSlot.B -> {
                                            photoBUri = uri
                                            photoB = bitmap
                                        }
                                    }
                                    clarityResult = null
                                } else {
                                    Toast.makeText(context, context.getString(R.string.import_failed), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    fun importPhoto(slot: PhotoSlot) {
                        pendingImportSlot = slot
                        importLauncher.launch("image/*")
                    }
                    suspend fun processCapture(slot: PhotoSlot, captured: com.photo.clarity.camera.CapturedImage) {
                        val bitmap = loadBitmapFromUri(context, captured.uri) ?: ensureSoftwareArgb8888(ensureSizeWithinLimit(captured.bitmap, MAX_BITMAP_DIMENSION))
                        when (slot) {
                            PhotoSlot.A -> {
                                photoAUri = captured.uri
                                photoA = bitmap
                            }
                            PhotoSlot.B -> {
                                photoBUri = captured.uri
                                photoB = bitmap
                            }
                        }
                        clarityResult = null
                    }
                    fun captureCurrentSlot() {
                        val slot = activeSlot ?: return
                        coroutineScope.launch {
                            isCapturing = true
                            try {
                                val captured = cameraState.captureBitmap() ?: throw IOException("capture_failed")
                                processCapture(slot, captured)
                                hideCamera()
                            } catch (error: Throwable) {
                                if (error is CancellationException) {
                                    throw error
                                }
                                Toast.makeText(context, context.getString(R.string.camera_capture_failed), Toast.LENGTH_SHORT).show()
                            } finally {
                                isCapturing = false
                            }
                        }
                    }
                    val canCompare = photoAUri != null && photoBUri != null
                    CompareScreen(
                        photoA = photoA,
                        photoB = photoB,
                        clarityResult = clarityResult,
                        isComparing = isComparing,
                        isCapturing = isCapturing,
                        canCompare = canCompare,
                        deniedCameraSlots = deniedCameraSlots,
                        onTakePhoto = { slot -> launchCamera(slot) },
                        onImportPhoto = { slot -> importPhoto(slot) },
                        onClearPhoto = { slot ->
                            when (slot) {
                                PhotoSlot.A -> photoA = null
                                PhotoSlot.B -> photoB = null
                            }
                            when (slot) {
                                PhotoSlot.A -> photoAUri = null
                                PhotoSlot.B -> photoBUri = null
                            }
                            clarityResult = null
                            if (deniedCameraSlots.contains(slot)) {
                                deniedCameraSlots = deniedCameraSlots - slot
                            }
                        },
                        onCompare = {
                            val first = photoA
                            val second = photoB
                            if (first != null && second != null && photoAUri != null && photoBUri != null && !isComparing && !isCapturing) {
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
                    if (showCamera) {
                        CameraCaptureDialog(
                            state = cameraState,
                            isCapturing = isCapturing,
                            onCapture = { captureCurrentSlot() },
                            onDismiss = { if (!isCapturing) hideCamera() }
                        )
                    }
                }
            }
        }
    }
}

private const val MAX_BITMAP_DIMENSION = 1024

private suspend fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(resolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val width = info.size.width
                    val height = info.size.height
                    val maxSide = max(width, height)
                    if (maxSide > MAX_BITMAP_DIMENSION) {
                        val scale = MAX_BITMAP_DIMENSION.toFloat() / maxSide.toFloat()
                        val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
                        val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
                        decoder.setTargetSize(targetWidth, targetHeight)
                    }
                }
            } else {
                val boundsOptions = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inJustDecodeBounds = true
                }
                resolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, boundsOptions)
                }
                val sampleSize = computeSampleSize(boundsOptions.outWidth, boundsOptions.outHeight, MAX_BITMAP_DIMENSION)
                val decodeOptions = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inSampleSize = sampleSize
                }
                val decoded = resolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOptions)
                }
                decoded?.let { ensureSizeWithinLimit(it, MAX_BITMAP_DIMENSION) }
            }
            bitmap?.let { ensureSoftwareArgb8888(it) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: OutOfMemoryError) {
            null
        } catch (error: Exception) {
            null
        }
    }
}

private fun computeSampleSize(width: Int, height: Int, maxDimension: Int): Int {
    if (width <= 0 || height <= 0) {
        return 1
    }
    var sampleSize = 1
    var currentMax = max(width, height)
    while (currentMax / sampleSize > maxDimension) {
        sampleSize *= 2
    }
    return sampleSize
}

private fun ensureSizeWithinLimit(bitmap: Bitmap, maxDimension: Int): Bitmap {
    val maxSide = max(bitmap.width, bitmap.height)
    if (maxSide <= maxDimension) {
        return bitmap
    }
    val scale = maxDimension.toFloat() / maxSide.toFloat()
    val targetWidth = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
    val targetHeight = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    if (scaled !== bitmap) {
        bitmap.recycle()
    }
    return scaled
}

private fun ensureSoftwareArgb8888(bitmap: Bitmap): Bitmap {
    val needsCopy = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) || bitmap.config != Bitmap.Config.ARGB_8888
    if (!needsCopy) {
        return bitmap
    }
    val converted = bitmap.copy(Bitmap.Config.ARGB_8888, false)
    return converted ?: bitmap
}

@Composable
private fun CameraCaptureDialog(
    state: CameraCaptureState,
    isCapturing: Boolean,
    onCapture: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = { if (!isCapturing) onDismiss() }) {
        Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 8.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(text = stringResource(id = R.string.camera_dialog_title), style = MaterialTheme.typography.titleMedium)
                Card(shape = RoundedCornerShape(18.dp), elevation = CardDefaults.cardElevation()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp)
                    ) {
                        CameraPreview(state = state, modifier = Modifier.fillMaxSize())
                        if (isCapturing) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isCapturing,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = stringResource(id = R.string.camera_cancel_button))
                    }
                    Button(
                        onClick = onCapture,
                        enabled = !isCapturing,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = stringResource(id = R.string.camera_capture_button))
                    }
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
    val (percentA, percentB) = Clarity.relativePercentages(scoreA, scoreB)
    return ClarityResult(percentA, percentB)
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
