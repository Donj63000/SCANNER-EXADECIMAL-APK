package com.rochias.clarity

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.rochias.clarity.camera.rememberCameraCaptureState
import com.rochias.clarity.iq.Clarity
import com.rochias.clarity.iq.ClarityEvaluation
import com.rochias.clarity.iq.ClarityMethod
import com.rochias.clarity.processing.extractLuminance
import com.rochias.clarity.ui.CompareScreen
import java.io.File
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CameraPermissionScreen()
                }
            }
        }
    }
}

@Composable
private fun CameraPermissionScreen() {
    val context = LocalContext.current
    val permission = Manifest.permission.CAMERA
    var hasPermission by rememberSaveable {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
    }
    LaunchedEffect(hasPermission) {
        if (!hasPermission) {
            launcher.launch(permission)
        }
    }
    if (hasPermission) {
        ComparisonHost()
    } else {
        PermissionRequest(onRequest = {
            launcher.launch(permission)
        })
    }
}

@Composable
private fun ComparisonHost() {
    val context = LocalContext.current
    val cameraState = rememberCameraCaptureState()
    val scope = rememberCoroutineScope()
    val storagePermission = Manifest.permission.WRITE_EXTERNAL_STORAGE
    val requiresStoragePermission = remember { Build.VERSION.SDK_INT < Build.VERSION_CODES.Q }
    var hasStoragePermission by rememberSaveable {
        mutableStateOf(
            !requiresStoragePermission || ContextCompat.checkSelfPermission(
                context,
                storagePermission
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var firstPhotoUri by rememberSaveable { mutableStateOf<String?>(null) }
    var secondPhotoUri by rememberSaveable { mutableStateOf<String?>(null) }
    var firstClarity by rememberSaveable(stateSaver = clarityEvaluationSaver) {
        mutableStateOf<ClarityEvaluation?>(null)
    }
    var secondClarity by rememberSaveable(stateSaver = clarityEvaluationSaver) {
        mutableStateOf<ClarityEvaluation?>(null)
    }
    var activeSlot by rememberSaveable { mutableStateOf<PhotoSlot?>(null) }
    var compareRequested by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val storagePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasStoragePermission = granted
        if (!granted) {
            errorMessage = "Autorisation de stockage requise pour enregistrer les images"
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activeSlot = null
        }
    }

    fun clearPhoto(slot: PhotoSlot) {
        val uriToClear = when (slot) {
            PhotoSlot.FIRST -> firstPhotoUri
            PhotoSlot.SECOND -> secondPhotoUri
        }
        val deletionFailed = uriToClear != null && !deletePersistedUri(context, uriToClear)
        when (slot) {
            PhotoSlot.FIRST -> {
                firstPhotoUri = null
                firstClarity = null
            }
            PhotoSlot.SECOND -> {
                secondPhotoUri = null
                secondClarity = null
            }
        }
        compareRequested = false
        if (deletionFailed) {
            errorMessage = "Impossible de supprimer l'image sélectionnée"
        }
    }

    val canCompare = firstPhotoUri != null && secondPhotoUri != null && firstClarity != null && secondClarity != null

    fun handleCapture(slot: PhotoSlot) {
        if (activeSlot != null) return
        if (requiresStoragePermission && !hasStoragePermission) {
            storagePermissionLauncher.launch(storagePermission)
            return
        }
        errorMessage = null
        scope.launch {
            activeSlot = slot
            runCatching {
                cameraState.captureBitmap()
            }.onSuccess { capture ->
                if (capture == null) {
                    errorMessage = "Impossible de capturer l'image"
                } else {
                    val evaluation = evaluateClarity(capture.bitmap)
                    when (slot) {
                        PhotoSlot.FIRST -> {
                            firstPhotoUri = capture.uri.toString()
                            firstClarity = evaluation
                        }
                        PhotoSlot.SECOND -> {
                            secondPhotoUri = capture.uri.toString()
                            secondClarity = evaluation
                        }
                    }
                    compareRequested = false
                    capture.bitmap.recycle()
                }
            }.onFailure {
                errorMessage = it.message ?: "Échec de la capture de l'image"
            }
            activeSlot = null
        }
    }

    CompareScreen(
        cameraState = cameraState,
        firstPhotoUri = firstPhotoUri,
        secondPhotoUri = secondPhotoUri,
        firstClarity = firstClarity,
        secondClarity = secondClarity,
        isCapturing = activeSlot != null,
        canCompare = canCompare,
        compareRequested = compareRequested,
        errorMessage = errorMessage,
        onCapture = { handleCapture(it) },
        onClear = { clearPhoto(it) },
        onCompare = {
            if (canCompare) {
                compareRequested = true
            }
        },
        onErrorDismiss = { errorMessage = null }
    )
}

@Composable
private fun PermissionRequest(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "La permission caméra est nécessaire pour capturer des images", style = MaterialTheme.typography.titleMedium)
        Button(onClick = onRequest) {
            Text(text = "Autoriser")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PermissionRequestPreview() {
    MaterialTheme {
        PermissionRequest(onRequest = {})
    }
}

private val clarityEvaluationSaver = Saver<ClarityEvaluation?, List<Any?>>(
    save = { value ->
        value?.let { listOf(it.score, it.method.name, it.laplacianScore, it.tenengradScore) }
    },
    restore = { value ->
        value?.let {
            ClarityEvaluation(
                score = it[0] as Double,
                method = ClarityMethod.valueOf(it[1] as String),
                laplacianScore = it[2] as Double,
                tenengradScore = it[3] as Double?
            )
        }
    }
)

private fun evaluateClarity(bitmap: Bitmap): ClarityEvaluation {
    val image = extractLuminance(bitmap)
    return Clarity.clarityScore(image.width, image.height, image.luminance)
}

private fun deletePersistedUri(context: Context, uriString: String): Boolean {
    return runCatching {
        val uri = Uri.parse(uriString)
        when (uri.scheme) {
            ContentResolver.SCHEME_CONTENT -> context.contentResolver.delete(uri, null, null) > 0
            ContentResolver.SCHEME_FILE -> uri.path?.let { File(it).delete() } ?: false
            else -> context.contentResolver.delete(uri, null, null) > 0
        }
    }.getOrElse { false }
}
