package com.openbible.ui.locations

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import android.content.pm.PackageManager
import com.openbible.data.db.entity.BibleLocationEntity
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File

/**
 * Offline-capable map of biblical locations using osmdroid (OpenStreetMap).
 *
 * Tile caching is configured to use the app's cache directory so tiles
 * are persisted between sessions and can be pre-seeded for offline use.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationMapScreen(
    onNavigateBack: () -> Unit,
    onLocationClick: (locationId: String) -> Unit,
    onOpenList: () -> Unit = {},
    viewModel: LocationViewModel = hiltViewModel()
) {
    val locations by viewModel.allLocations.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bible Map") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onOpenList) { Text("List") }
                }
            )
        }
    ) { padding ->
        BibleMapView(
            locations = locations,
            onLocationClick = onLocationClick,
            context = context,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }
}

@Composable
private fun BibleMapView(
    locations: List<BibleLocationEntity>,
    onLocationClick: (String) -> Unit,
    context: Context,
    modifier: Modifier = Modifier
) {
    // Configure osmdroid once
    val mapView = remember {
        Configuration.getInstance().apply {
            // OSM tile policy requires a meaningful User-Agent — use package/version
            val appVersion = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
            } catch (_: PackageManager.NameNotFoundException) { "1.0" }
            userAgentValue = "${context.packageName}/$appVersion"
            // ponytail: tile cache in app cache dir, survives sessions
            osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles").also { it.mkdirs() }
        }
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            // Default zoom to show Israel region
            controller.setZoom(7.0)
            controller.setCenter(GeoPoint(31.5, 35.0))
            minZoomLevel = 3.0
            maxZoomLevel = 19.0
        }
    }

    // Lifecycle management
    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()  // prevents tile downloader thread leak
        }
    }

    // Update markers when locations change
    LaunchedEffect(locations) {
        mapView.overlays.removeAll { it is Marker }
        locations.forEach { loc ->
            val marker = Marker(mapView).apply {
                position = GeoPoint(loc.latitude, loc.longitude)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = loc.name
                snippet = loc.modernName?.let { "$it · ${loc.category}" } ?: loc.category
                setOnMarkerClickListener { _, _ ->
                    onLocationClick(loc.id)
                    true
                }
            }
            mapView.overlays.add(marker)
        }
        mapView.invalidate()
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier
    )
}
