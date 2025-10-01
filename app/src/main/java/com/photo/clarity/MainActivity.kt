package com.photo.clarity

import android.Manifest
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.photo.clarity.ui.CompareScreen

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
    var hasPermission by remember {
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
        CompareScreen()
    } else {
        PermissionRequest(onRequest = {
            launcher.launch(permission)
        })
    }
}

@Composable
private fun PermissionRequest(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Camera permission is required to capture images", style = MaterialTheme.typography.titleMedium)
        Button(onClick = onRequest) {
            Text(text = "Grant Permission")
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
