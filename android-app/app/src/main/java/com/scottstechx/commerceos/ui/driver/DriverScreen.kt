package com.scottstechx.commerceos.ui.driver

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.shouldShowRationale
import com.scottstechx.commerceos.R
import com.scottstechx.commerceos.data.remote.dto.OrderResponse
import com.scottstechx.commerceos.ui.brand.BrandLogo

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun DriverScreen(
    onSignedOut: () -> Unit,
    viewModel: DriverViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BrandLogo(size = 32.dp)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.driver_title))
                    }
                },
                actions = {
                    TextButton(onClick = {
                        viewModel.signOut()
                        onSignedOut()
                    }) {
                        Text(stringResource(R.string.driver_logout))
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
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.loadError != null -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(state.loadError!!, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { viewModel.loadAssigned() }) {
                        Text(stringResource(R.string.retry))
                    }
                }
                state.assigned.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No orders assigned to you right now.")
                }
                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.assigned, key = { it.orderId }) { order ->
                        AssignedOrderCard(
                            order = order,
                            isFetchingLocation = state.isFetchingLocation,
                            isSubmitting = state.isSubmitting,
                            onPickup = { lat, lng, notes, photo ->
                                viewModel.submitPod(order.orderId, PodAction.PICKUP, lat, lng, notes, photo)
                            },
                            onDeliver = { lat, lng, notes, photo ->
                                viewModel.submitPod(order.orderId, PodAction.DELIVER, lat, lng, notes, photo)
                            },
                            onRequestLocation = { onResolved ->
                                viewModel.fetchLocationForOrder(order.orderId, onResolved)
                            },
                            onStartPhotoCapture = { orderId -> viewModel.startPhotoCapture(orderId) },
                            pendingCaptureOrderId = state.pendingCaptureOrderId
                        )
                    }
                }
            }
        }
    }

    state.lastSubmittedOrderId?.let {
        AlertDialog(
            onDismissRequest = { viewModel.consumeLastSubmitted() },
            title = { Text(stringResource(R.string.driver_pod_success)) },
            text = { Text("Order $it updated.") },
            confirmButton = {
                TextButton(onClick = { viewModel.consumeLastSubmitted() }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    state.podError?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.clearPodError() },
            title = { Text(stringResource(R.string.driver_pod_failed)) },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearPodError() }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun AssignedOrderCard(
    order: OrderResponse,
    isFetchingLocation: Boolean,
    isSubmitting: Boolean,
    onPickup: (Double, Double, String?, Uri?) -> Unit,
    onDeliver: (Double, Double, String?, Uri?) -> Unit,
    onRequestLocation: ((Double, Double) -> Unit) -> Unit,
    onStartPhotoCapture: (String) -> Uri,
    pendingCaptureOrderId: String?
) {
    var lat by remember { mutableStateOf("") }
    var lng by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var capturedUri by remember { mutableStateOf<Uri?>(null) }

    // Runtime permission state for location + camera.
    val perms = rememberMultiplePermissionsState(
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.CAMERA
        )
    )

    val context = LocalContext.current
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (!success) capturedUri = null
    }

    // If a capture is pending for THIS order, fire the camera intent
    // once permissions are granted.
    LaunchedEffect(pendingCaptureOrderId) {
        if (pendingCaptureOrderId == order.orderId) {
            if (perms.permissions.first { it.permission == Manifest.permission.CAMERA }.status.isGranted) {
                val uri = onStartPhotoCapture(order.orderId)
                capturedUri = uri
                cameraLauncher.launch(uri)
            } else {
                perms.launchMultiplePermissionRequest()
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(order.orderId, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Status: ${order.status}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Total: ${order.totalMinor} ${order.currency}",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            Text(stringResource(R.string.driver_gps_label), style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = lat,
                    onValueChange = { lat = it },
                    label = { Text("Lat") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = lng,
                    onValueChange = { lng = it },
                    label = { Text("Lng") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButtonWithProgress(
                    isLoading = isFetchingLocation,
                    onClick = {
                        if (perms.permissions.first { it.permission == Manifest.permission.ACCESS_FINE_LOCATION }.status.isGranted) {
                            onRequestLocation { newLat, newLng ->
                                lat = newLat.toString()
                                lng = newLng.toString()
                            }
                        } else {
                            perms.launchMultiplePermissionRequest()
                        }
                    }
                )
            }
            if (perms.permissions.first { it.permission == Manifest.permission.ACCESS_FINE_LOCATION }.status.shouldShowRationale) {
                Text(
                    stringResource(R.string.driver_location_grant),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.driver_notes_label)) },
                singleLine = false,
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            // Photo capture row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        if (perms.permissions.first { it.permission == Manifest.permission.CAMERA }.status.isGranted) {
                            val uri = onStartPhotoCapture(order.orderId)
                            capturedUri = uri
                            cameraLauncher.launch(uri)
                        } else {
                            perms.launchMultiplePermissionRequest()
                        }
                    }
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null)
                    Spacer(Modifier.padding(4.dp))
                    Text(
                        if (capturedUri != null) stringResource(R.string.driver_recapture_photo)
                        else stringResource(R.string.driver_capture_photo)
                    )
                }
                Spacer(Modifier.padding(8.dp))
                Text(
                    if (capturedUri != null) stringResource(R.string.driver_photo_captured)
                    else stringResource(R.string.driver_no_photo_yet),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (capturedUri != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    enabled = !isSubmitting,
                    onClick = {
                        onPickup(
                            lat.toDoubleOrNull() ?: 0.0,
                            lng.toDoubleOrNull() ?: 0.0,
                            notes,
                            capturedUri
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.driver_action_pickup)) }
                Button(
                    enabled = !isSubmitting,
                    onClick = {
                        onDeliver(
                            lat.toDoubleOrNull() ?: 0.0,
                            lng.toDoubleOrNull() ?: 0.0,
                            notes,
                            capturedUri
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.driver_action_deliver)) }
            }
        }
    }
}

@Composable
private fun IconButtonWithProgress(
    isLoading: Boolean,
    onClick: () -> Unit
) {
    if (isLoading) {
        CircularProgressIndicator(
            strokeWidth = 2.dp,
            modifier = Modifier
                .height(40.dp)
                .padding(8.dp)
        )
    } else {
        OutlinedButton(onClick = onClick) {
            Icon(Icons.Filled.MyLocation, contentDescription = stringResource(R.string.driver_use_location))
        }
    }
}
