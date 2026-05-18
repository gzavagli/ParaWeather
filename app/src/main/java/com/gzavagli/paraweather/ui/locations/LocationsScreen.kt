package com.gzavagli.paraweather.ui.locations

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationsScreen(
    viewModel: LocationsViewModel = hiltViewModel()
) {
    val preferences by viewModel.userPreferences.collectAsState()
    val nearbyState by viewModel.nearbyState.collectAsState()
    val scrollState = rememberScrollState()

    var showMap by remember { mutableStateOf(false) }

    if (showMap) {
        InteractiveMapScreen(onDismiss = { showMap = false })
    } else {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text("Manage Flight Sites") })
            }
        ) { innerPadding ->
            preferences?.let { prefs ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(16.dp)
                        .verticalScroll(scrollState)
                ) {
                    // Saved Locations Header
                    Text(
                        text = "Saved Locations",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // List of Locations
                    prefs.savedLocations.forEach { loc ->
                        val isActive = prefs.activeLocationId == loc.id
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .clickable { viewModel.selectActiveLocation(loc.id) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isActive,
                                    onClick = { viewModel.selectActiveLocation(loc.id) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = if (loc.isCurrentGps) Icons.Default.GpsFixed else Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = if (isActive) MaterialTheme.colorScheme.primary else Color.Gray,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = loc.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal)
                                    )
                                    if (!loc.isCurrentGps) {
                                        Text(
                                            text = "${loc.latitude.coerceDecimals(4)}, ${loc.longitude.coerceDecimals(4)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                    }
                                }
                                if (!loc.isCurrentGps) {
                                    IconButton(onClick = { viewModel.deleteLocation(loc.id) }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = Color.Red.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Button to launch interactive map
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showMap = true }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Map,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Interactive Sites Map",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Browse topographic maps and pick launches",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Dynamic Site Weather Alerts Card
                    var alertsPanelExpanded by remember { mutableStateOf(false) }
                    val notificationPermissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) { _ -> }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { alertsPanelExpanded = !alertsPanelExpanded },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.NotificationsActive,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = "Manage Site Alerts", style = MaterialTheme.typography.titleSmall)
                                }
                                Text(
                                    text = if (alertsPanelExpanded) "Collapse" else "Expand",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            if (alertsPanelExpanded) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Scan sites automatically twice daily at 6 AM & 18 PM. Get alerted if a HIGH-soaring wind window is forecasted within your configured settings window.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                val alertableLocations = prefs.savedLocations.filter { !it.isCurrentGps }
                                if (alertableLocations.isEmpty()) {
                                    Text(
                                        text = "Please import or save at least one specific flight site to enable notifications.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.DarkGray,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                } else {
                                    alertableLocations.forEach { loc ->
                                        val isChecked = prefs.alertLocationIds.contains(loc.id)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    if (!isChecked && android.os.Build.VERSION.SDK_INT >= 33) {
                                                        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                                    }
                                                    viewModel.toggleLocationAlert(loc.id, !isChecked)
                                                }
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = isChecked,
                                                onCheckedChange = {
                                                    if (!isChecked && android.os.Build.VERSION.SDK_INT >= 33) {
                                                        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                                    }
                                                    viewModel.toggleLocationAlert(loc.id, !isChecked)
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(text = loc.name, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Nearby Paragliding Launches Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Explore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "Search Nearby Flight Sites (OSM)", style = MaterialTheme.typography.titleSmall)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Locate official paragliding launches & landings mapped on OpenStreetMap within a 100km radius.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            Button(
                                onClick = { viewModel.searchNearbyLaunches() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Find Launches Near Active Coordinates")
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Handles States
                            when (val state = nearbyState) {
                                is NearbyLaunchesState.Loading -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }

                                is NearbyLaunchesState.Success -> {
                                    if (state.launches.isEmpty()) {
                                        Text(
                                            text = "No paragliding sites found nearby.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.Gray,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    } else {
                                        Column {
                                            state.launches.forEach { launch ->
                                                val isAlreadySaved = prefs.savedLocations.any {
                                                    it.latitude.coerceDecimals(4) == launch.latitude.coerceDecimals(4) &&
                                                    it.longitude.coerceDecimals(4) == launch.longitude.coerceDecimals(4)
                                                }

                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Landscape,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.secondary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(text = launch.name, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                                                        Text(text = "${launch.latitude.coerceDecimals(4)}, ${launch.longitude.coerceDecimals(4)}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                                    }
                                                    if (isAlreadySaved) {
                                                        Icon(
                                                            imageVector = Icons.Default.CheckCircle,
                                                            contentDescription = "Saved",
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                    } else {
                                                        Button(
                                                            onClick = {
                                                                viewModel.addCustomLocation(launch.name, launch.latitude, launch.longitude)
                                                            },
                                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                            modifier = Modifier.height(28.dp)
                                                        ) {
                                                            Text("Import", fontSize = 10.sp)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                is NearbyLaunchesState.Error -> {
                                    Text(
                                        text = state.message,
                                        color = Color.Red,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }

                                is NearbyLaunchesState.Idle -> {}
                            }
                        }
                    }
                }
            } ?: Spacer(modifier = Modifier.fillMaxSize())
        }
    }
}

// Simple Helper to round doubles cleanly
private fun Double.coerceDecimals(decimals: Int): Double {
    var multiplier = 1.0
    repeat(decimals) { multiplier *= 10.0 }
    return kotlin.math.round(this * multiplier) / multiplier
}
