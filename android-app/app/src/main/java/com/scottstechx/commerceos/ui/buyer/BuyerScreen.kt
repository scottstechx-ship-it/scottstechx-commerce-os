package com.scottstechx.commerceos.ui.buyer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.scottstechx.commerceos.R
import com.scottstechx.commerceos.data.remote.dto.ProductDto
import com.scottstechx.commerceos.ui.animation.AnimatedFadeInUp
import com.scottstechx.commerceos.ui.animation.staggerDelayMs
import com.scottstechx.commerceos.ui.brand.BrandLogo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuyerScreen(
    onSignedOut: () -> Unit,
    onOpenNearby: () -> Unit = {},
    onOpenChat: () -> Unit = {},
    viewModel: BuyerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BrandLogo(size = 32.dp)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.buyer_title))
                    }
                },
                actions = {
                    BadgedBox(
                        badge = {
                            if (state.cartCount > 0) {
                                Badge { Text(state.cartCount.toString()) }
                            }
                        }
                    ) {
                        IconButton(onClick = { viewModel.setCartVisible(true) }) {
                            Icon(Icons.Filled.ShoppingCart, contentDescription = "Cart")
                        }
                    }
                    IconButton(onClick = onOpenNearby) {
                        Icon(
                            Icons.Filled.MyLocation,
                            contentDescription = "Find sellers near me"
                        )
                    }
                    IconButton(onClick = onOpenChat) {
                        Icon(
                            Icons.Filled.SupportAgent,
                            contentDescription = "Shopping helper"
                        )
                    }
                    TextButton(onClick = {
                        viewModel.signOut()
                        onSignedOut()
                    }) {
                        Text(stringResource(R.string.buyer_logout))
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.isLoadingProducts && state.products.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.products.isEmpty() && state.productsError != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            state.productsError!!,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { viewModel.loadProducts() }) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
                state.products.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No products available")
                    }
                }
                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (state.productsError != null) {
                            // Soft banner: cache is showing, but refresh failed.
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        state.productsError!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { viewModel.clearProductsError() }) {
                                        Text(stringResource(R.string.ok))
                                    }
                                }
                            }
                        }
                        if (state.isShowingCached) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "Showing cached products",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(state.products, key = { _, p -> p.id }) { index, p ->
                                AnimatedFadeInUp(delayMs = staggerDelayMs(index)) {
                                    ProductCard(
                                        product = p,
                                        onAdd = { viewModel.addToCart(p) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (state.showCart) {
        CartDialog(
            state = state,
            onDismiss = { viewModel.setCartVisible(false) },
            onRemove = viewModel::removeFromCart,
            onCheckout = { line1, city -> viewModel.checkout(line1, city) }
        )
    }

    state.lastOrder?.let { order ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissOrder() },
            title = { Text(stringResource(R.string.buyer_checkout_success)) },
            text = {
                Column {
                    Text("Order ID: ${order.orderId}")
                    Text("Status: ${order.status}")
                    Text("Total: ${order.totalMinor} ${order.currency}")
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissOrder() }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    state.checkoutError?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.clearCheckoutError() },
            title = { Text(stringResource(R.string.buyer_checkout_failed)) },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearCheckoutError() }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }
}

@Composable
private fun ProductCard(product: ProductDto, onAdd: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Product image, or a placeholder if imageUrl is null.
            if (product.imageUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(product.imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = product.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Image,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(product.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    product.description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${product.priceMinor} ${product.currency}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Stock: ${product.stockQuantity}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = "Add to cart")
            }
        }
    }
}

@Composable
private fun CartDialog(
    state: BuyerUiState,
    onDismiss: () -> Unit,
    onRemove: (String) -> Unit,
    onCheckout: (String, String) -> Unit
) {
    var line1 by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.buyer_cart)) },
        text = {
            Column {
                if (state.cart.isEmpty()) {
                    Text(stringResource(R.string.buyer_empty_cart))
                } else {
                    LazyColumn(modifier = Modifier.height(220.dp)) {
                        items(state.cart, key = { it.product.id }) { line ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(line.product.title, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${line.qty} × ${line.product.priceMinor} = ${line.lineTotalMinor}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                IconButton(onClick = { onRemove(line.product.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Remove")
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Total: ${state.cartTotalMinor} UGX",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = line1,
                        onValueChange = { line1 = it },
                        label = { Text("Address line 1") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        label = { Text("City") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = state.cart.isNotEmpty() && !state.isCheckingOut,
                onClick = { onCheckout(line1, city) }
            ) {
                if (state.isCheckingOut) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.height(18.dp)
                    )
                } else {
                    Text(stringResource(R.string.buyer_checkout))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
