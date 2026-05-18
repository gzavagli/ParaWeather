package com.gzavagli.paraweather.ui.locations

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation

import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.gzavagli.paraweather.data.model.SavedLocation
import org.osmdroid.config.Configuration
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.views.overlay.Marker

@Composable
fun InteractiveMapScreen(
    onDismiss: () -> Unit,
    viewModel: MapViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val mapUiState by viewModel.mapState.collectAsState()

    // Initialize Osmdroid Configuration (required for tile caching and User-Agent rules)
    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = "ParaWeatherApp/1.0 (Android; gzavagli)"
    }

    var selectedSite by remember { mutableStateOf<SavedLocation?>(null) }
    var pickedCoords by remember { mutableStateOf<GeoPoint?>(null) }
    var customSiteName by remember { mutableStateOf("") }

    var isTerrainMode by remember { mutableStateOf(false) }

    // Renders OpenTopoMap tiles or Standard OpenStreetMap Mapnik
    val streetTileSource = TileSourceFactory.MAPNIK
    val terrainTileSource = remember {
        XYTileSource(
            "OpenTopoMap", 0, 17, 256, ".png",
            arrayOf(
                "https://a.tile.opentopomap.org/",
                "https://b.tile.opentopomap.org/",
                "https://c.tile.opentopomap.org/"
            ),
            "© OpenTopoMap contributors, CC-BY-SA"
        )
    }

    val mapView = remember {
        MapView(context).apply {
            setMultiTouchControls(true)
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(11.0)
            controller.setCenter(GeoPoint(37.3688, -122.0363)) // Default Sunnyvale center
            
            // Trigger initial fetch as soon as map settles on screen
            addOnFirstLayoutListener { _, _, _, _, _ ->
                triggerViewportFetch(this, viewModel)
            }
        }
    }


    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Native Osmdroid View wrapped in AndroidView
            AndroidView(
                factory = { mapView },
                modifier = Modifier.fillMaxSize(),
                update = { map ->
                    // Handle tile swapping
                    map.setTileSource(if (isTerrainMode) terrainTileSource else streetTileSource)

                    // Clear existing overlays before drawing updated viewport elements
                    map.overlays.clear()

                    // 1. Long Press Coordinate Picker Overlay
                    val eventsReceiver = object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                            // Dismiss active overlays on empty tap
                            selectedSite = null
                            pickedCoords = null
                            return true
                        }

                        override fun longPressHelper(p: GeoPoint?): Boolean {
                            pickedCoords = p
                            selectedSite = null
                            return true
                        }
                    }
                    map.overlays.add(MapEventsOverlay(eventsReceiver))

                    // 2. Draw Green Pin if coordinates are picked
                    pickedCoords?.let { picked ->
                        val pickerMarker = Marker(map).apply {
                            position = picked
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            title = "Picked Location"
                        }
                        map.overlays.add(pickerMarker)
                    }

                    // 3. Draw Paragliding site markers from State
                    if (mapUiState is MapUiState.Success) {
                        val sites = (mapUiState as MapUiState.Success).visibleSites
                        sites.forEach { site ->
                            val marker = Marker(map).apply {
                                position = GeoPoint(site.latitude, site.longitude)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                title = site.name
                                setOnMarkerClickListener { _, _ ->
                                    selectedSite = site
                                    pickedCoords = null
                                    true
                                }
                            }
                            map.overlays.add(marker)
                        }
                    }

                    map.invalidate() // Force redrawing the overlays
                }
            )

            // Dynamic Map Drag Listener to automatically fetch spots currently visible in viewport
            DisposableEffect(mapView) {
                val mapListener = object : MapListener {
                    override fun onScroll(event: ScrollEvent?): Boolean {
                        triggerViewportFetch(mapView, viewModel)
                        return true
                    }

                    override fun onZoom(event: ZoomEvent?): Boolean {
                        triggerViewportFetch(mapView, viewModel)
                        return true
                    }
                }
                
                // Wrap listener in DelayedMapListener to prevent spamming queries during fluid pans (debounces query by 300ms)
                val debouncedListener = DelayedMapListener(mapListener, 300)
                mapView.addMapListener(debouncedListener)

                onDispose {
                    mapView.removeMapListener(debouncedListener)
                }
            }

            // --- Floating UI Controls ---

            // Top Banner: Close Map Button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .background(Color.White.copy(alpha = 0.9f), CircleShape)
            ) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Close Map")
            }

            // Top Banner: Map Layer Toggle Button (Terrain / Streets)
            FloatingActionButton(
                onClick = { isTerrainMode = !isTerrainMode },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                containerColor = Color.White
            ) {
                Icon(
                    imageVector = if (isTerrainMode) Icons.Default.Map else Icons.Default.Landscape,
                    contentDescription = "Toggle Map Type",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Middle: Loading spinner if API is working
            if (mapUiState is MapUiState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                )
            }

            // Bottom Banner Overlay: Clicked Paragliding Launch Spot Details
            AnimatedVisibility(
                visible = selectedSite != null,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                selectedSite?.let { site ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Navigation,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = site.name,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { selectedSite = null }) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Dismiss")
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Coordinates: ${site.latitude.coerceDecimals(4)}, ${site.longitude.coerceDecimals(4)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    viewModel.saveLocation(site.name, site.latitude, site.longitude)
                                    selectedSite = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Import Flying Site to List")
                            }
                        }
                    }
                }
            }

            // Bottom Banner Overlay: Save Long-pressed Coordinates form
            AnimatedVisibility(
                visible = pickedCoords != null,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                pickedCoords?.let { coords ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AddLocation,
                                    contentDescription = null,
                                    tint = Color.Green,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Name Custom Coordinate",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { pickedCoords = null }) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Dismiss")
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = customSiteName,
                                onValueChange = { customSiteName = it },
                                label = { Text("Name (e.g. My Secret Ridge)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Coords: ${coords.latitude.coerceDecimals(4)}, ${coords.longitude.coerceDecimals(4)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    if (customSiteName.isNotBlank()) {
                                        viewModel.saveLocation(customSiteName, coords.latitude, coords.longitude)
                                        customSiteName = ""
                                        pickedCoords = null
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = customSiteName.isNotBlank()
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Save Custom Spot")
                            }
                        }
                    }
                }
            }
        }
    }
}

// Fetch bounding-box dynamic handler
private fun triggerViewportFetch(map: MapView, viewModel: MapViewModel) {
    val bbox = map.boundingBox
    val south = bbox.latSouth
    val west = bbox.lonWest
    val north = bbox.latNorth
    val east = bbox.lonEast
    
    viewModel.searchSitesInViewport(south, west, north, east)
}

// Circle Shape helper
private val CircleShape = RoundedCornerShape(50)

// Simple Helper to round doubles cleanly
private fun Double.coerceDecimals(decimals: Int): Double {
    var multiplier = 1.0
    repeat(decimals) { multiplier *= 10.0 }
    return kotlin.math.round(this * multiplier) / multiplier
}

