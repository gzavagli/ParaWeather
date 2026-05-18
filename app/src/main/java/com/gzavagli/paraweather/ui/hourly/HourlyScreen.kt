package com.gzavagli.paraweather.ui.hourly

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gzavagli.paraweather.domain.model.FlyabilityStatus
import com.gzavagli.paraweather.ui.theme.ExcellentGreen
import com.gzavagli.paraweather.ui.theme.FlyableGreen
import com.gzavagli.paraweather.ui.theme.MarginalOrange
import com.gzavagli.paraweather.ui.theme.UnflyableRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HourlyScreen(
    viewModel: HourlyViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadHourlyForecast()
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("7-Day Flyability Forecast") })
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is HourlyUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is HourlyUiState.Success -> {
                    HourlyList(
                        locationName = state.locationName,
                        items = state.hourlyItems,
                        onRefresh = { viewModel.loadHourlyForecast() }
                    )
                }

                is HourlyUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = state.message, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadHourlyForecast() }) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Retry")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HourlyList(
    locationName: String,
    items: List<DetailedHourlyItem>,
    onRefresh: () -> Unit
) {
    val expandedStates = remember { mutableStateMapOf<Int, Boolean>() }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Location: $locationName",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
        ) {
            itemsIndexed(items) { index, item ->
                if (index == 0 || items[index - 1].dayOfWeek != item.dayOfWeek) {
                    Text(
                        text = item.dayOfWeek,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 4.dp)
                    )
                }

                val isExpanded = expandedStates[index] ?: false
                HourlyItemRow(
                    item = item,
                    isExpanded = isExpanded,
                    onToggleExpand = { expandedStates[index] = !isExpanded }
                )
            }
        }
    }
}

@Composable
fun HourlyItemRow(
    item: DetailedHourlyItem,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val statusColor = when (item.assessment.status) {
        FlyabilityStatus.EXCELLENT -> ExcellentGreen
        FlyabilityStatus.IDEAL -> FlyableGreen
        FlyabilityStatus.MARGINAL -> MarginalOrange
        FlyabilityStatus.UNFLYABLE -> UnflyableRed
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onToggleExpand() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // 1. Time
                Text(
                    text = item.timeLabel,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.width(55.dp)
                )

                // 2. Status Dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // 3. Core Wind Column (Width 95.dp)
                Column(modifier = Modifier.width(95.dp)) {
                    Text(
                        text = "Wind: ${item.windSpeed.toInt()} ${getCompassDirection(item.windDirection)}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "Gusts: ${item.gustSpeed.toInt()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                // 4. Temp & Rain Column (Weight 1f)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${item.temperature.toInt()}°C",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Rain: ${item.precipitationProb}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.precipitationProb > 30) Color.Red else Color.Gray
                    )
                }

                // 5. Thermal Lift Column (Width 60.dp)
                Column(
                    modifier = Modifier.width(60.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = item.assessment.thermalChance.symbol,
                        fontSize = 16.sp
                    )
                    Text(
                        text = item.assessment.thermalChance.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // 6. Chevron (Always visible so they can expand to check boundaries)
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Expand details",
                    tint = Color.Gray
                )
            }

            // Expanded view containing safety alerts + core convective parameters
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, start = 8.dp)
                ) {
                    // Safety Alerts Section (only show if reasons exist)
                    if (item.assessment.reasons.isNotEmpty()) {
                        Text(
                            text = "Safety Warnings:",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                            color = Color.Gray,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        item.assessment.reasons.forEach { reason ->
                            Text(
                                text = "• $reason",
                                style = MaterialTheme.typography.bodySmall,
                                color = statusColor,
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Convective Thermal Parameters Section
                    Text(
                        text = "Convective Soaring Details:",
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = "• Thermal Lift Quality: ${item.assessment.thermalChance.label} (${item.assessment.thermalChance.symbol})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                    Text(
                        text = "• Convective Ceiling Height: ${item.assessment.boundaryLayerHeight.toInt()} m (approx ${(item.assessment.boundaryLayerHeight * 3.28084).toInt()} ft)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.DarkGray,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                    Text(
                        text = "• Solar Radiation Power: ${item.shortwaveRadiation.toInt()} W/m²",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.DarkGray,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                    if (item.cape > 0.0) {
                        Text(
                            text = "• Atmospheric CAPE Energy: ${item.cape.toInt()} J/kg",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.DarkGray,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

fun getCompassDirection(degrees: Double): String {
    val directions = listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
    val index = (((degrees % 360) + 11.25) / 22.5).toInt() % 16
    return directions[index]
}
