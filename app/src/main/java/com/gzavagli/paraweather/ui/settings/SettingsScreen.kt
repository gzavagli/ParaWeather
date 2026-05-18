package com.gzavagli.paraweather.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gzavagli.paraweather.data.preferences.TakeoffDirection
import com.gzavagli.paraweather.data.preferences.WindUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val preferences by viewModel.userPreferences.collectAsState()
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Paragliding Settings") })
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
                Text(
                    text = "Configure Flyability Thresholds",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Min Wind Speed Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "Min Flyable Wind Speed", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "${prefs.minWindSpeed.toInt()} ${prefs.windUnit.label}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Slider(
                            value = prefs.minWindSpeed.toFloat(),
                            onValueChange = { viewModel.updateMinWindSpeed(it.toDouble()) },
                            valueRange = 0f..30f,
                            steps = 30
                        )
                    }
                }

                // Max Wind Speed Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "Max Flyable Wind Speed", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "${prefs.maxWindSpeed.toInt()} ${prefs.windUnit.label}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Slider(
                            value = prefs.maxWindSpeed.toFloat(),
                            onValueChange = { viewModel.updateMaxWindSpeed(it.toDouble()) },
                            valueRange = 10f..45f,
                            steps = 35
                        )
                    }
                }

                // Max Gust Speed Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "Max Safe Wind Gust", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "${prefs.maxGustSpeed.toInt()} ${prefs.windUnit.label}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Slider(
                            value = prefs.maxGustSpeed.toFloat(),
                            onValueChange = { viewModel.updateMaxGustSpeed(it.toDouble()) },
                            valueRange = 10f..60f,
                            steps = 50
                        )
                    }
                }

                Text(
                    text = "Preferences & Orientation",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)
                )

                // Units Dropdown
                var unitExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = unitExpanded,
                    onExpandedChange = { unitExpanded = !unitExpanded },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    OutlinedTextField(
                        value = prefs.windUnit.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Wind Speed Unit") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                    )
                    ExposedDropdownMenu(
                        expanded = unitExpanded,
                        onDismissRequest = { unitExpanded = false }
                    ) {
                        WindUnit.values().forEach { unit ->
                            DropdownMenuItem(
                                text = { Text(unit.label) },
                                onClick = {
                                    viewModel.updateWindUnit(unit)
                                    unitExpanded = false
                                }
                            )
                        }
                    }
                }

                // Takeoff Orientation Dropdown
                var takeoffExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = takeoffExpanded,
                    onExpandedChange = { takeoffExpanded = !takeoffExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = prefs.takeoffDirection.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Takeoff Launch Direction") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = takeoffExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                    )
                    ExposedDropdownMenu(
                        expanded = takeoffExpanded,
                        onDismissRequest = { takeoffExpanded = false }
                    ) {
                        TakeoffDirection.values().forEach { dir ->
                            DropdownMenuItem(
                                text = { Text(dir.label) },
                                onClick = {
                                    viewModel.updateTakeoffDirection(dir)
                                    takeoffExpanded = false
                                }
                            )
                        }
                    }
                }

                // Alert Inspection Days Dropdown
                var alertDaysExpanded by remember { mutableStateOf(false) }
                Spacer(modifier = Modifier.height(16.dp))
                ExposedDropdownMenuBox(
                    expanded = alertDaysExpanded,
                    onExpandedChange = { alertDaysExpanded = !alertDaysExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = "${prefs.alertInspectionPeriodDays} Days",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Alerts Scan Period (Future)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = alertDaysExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                    )
                    ExposedDropdownMenu(
                        expanded = alertDaysExpanded,
                        onDismissRequest = { alertDaysExpanded = false }
                    ) {
                        listOf(1, 2, 3, 4, 5).forEach { days ->
                            DropdownMenuItem(
                                text = { Text("$days ${if (days == 1) "Day" else "Days"}") },
                                onClick = {
                                    viewModel.updateAlertInspectionPeriodDays(days)
                                    alertDaysExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        } ?: Spacer(modifier = Modifier.fillMaxSize())
    }
}
