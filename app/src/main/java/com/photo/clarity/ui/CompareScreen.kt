package com.photo.clarity.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import com.photo.clarity.ClarityResult
import com.photo.clarity.PhotoSlot
import com.photo.clarity.R

@Composable
fun CompareScreen(
    photoA: Bitmap?,
    photoB: Bitmap?,
    clarityResult: ClarityResult?,
    isComparing: Boolean,
    onTakePhoto: (PhotoSlot) -> Unit,
    onClearPhoto: (PhotoSlot) -> Unit,
    onCompare: () -> Unit
) {
    val badgeA = clarityResult?.let {
        stringResource(
            id = R.string.badge_photo_percentage,
            stringResource(R.string.photo_short_label_a),
            it.percentA
        )
    }
    val badgeB = clarityResult?.let {
        stringResource(
            id = R.string.badge_photo_percentage,
            stringResource(R.string.photo_short_label_b),
            it.percentB
        )
    }
    val resultText = clarityResult?.let {
        when (it.leadingSlot) {
            PhotoSlot.A -> stringResource(R.string.result_a_sharper, it.percentA, it.percentB)
            PhotoSlot.B -> stringResource(R.string.result_b_sharper, it.percentB, it.percentA)
            null -> stringResource(R.string.result_equal, it.percentA)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = stringResource(R.string.screen_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PhotoCard(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.photo_label_a),
                slot = PhotoSlot.A,
                bitmap = photoA,
                badgeText = badgeA,
                onTakePhoto = onTakePhoto,
                onClearPhoto = onClearPhoto
            )
            PhotoCard(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.photo_label_b),
                slot = PhotoSlot.B,
                bitmap = photoB,
                badgeText = badgeB,
                onTakePhoto = onTakePhoto,
                onClearPhoto = onClearPhoto
            )
        }
        Button(
            onClick = onCompare,
            enabled = photoA != null && photoB != null && !isComparing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (isComparing) {
                    stringResource(R.string.compare_in_progress)
                } else {
                    stringResource(R.string.compare_button)
                }
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.result_title),
                style = MaterialTheme.typography.titleMedium
            )
            if (isComparing) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            } else if (resultText != null) {
                Text(
                    text = resultText,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
private fun PhotoCard(
    modifier: Modifier,
    label: String,
    slot: PhotoSlot,
    bitmap: Bitmap?,
    badgeText: String?,
    onTakePhoto: (PhotoSlot) -> Unit,
    onClearPhoto: (PhotoSlot) -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline, shape),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.photo_preview_content_description, label),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = stringResource(R.string.placeholder_no_image),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { onTakePhoto(slot) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.take_button))
                }
                OutlinedButton(
                    onClick = { onClearPhoto(slot) },
                    modifier = Modifier.weight(1f),
                    enabled = bitmap != null
                ) {
                    Text(text = stringResource(R.string.clear_button))
                }
            }
            if (badgeText != null) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Text(
                        text = badgeText,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
