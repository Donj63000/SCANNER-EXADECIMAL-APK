package com.photo.clarity.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.camera.view.PreviewView
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CameraCaptureState internal constructor(
    private val context: Context,
    val controller: LifecycleCameraController
) {
    suspend fun captureBitmap(): Bitmap? = suspendCancellableCoroutine { continuation ->
        controller.takePicture(
            ContextCompat.getMainExecutor(context),
            object : androidx.camera.core.ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    runCatching {
                        val rotation = image.imageInfo.rotationDegrees
                        val bitmap = image.toBitmap()?.let { it.rotate(rotation) }
                        image.close()
                        continuation.resume(bitmap)
                    }.onFailure {
                        image.close()
                        continuation.resume(null)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    continuation.resumeWithException(exception)
                }
            }
        )
    }
}

@Composable
fun rememberCameraCaptureState(): CameraCaptureState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember(context) { LifecycleCameraController(context) }
    DisposableEffect(lifecycleOwner) {
        controller.setEnabledUseCases(LifecycleCameraController.IMAGE_CAPTURE)
        controller.bindToLifecycle(lifecycleOwner)
        onDispose {
            controller.unbind()
        }
    }
    return remember(context, controller) { CameraCaptureState(context, controller) }
}

@Composable
fun CameraPreview(state: CameraCaptureState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier,
        factory = {
            PreviewView(context).apply {
                controller = state.controller
            }
        },
        update = {
            it.controller = state.controller
        }
    )
}

private fun ImageProxy.toBitmap(): Bitmap? {
    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer
    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()
    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)
    val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    return runCatching {
        ByteArrayOutputStream().use { stream ->
            yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, stream)
            val bytes = stream.toByteArray()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }.getOrNull()
}

private fun Bitmap.rotate(degrees: Int): Bitmap {
    if (degrees == 0) return this
    val matrix = Matrix()
    matrix.postRotate(degrees.toFloat())
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}
