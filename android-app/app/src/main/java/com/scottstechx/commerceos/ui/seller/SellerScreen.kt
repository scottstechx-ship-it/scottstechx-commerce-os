package com.scottstechx.commerceos.ui.seller

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.scottstechx.commerceos.R
import com.scottstechx.commerceos.data.remote.dto.OrderResponse
import com.scottstechx.commerceos.data.remote.dto.ProductDto
import com.scottstechx.commerceos.ui.animation.AnimatedFadeInUp
import com.scottstechx.commerceos.ui.animation.AnimatedHeroEnter
import com.scottstechx.commerceos.ui.animation.staggerDelayMs
import com.scottstechx.commerceos.ui.brand.BrandLogo

private enum class SellerTab(val label: String) {
    Inventory("Inventory"),
    Orders("Orders")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SellerScreen(
    onSignedOut: () -> Unit,
    onOpenAssistant: () -> Unit = {},
    viewModel: SellerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var tab by remember { mutableStateOf(SellerTab.Inventory) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BrandLogo(size = 32.dp)
                        Spacer(Modifier.size(8.dp))
                        Text("Seller")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenAssistant) {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = "AI Assistant"
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
        },
        floatingActionButton = {
            if (tab == SellerTab.Inventory) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.showCreateDialog() },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("New product") }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Hero stats card.
            StatsCard(
                isLoading = state.isLoading && state.stats == null,
                stats = state.stats,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .animateContentSize()
            )

            TabRow(selectedTabIndex = tab.ordinal) {
                SellerTab.values().forEach { t ->
                    Tab(
                        selected = tab == t,
                        onClick = { tab = t },
                        text = { Text(t.label) }
                    )
                }
            }

            when (tab) {
                SellerTab.Inventory -> InventoryList(
                    isLoading = state.isLoading && state.products.isEmpty(),
                    products = state.products,
                    onEdit = viewModel::startEdit,
                    onDelete = viewModel::deleteProduct
                )
                SellerTab.Orders -> OrdersList(
                    isLoading = state.isLoading && state.orders.isEmpty(),
                    orders = state.orders
                )
            }
        }
    }

    if (state.showCreateDialog) {
        CreateProductDialog(
            isSaving = state.isSaving,
            onDismiss = viewModel::hideCreateDialog,
            onConfirm = viewModel::createProduct
        )
    }

    state.editingProduct?.let { product ->
        EditProductDialog(
            product = product,
            isSaving = state.isSaving,
            onDismiss = viewModel::cancelEdit,
            onConfirm = { title, desc, priceMinor, stock ->
                viewModel.updateProduct(product.id, title, desc, priceMinor, stock)
            }
        )
    }

    state.error?.let { msg ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text("Error") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = viewModel::clearError) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }
}

@Composable
private fun StatsCard(
    isLoading: Boolean,
    stats: com.scottstechx.commerceos.data.remote.dto.SellerStatsDto?,
    modifier: Modifier = Modifier
) {
    AnimatedHeroEnter {
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Storefront,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Today's snapshot",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (isLoading || stats == null) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatTile("Active", stats.activeListings.toString())
                        StatTile("Orders today", stats.ordersToday.toString())
                        StatTile("Revenue", "${stats.revenueMinorToday} ${stats.currency}")
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "${stats.averageRating} (${stats.ratingCount} ratings)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun InventoryList(
    isLoading: Boolean,
    products: List<ProductDto>,
    onEdit: (ProductDto) -> Unit,
    onDelete: (String) -> Unit
) {
    when {
        isLoading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        products.isEmpty() -> {
            Box(
                Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Inventory,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No products yet",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tap + New product to add your first item.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        else -> {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(products, key = { _, p -> p.id }) { index, p ->
                    AnimatedFadeInUp(delayMs = staggerDelayMs(index)) {
                        InventoryRow(
                            product = p,
                            onEdit = { onEdit(p) },
                            onDelete = { onDelete(p.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryRow(
    product: ProductDto,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    product.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${product.priceMinor} ${product.currency}  •  Stock ${product.stockQuantity}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit")
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun OrdersList(
    isLoading: Boolean,
    orders: List<OrderResponse>
) {
    when {
        isLoading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        orders.isEmpty() -> {
            Box(
                Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No orders yet")
            }
        }
        else -> {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(orders, key = { _, o -> o.orderId }) { index, o ->
                    AnimatedFadeInUp(delayMs = staggerDelayMs(index)) {
                        OrderRow(o)
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderRow(order: OrderResponse) {
    val statusColor = when (order.status.uppercase()) {
        "DELIVERED", "COMPLETED" -> MaterialTheme.colorScheme.primary
        "PENDING", "CREATED" -> MaterialTheme.colorScheme.secondary
        "CANCELLED", "FAILED" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Order ${order.orderId.take(8)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${order.totalMinor} ${order.currency}  •  ${order.items.size} items",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                color = statusColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    order.status.uppercase(),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun CreateProductDialog(
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (title: String, description: String, priceMinor: Long, stock: Int, imageUrl: String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var stockText by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }
    val parsedPrice = priceText.toLongOrNull()
    val parsedStock = stockText.toIntOrNull()
    val canSave = title.isNotBlank() && description.isNotBlank() &&
        parsedPrice != null && parsedPrice >= 0L &&
        parsedStock != null && parsedStock >= 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New product") },
        text = {
            Column {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("Description") },
                    minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = priceText, onValueChange = { priceText = it.filter { c -> c.isDigit() } },
                    label = { Text("Price (UGX)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = stockText, onValueChange = { stockText = it.filter { c -> c.isDigit() } },
                    label = { Text("Stock quantity") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = imageUrl, onValueChange = { imageUrl = it },
                    label = { Text("Image URL (optional)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = canSave && !isSaving,
                onClick = {
                    onConfirm(title, description, parsedPrice!!, parsedStock!!, imageUrl)
                }
            ) {
                if (isSaving) CircularProgressIndicator(
                    strokeWidth = 2.dp, modifier = Modifier.size(18.dp)
                ) else Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun EditProductDialog(
    product: ProductDto,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (title: String?, description: String?, priceMinor: Long?, stock: Int?) -> Unit
) {
    var title by remember { mutableStateOf(product.title) }
    var description by remember { mutableStateOf(product.description) }
    var priceText by remember { mutableStateOf(product.priceMinor.toString()) }
    var stockText by remember { mutableStateOf(product.stockQuantity.toString()) }
    val parsedPrice = priceText.toLongOrNull()
    val parsedStock = stockText.toIntOrNull()
    val canSave = title.isNotBlank() && description.isNotBlank() &&
        parsedPrice != null && parsedPrice >= 0L &&
        parsedStock != null && parsedStock >= 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit product") },
        text = {
            Column {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("Description") },
                    minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = priceText, onValueChange = { priceText = it.filter { c -> c.isDigit() } },
                    label = { Text("Price (UGX)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = stockText, onValueChange = { stockText = it.filter { c -> c.isDigit() } },
                    label = { Text("Stock quantity") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Text(
                    "Leave a field as-is to skip updating it.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                enabled = canSave && !isSaving,
                onClick = {
                    onConfirm(title, description, parsedPrice, parsedStock)
                }
            ) {
                if (isSaving) CircularProgressIndicator(
                    strokeWidth = 2.dp, modifier = Modifier.size(18.dp)
                ) else Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
