package com.rochias.clarity.ui

import android.graphics.Bitmap
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rochias.clarity.camera.CameraPreview
import com.rochias.clarity.camera.CameraCaptureState
import com.rochias.clarity.camera.rememberCameraCaptureState
import com.rochias.clarity.iq.Clarity
import kotlinx.coroutines.launch

@Composable
fun CompareScreen(modifier: Modifier = Modifier) {
    val cameraState = rememberCameraCaptureState()
    val scope = rememberCoroutineScope()
    var firstBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var secondBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var firstScores by remember { mutableStateOf<ClarityScores?>(null) }
    var secondScores by remember { mutableStateOf<ClarityScores?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
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
                scope.launch {
                    captureImage(cameraState, onResult = { bitmap ->
                        firstBitmap = bitmap
                        firstScores = bitmap?.let { evaluateClarity(it) }
                    }, onError = { message ->
                        errorMessage = message
                    }, onStateChange = { busy ->
                        isCapturing = busy
                    })
                }
            },
            onCaptureSecond = {
                scope.launch {
                    captureImage(cameraState, onResult = { bitmap ->
                        secondBitmap = bitmap
                        secondScores = bitmap?.let { evaluateClarity(it) }
                    }, onError = { message ->
                        errorMessage = message
                    }, onStateChange = { busy ->
                        isCapturing = busy
                    })
                }
            }
        )
        ResultSection(firstScores, secondScores, firstBitmap, secondBitmap)
        ErrorMessage(errorMessage)
        LaunchedEffect(firstBitmap, secondBitmap) {
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
    firstScores: ClarityScores?,
    secondScores: ClarityScores?,
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
            val laplacian = relativePercentage(firstScores?.laplacian, secondScores?.laplacian)
            val tenengrad = relativePercentage(firstScores?.tenengrad, secondScores?.tenengrad)
            Text(text = "Variance of Laplacian: ${laplacian.first}% vs ${laplacian.second}%")
            Text(text = "Tenengrad: ${tenengrad.first}% vs ${tenengrad.second}%")
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
    onResult: (Bitmap?) -> Unit,
    onError: (String) -> Unit,
    onStateChange: (Boolean) -> Unit
) {
    runCatching {
        onStateChange(true)
        val bitmap = cameraState.captureBitmap()
        if (bitmap != null) {
            onResult(bitmap)
        } else {
            onError("Unable to capture image data")
        }
    }.onFailure {
        onError(it.message ?: "Failed to capture image")
    }
    onStateChange(false)
}

private fun evaluateClarity(bitmap: Bitmap): ClarityScores {
    val laplacian = Clarity.varianceOfLaplacian(bitmap)
    val tenengrad = Clarity.tenengrad(bitmap)
    return ClarityScores(laplacian, tenengrad)
}

private fun relativePercentage(first: Double?, second: Double?): Pair<Int, Int> {
    val firstValue = first ?: 0.0
    val secondValue = second ?: 0.0
    val total = firstValue + secondValue
    if (total <= 0.0) return 50 to 50
    val firstPercent = ((firstValue / total) * 100).toInt()
    val secondPercent = 100 - firstPercent
    return firstPercent to secondPercent
}

data class ClarityScores(
    val laplacian: Double,
    val tenengrad: Double
)
