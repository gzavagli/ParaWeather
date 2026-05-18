package com.gzavagli.paraweather.ui.dashboard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gzavagli.paraweather.data.preferences.UserPreferences
import com.gzavagli.paraweather.domain.model.FlyabilityAssessment
import com.gzavagli.paraweather.domain.model.FlyabilityStatus
import com.gzavagli.paraweather.ui.theme.ExcellentGreen
import com.gzavagli.paraweather.ui.theme.FlyableGreen
import com.gzavagli.paraweather.ui.theme.MarginalOrange
import com.gzavagli.paraweather.ui.theme.UnflyableRed

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val prefs by viewModel.userPreferences.collectAsState(null)

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.loadWeatherData()
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is DashboardUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is DashboardUiState.Success -> {
                    DashboardContent(
                        locationName = state.locationName,
                        assessment = state.currentAssessment,
                        nextForecast = state.nextHoursForecast,
                        userPreferences = prefs,
                        onSelectLocation = { viewModel.selectActiveLocation(it) },
                        onRefresh = { viewModel.loadWeatherData() }
                    )
                }

                is DashboardUiState.Error -> {
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
                        Button(onClick = { viewModel.loadWeatherData() }) {
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
fun DashboardContent(
    locationName: String,
    assessment: FlyabilityAssessment,
    nextForecast: List<HourlyPreviewItem>,
    userPreferences: UserPreferences?,
    onSelectLocation: (String) -> Unit,
    onRefresh: () -> Unit
) {
    var isDropdownExpanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Location Dropdown Selector Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = "Location",
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box {
                Column(
                    modifier = Modifier.clickable { isDropdownExpanded = true }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = locationName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Switch Location",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "Paragliding Weather (Tap to switch)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }

                DropdownMenu(
                    expanded = isDropdownExpanded,
                    onDismissRequest = { isDropdownExpanded = false },
                    // Restricts maximum height of dropdown card on screen and handles vertical scrolling natively
                    modifier = Modifier.heightIn(max = 280.dp)
                ) {
                    userPreferences?.savedLocations?.forEachIndexed { index, loc ->
                        val isActive = loc.id == userPreferences.activeLocationId

                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = loc.name,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 14.sp
                                )
                            },
                            trailingIcon = {
                                if (isActive) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Active Location",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            onClick = {
                                onSelectLocation(loc.id)
                                isDropdownExpanded = false
                            }
                        )

                        // Draw clean, light dividers separating entries (except for the last row item)
                        if (index < userPreferences.savedLocations.size - 1) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                                thickness = 1.dp,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onRefresh) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Custom Wind Compass
        WindCompass(
            averageWind = assessment.averageWindSpeed,
            gustWind = assessment.gustSpeed,
            windDir = assessment.windDirection,
            modifier = Modifier.size(200.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Flyability Banner Card
        FlyabilityCard(assessment = assessment)

        Spacer(modifier = Modifier.height(24.dp))

        // Next 6 Hours Title
        Text(
            text = "Next Hours Forecast",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        )

        // Horizontal forecast preview
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(nextForecast) { item ->
                HourlyPreviewCard(item = item)
            }
        }
    }
}

@Composable
fun WindCompass(
    averageWind: Double,
    gustWind: Double,
    windDir: Double,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        val compassColor = MaterialTheme.colorScheme.onSurface
        val arrowColor = MaterialTheme.colorScheme.primary

        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.width / 2

            // Draw outer ring
            drawCircle(
                color = compassColor.copy(alpha = 0.3f),
                radius = radius,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
            )

            // Draw North, East, South, West labels
            val textPaint = android.graphics.Paint().apply {
                color = compassColor.hashCode()
                textSize = 12.sp.toPx()
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }

            drawContext.canvas.nativeCanvas.drawText("N", center.x, center.y - radius + 18.dp.toPx(), textPaint)
            drawContext.canvas.nativeCanvas.drawText("S", center.x, center.y + radius - 8.dp.toPx(), textPaint)
            drawContext.canvas.nativeCanvas.drawText("E", center.x + radius - 12.dp.toPx(), center.y + 4.dp.toPx(), textPaint)
            drawContext.canvas.nativeCanvas.drawText("W", center.x - radius + 12.dp.toPx(), center.y + 4.dp.toPx(), textPaint)

            // Draw Wind direction indicator arrow.
            rotate(degrees = (windDir + 180).toFloat(), pivot = center) {
                val arrowPath = Path().apply {
                    moveTo(center.x, center.y - radius + 24.dp.toPx()) // Arrow Tip
                    lineTo(center.x - 8.dp.toPx(), center.y - radius + 40.dp.toPx())
                    lineTo(center.x - 3.dp.toPx(), center.y - radius + 37.dp.toPx())
                    lineTo(center.x - 3.dp.toPx(), center.y - radius + 55.dp.toPx()) // Tail stem left
                    lineTo(center.x + 3.dp.toPx(), center.y - radius + 55.dp.toPx()) // Tail stem right
                    lineTo(center.x + 3.dp.toPx(), center.y - radius + 37.dp.toPx())
                    lineTo(center.x + 8.dp.toPx(), center.y - radius + 40.dp.toPx())
                    close()
                }
                drawPath(path = arrowPath, color = arrowColor)
            }
        }

        // Inner textual details
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${averageWind.toInt()}",
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 36.sp),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Gust ${gustWind.toInt()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = "${windDir.toInt()}°",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun FlyabilityCard(assessment: FlyabilityAssessment) {
    val (backgroundColor, textColor, bannerTitle) = when (assessment.status) {
        FlyabilityStatus.EXCELLENT -> Triple(ExcellentGreen, Color.White, "EXCELLENT CONDITIONS")
        FlyabilityStatus.IDEAL -> Triple(FlyableGreen, Color.White, "GOOD CONDITIONS")
        FlyabilityStatus.MARGINAL -> Triple(MarginalOrange, Color.Black, "MARGINAL CONDITIONS")
        FlyabilityStatus.UNFLYABLE -> Triple(UnflyableRed, Color.White, "DANGEROUS / UNFLYABLE")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = bannerTitle,
                style = MaterialTheme.typography.titleLarge,
                color = textColor
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (assessment.reasons.isEmpty()) {
                Text(
                    text = "Perfect wind, clear weather, no thunderstorm risk. Happy flights!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor.copy(alpha = 0.9f),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            } else {
                assessment.reasons.forEach { reason ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.titleMedium,
                            color = textColor,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Text(
                            text = reason,
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Thermal Soaring Quality indicators Card Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .background(Color.Black.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = assessment.thermalChance.symbol,
                    fontSize = 22.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Column {
                    Text(
                        text = "Thermal Soaring: ${assessment.thermalChance.label.uppercase()}",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = textColor
                    )
                    Text(
                        text = assessment.thermalChance.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
fun HourlyPreviewCard(item: HourlyPreviewItem) {
    val statusColor = when (item.assessment.status) {
        FlyabilityStatus.EXCELLENT -> ExcellentGreen
        FlyabilityStatus.IDEAL -> FlyableGreen
        FlyabilityStatus.MARGINAL -> MarginalOrange
        FlyabilityStatus.UNFLYABLE -> UnflyableRed
    }

    Card(
        modifier = Modifier
            .width(100.dp)
            .height(130.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = item.formattedTime, style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = item.assessment.thermalChance.symbol, fontSize = 11.sp)
            }

            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${item.windSpeed.toInt()}-${item.gustSpeed.toInt()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${item.temperature.toInt()}°C",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}
