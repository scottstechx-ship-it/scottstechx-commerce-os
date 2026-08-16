package com.scottstechx.commerceos.ui.brand

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * Reusable brand-mark composable. Loads the company logo from
 * `assets/brand_logo.png` via Coil. Sized appropriately for a
 * top-app-bar start slot, a login splash, or an inline header.
 */
@Composable
fun BrandLogo(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    contentDescription: String = "ScottsTechX logo",
    colorFilter: ColorFilter? = null
) {
    val context = LocalContext.current
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data("file:///android_asset/brand_logo.png")
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            colorFilter = colorFilter
        )
    }
}

/**
 * Inline brand row: logo on the left, two-line wordmark on the
 * right. Used on the Login splash and inside the Buyer/Driver
 * top-app-bar titles.
 */
@Composable
fun BrandWordmark(
    modifier: Modifier = Modifier,
    logoSize: Dp = 36.dp,
    showLogo: Boolean = true
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showLogo) {
            BrandLogo(size = logoSize)
            androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        }
        androidx.compose.foundation.layout.Column {
            androidx.compose.material3.Text(
                "ScottsTechX",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            androidx.compose.material3.Text(
                "Commerce OS",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
