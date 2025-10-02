package com.rochias.clarity.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.rochias.clarity.camera.CameraPreview
import com.rochias.clarity.camera.CameraCaptureState
import com.rochias.clarity.camera.rememberCameraCaptureState
import com.rochias.clarity.iq.Clarity
import com.rochias.clarity.iq.ClarityEvaluation
import com.rochias.clarity.iq.ClarityMethod
import com.rochias.clarity.processing.extractLuminance
import kotlinx.coroutines.launch

@Composable
fun CompareScreen(modifier: Modifier = Modifier) {
    val cameraState = rememberCameraCaptureState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val storagePermission = Manifest.permission.WRITE_EXTERNAL_STORAGE
    val requiresStoragePermission = remember { Build.VERSION.SDK_INT < Build.VERSION_CODES.Q }
    var hasStoragePermission by remember {
        mutableStateOf(
            !requiresStoragePermission || ContextCompat.checkSelfPermission(context, storagePermission) == PackageManager.PERMISSION_GRANTED
        )
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val storagePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasStoragePermission = granted
        if (!granted) {
            errorMessage = "Storage permission is required to save images"
        }
    }
    var firstBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var secondBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var firstImageUri by remember { mutableStateOf<Uri?>(null) }
    var secondImageUri by remember { mutableStateOf<Uri?>(null) }
    var firstClarity by remember { mutableStateOf<ClarityEvaluation?>(null) }
    var secondClarity by remember { mutableStateOf<ClarityEvaluation?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CameraSection(cameraState = cameraState)
        CaptureControls(
            isCapturing = isCapturing,
            onCaptureFirst = {
                if (requiresStoragePermission && !hasStoragePermission) {
                    storagePermissionLauncher.launch(storagePermission)
                } else {
                    scope.launch {
                        captureImage(cameraState, onResult = { uri, bitmap ->
                            firstImageUri = uri
                            firstBitmap = bitmap
                            firstClarity = evaluateClarity(bitmap)
                        }, onError = { message ->
                            errorMessage = message
                        }, onStateChange = { busy ->
                            isCapturing = busy
                        })
                    }
                }
            },
            onCaptureSecond = {
                if (requiresStoragePermission && !hasStoragePermission) {
                    storagePermissionLauncher.launch(storagePermission)
                } else {
                    scope.launch {
                        captureImage(cameraState, onResult = { uri, bitmap ->
                            secondImageUri = uri
                            secondBitmap = bitmap
                            secondClarity = evaluateClarity(bitmap)
                        }, onError = { message ->
                            errorMessage = message
                        }, onStateChange = { busy ->
                            isCapturing = busy
                        })
                    }
                }
            }
        )
        ResultSection(firstClarity, secondClarity, firstBitmap, secondBitmap)
        ErrorMessage(errorMessage)
        LaunchedEffect(firstBitmap, secondBitmap, firstImageUri, secondImageUri) {
            if (errorMessage != null) {
                errorMessage = null
            }
        }
    }
}

@Composable
private fun CameraSection(cameraState: CameraCaptureState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
        ) {
            CameraPreview(state = cameraState, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun CaptureControls(
    isCapturing: Boolean,
    onCaptureFirst: () -> Unit,
    onCaptureSecond: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            modifier = Modifier.weight(1f),
            enabled = !isCapturing,
            onClick = onCaptureFirst,
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            Text(text = "Capture First")
        }
        Button(
            modifier = Modifier.weight(1f),
            enabled = !isCapturing,
            onClick = onCaptureSecond,
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            Text(text = "Capture Second")
        }
    }
    if (isCapturing) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun ResultSection(
    firstScores: ClarityEvaluation?,
    secondScores: ClarityEvaluation?,
    firstBitmap: Bitmap?,
    secondBitmap: Bitmap?
) {
    if (firstScores == null && secondScores == null) {
        Text(text = "Capture images to compare clarity", style = MaterialTheme.typography.bodyMedium)
        return
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = "Clarity Results", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            val percentages = relativePercentage(firstScores, secondScores)
            Text(text = "Clarity index: ${percentages.first}% vs ${percentages.second}%")
            firstScores?.let {
                Text(text = "First image method: ${methodLabel(it.method)}")
            }
            secondScores?.let {
                Text(text = "Second image method: ${methodLabel(it.method)}")
            }
            if (firstBitmap != null || secondBitmap != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    firstBitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "First capture",
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .height(160.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                    secondBitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "Second capture",
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .height(160.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorMessage(error: String?) {
    if (error.isNullOrBlank()) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Text(
            text = error,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

private suspend fun captureImage(
    cameraState: CameraCaptureState,
    onResult: (Uri, Bitmap) -> Unit,
    onError: (String) -> Unit,
    onStateChange: (Boolean) -> Unit
) {
    try {
        onStateChange(true)
        val capture = cameraState.captureBitmap()
        if (capture != null) {
            onResult(capture.uri, capture.bitmap)
        } else {
            onError("Unable to capture image data")
        }
    } catch (error: Throwable) {
        onError(error.message ?: "Failed to capture image")
    } finally {
        onStateChange(false)
    }
}

private fun evaluateClarity(bitmap: Bitmap): ClarityEvaluation {
    val image = extractLuminance(bitmap)
    return Clarity.clarityScore(image.width, image.height, image.luminance)
}

private fun relativePercentage(first: ClarityEvaluation?, second: ClarityEvaluation?): Pair<Int, Int> {
    val firstValue = first?.score ?: 0.0
    val secondValue = second?.score ?: 0.0
    val total = firstValue + secondValue
    if (total <= 0.0) return 50 to 50
    val firstPercent = ((firstValue / total) * 100).toInt()
    val secondPercent = 100 - firstPercent
    return firstPercent to secondPercent
}

private fun methodLabel(method: ClarityMethod): String {
    return when (method) {
        ClarityMethod.LAPLACIAN -> "Laplacian"
        ClarityMethod.TENENGRAD -> "Tenengrad"
    }
}
