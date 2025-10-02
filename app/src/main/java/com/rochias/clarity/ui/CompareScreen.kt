package com.rochias.clarity.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rochias.clarity.PhotoSlot
import com.rochias.clarity.camera.CameraPreview
import com.rochias.clarity.camera.CameraCaptureState
import com.rochias.clarity.iq.ClarityEvaluation
import com.rochias.clarity.iq.ClarityMethod
import android.graphics.BitmapFactory

@Composable
fun CompareScreen(
    cameraState: CameraCaptureState,
    firstPhotoUri: String?,
    secondPhotoUri: String?,
    firstClarity: ClarityEvaluation?,
    secondClarity: ClarityEvaluation?,
    isCapturing: Boolean,
    canCompare: Boolean,
    compareRequested: Boolean,
    errorMessage: String?,
    onCapture: (PhotoSlot) -> Unit,
    onClear: (PhotoSlot) -> Unit,
    onCompare: () -> Unit,
    onErrorDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CameraSection(cameraState = cameraState, isCapturing = isCapturing)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PhotoCard(
                modifier = Modifier.weight(1f),
                title = "Photo A",
                uriString = firstPhotoUri,
                contentDescription = "Première photo",
                onCapture = { onCapture(PhotoSlot.FIRST) },
                onClear = { onClear(PhotoSlot.FIRST) },
                isCapturing = isCapturing
            )
            PhotoCard(
                modifier = Modifier.weight(1f),
                title = "Photo B",
                uriString = secondPhotoUri,
                contentDescription = "Deuxième photo",
                onCapture = { onCapture(PhotoSlot.SECOND) },
                onClear = { onClear(PhotoSlot.SECOND) },
                isCapturing = isCapturing
            )
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = canCompare && !isCapturing,
            onClick = onCompare,
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            Text(text = "Comparer")
        }
        if (compareRequested && canCompare && firstClarity != null && secondClarity != null) {
            ResultSection(
                firstScores = firstClarity,
                secondScores = secondClarity,
                firstPhotoUri = firstPhotoUri,
                secondPhotoUri = secondPhotoUri
            )
        }
        ErrorMessage(errorMessage = errorMessage, onDismiss = onErrorDismiss)
    }
}

@Composable
private fun CameraSection(cameraState: CameraCaptureState, isCapturing: Boolean) {
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
            if (isCapturing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun PhotoCard(
    modifier: Modifier,
    title: String,
    uriString: String?,
    contentDescription: String,
    onCapture: () -> Unit,
    onClear: () -> Unit,
    isCapturing: Boolean
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            PhotoThumbnail(
                uriString = uriString,
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onCapture,
                enabled = !isCapturing,
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Text(text = "Prendre")
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onClear,
                enabled = uriString != null,
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Text(text = "Effacer")
            }
        }
    }
}

@Composable
private fun ResultSection(
    firstScores: ClarityEvaluation?,
    secondScores: ClarityEvaluation?,
    firstPhotoUri: String?,
    secondPhotoUri: String?
) {
    if (firstScores == null && secondScores == null) {
        Text(text = "Capturez deux images pour lancer la comparaison", style = MaterialTheme.typography.bodyMedium)
        return
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = "Résultat de la comparaison", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            val percentages = relativePercentage(firstScores, secondScores)
            Text(text = "Indice de netteté : ${percentages.first}% / ${percentages.second}%")
            firstScores?.let {
                Text(text = "Photo A : ${methodLabel(it.method)}")
            }
            secondScores?.let {
                Text(text = "Photo B : ${methodLabel(it.method)}")
            }
            if (firstPhotoUri != null || secondPhotoUri != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PhotoThumbnail(
                        uriString = firstPhotoUri,
                        contentDescription = "Aperçu photo A",
                        modifier = Modifier
                            .weight(1f)
                            .height(160.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    PhotoThumbnail(
                        uriString = secondPhotoUri,
                        contentDescription = "Aperçu photo B",
                        modifier = Modifier
                            .weight(1f)
                            .height(160.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoThumbnail(uriString: String?, contentDescription: String, modifier: Modifier = Modifier) {
    val bitmap = rememberBitmapFromUri(uriString)
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(text = "Aucune image", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ErrorMessage(error: String?, onDismiss: () -> Unit) {
    if (error.isNullOrBlank()) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            OutlinedButton(onClick = onDismiss) {
                Text(text = "Fermer")
            }
        }
    }
}

@Composable
private fun rememberBitmapFromUri(uriString: String?): Bitmap? {
    val context = LocalContext.current
    val state = produceState<Bitmap?>(initialValue = null, uriString) {
        value = uriString?.let {
            runCatching { loadBitmapFromUri(context, Uri.parse(it)) }.getOrNull()
        }
        awaitDispose {
            value?.recycle()
        }
    }
    return state.value
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
        ClarityMethod.LAPLACIAN -> "Laplacien"
        ClarityMethod.TENENGRAD -> "Tenengrad"
    }
}

private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    val resolver = context.contentResolver
    return resolver.openInputStream(uri)?.use { stream ->
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(stream, null, options)
        val maxDimension = 1024
        var sampleSize = 1
        while (
            options.outWidth / sampleSize > maxDimension ||
            options.outHeight / sampleSize > maxDimension
        ) {
            sampleSize *= 2
        }
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        resolver.openInputStream(uri)?.use { decodeStream ->
            BitmapFactory.decodeStream(decodeStream, null, decodeOptions)
        }
    }
}
