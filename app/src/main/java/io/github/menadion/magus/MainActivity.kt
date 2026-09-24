package io.github.menadion.magus

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.engine.LocationEngineCallback
import org.maplibre.android.location.engine.LocationEngineResult
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.color
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.switchCase
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textOffset
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

// OpenFreeMap: free map tiles, no key. Swapping to an offline file later changes only this line.
private const val MAP_STYLE = "https://tiles.openfreemap.org/styles/liberty"

// Roughly the middle of the Philippines, zoomed out to show the whole country.
private val PHILIPPINES = LatLng(12.3, 122.5)

// While the app is open, my location goes up at most this often.
private const val SEND_EVERY_MS = 60_000L

private const val FAMILY_SOURCE = "family"

// Dot colours: sharing right now, or switched off.
private const val SHARING_GREEN = "#2E7D32"
private const val PAUSED_GREY = "#9E9E9E"

// First open asks for a name and a family, then the map shows everyone in it.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var code by remember { mutableStateOf(Family.savedCode(this)) }
                    val familyCode = code
                    if (familyCode == null) {
                        SetupScreen(onDone = { code = Family.savedCode(this) })
                    } else {
                        FamilyScreen(familyCode)
                    }
                }
            }
        }
    }
}

// The map, the top strip with the sharing switch, and the steps that let sharing run in the background.
@Composable
fun FamilyScreen(code: String) {
    val context = LocalContext.current
    var sharing by remember { mutableStateOf(Family.isSharing(context)) }
    var explainBackground by remember { mutableStateOf(false) }

    val askBackground = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ShareService.start(context) }

    val askNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        if (isGranted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)) ShareService.start(context)
        else explainBackground = true
    }

    // Once location is allowed and switched on: notifications, then "Allow all the time", then start sharing.
    fun startSharingSteps() {
        if (!Family.isSharing(context)) return
        when {
            Build.VERSION.SDK_INT >= 33 && !isGranted(context, Manifest.permission.POST_NOTIFICATIONS) ->
                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            !isGranted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) -> explainBackground = true
            else -> ShareService.start(context)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        FamilyMap(onLocationReady = { startSharingSteps() })
        FamilyStrip(
            code = code,
            sharing = sharing,
            onToggle = {
                sharing = !sharing
                Family.setSharing(context, sharing)
                if (sharing) startSharingSteps() else ShareService.stop(context)
            },
        )
    }

    if (explainBackground) {
        // "Not now" still shares, but only after Magus has been opened since the phone last restarted.
        AlertDialog(
            onDismissRequest = {
                explainBackground = false
                ShareService.start(context)
            },
            title = { Text("Keep sharing when Magus is closed") },
            text = {
                Text(
                    "On the next screen, choose \"Allow all the time\". " +
                        "Then your family can still see you after your phone restarts."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    explainBackground = false
                    askBackground.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = {
                    explainBackground = false
                    ShareService.start(context)
                }) { Text("Not now") }
            },
        )
    }
}

private fun isGranted(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

@Composable
fun FamilyMap(onLocationReady: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    var hasLocation by remember {
        mutableStateOf(
            isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
                isGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }

    // Google's "Turn on device location?" box. Whatever they tap, carry on to the sharing steps.
    val askTurnOn = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { onLocationReady() }

    val askLocation = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        hasLocation = granted.values.any { it }
        if (hasLocation) checkLocationIsOn(context, { askTurnOn.launch(it) }, onLocationReady)
    }

    // Runs once each time the app opens: permission first, then the phone's location switch, then sharing.
    LaunchedEffect(Unit) {
        if (!hasLocation) {
            askLocation.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        } else {
            checkLocationIsOn(context, { askTurnOn.launch(it) }, onLocationReady)
        }
    }

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            getMapAsync { m ->
                m.cameraPosition = CameraPosition.Builder().target(PHILIPPINES).zoom(4.8).build()
                m.setStyle(Style.Builder().fromUri(MAP_STYLE)) { s ->
                    addFamilyLayers(s)
                    map = m
                    style = s
                }
            }
        }
    }

    // The map view needs to hear when the screen starts, pauses and closes.
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // Once the map is loaded and location is allowed, put the blue dot on, follow it, and share it.
    DisposableEffect(map, style, hasLocation) {
        val m = map
        val s = style
        val stop = if (m != null && s != null && hasLocation) showMyLocation(context, m, s) else null
        onDispose { stop?.invoke() }
    }

    // Everyone else in the family, redrawn whenever anyone's location changes.
    DisposableEffect(style) {
        val s = style
        val registration = if (s == null) null else Family.listen(context) { members ->
            val me = FirebaseAuth.getInstance().currentUser?.uid
            showFamily(s, members.filter { it.uid != me })
        }
        onDispose { registration?.remove() }
    }

    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
}

// Asks Google whether the phone's location is on. If it's off, shows the box that turns it on;
// either way, calls whenDone once that's settled.
private fun checkLocationIsOn(
    context: Context,
    showTurnOnBox: (IntentSenderRequest) -> Unit,
    whenDone: () -> Unit,
) {
    val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000L).build()
    val settings = LocationSettingsRequest.Builder().addLocationRequest(request).setAlwaysShow(true).build()
    LocationServices.getSettingsClient(context)
        .checkLocationSettings(settings)
        .addOnSuccessListener { whenDone() }
        .addOnFailureListener { e ->
            if (e is ResolvableApiException) {
                showTurnOnBox(IntentSenderRequest.Builder(e.resolution).build())
            } else {
                whenDone()
            }
        }
}

// Turns on the blue dot and sends my location at most once a minute while the app is open.
// Returns a function that stops listening for locations.
@SuppressLint("MissingPermission") // only called after the permission check above
private fun showMyLocation(context: Context, map: MapLibreMap, style: Style): () -> Unit {
    val component = map.locationComponent
    component.activateLocationComponent(
        LocationComponentActivationOptions.builder(context, style).build()
    )
    component.isLocationComponentEnabled = true
    component.renderMode = RenderMode.NORMAL
    component.cameraMode = CameraMode.TRACKING

    var lastSent = 0L
    fun maybeSend(location: Location) {
        val now = SystemClock.elapsedRealtime()
        if (lastSent == 0L || now - lastSent >= SEND_EVERY_MS) {
            lastSent = now
            Family.sendLocation(context, location)
        }
    }

    // Zoom in only once a real location arrives. With location off, the map stays on the whole country.
    var zoomed = false
    component.lastKnownLocation?.let {
        zoomed = true
        component.zoomWhileTracking(15.0)
        maybeSend(it)
    }

    val engine = component.locationEngine ?: return {}
    val onFix = object : LocationEngineCallback<LocationEngineResult> {
        override fun onSuccess(result: LocationEngineResult) {
            val location = result.lastLocation ?: return
            if (!zoomed) {
                zoomed = true
                component.zoomWhileTracking(15.0)
            }
            maybeSend(location)
        }

        override fun onFailure(exception: Exception) {}
    }
    engine.requestLocationUpdates(component.locationEngineRequest, onFix, Looper.getMainLooper())
    return { engine.removeLocationUpdates(onFix) }
}

// Family members are drawn from one list of points: a dot each (green sharing, grey paused), name above.
private fun addFamilyLayers(style: Style) {
    style.addSource(GeoJsonSource(FAMILY_SOURCE, FeatureCollection.fromFeatures(emptyList())))
    style.addLayer(
        CircleLayer("family-dots", FAMILY_SOURCE).withProperties(
            circleRadius(9f),
            circleColor(
                switchCase(
                    get("sharing"), color(android.graphics.Color.parseColor(SHARING_GREEN)),
                    color(android.graphics.Color.parseColor(PAUSED_GREY)),
                )
            ),
            circleStrokeColor("#FFFFFF"),
            circleStrokeWidth(3f),
        )
    )
    style.addLayer(
        SymbolLayer("family-names", FAMILY_SOURCE).withProperties(
            textField(get("name")),
            textFont(arrayOf("Noto Sans Bold")),
            textSize(14f),
            textOffset(arrayOf(0f, -1.6f)),
            textColor("#000000"),
            textHaloColor("#FFFFFF"),
            textHaloWidth(2f),
            textAllowOverlap(true),
            textIgnorePlacement(true),
        )
    )
}

private fun showFamily(style: Style, members: List<Member>) {
    val features = members.map { member ->
        Feature.fromGeometry(Point.fromLngLat(member.lng, member.lat)).apply {
            addStringProperty("name", member.name)
            addBooleanProperty("sharing", member.sharing)
        }
    }
    style.getSourceAs<GeoJsonSource>(FAMILY_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(features))
}

@Composable
fun FamilyStrip(code: String, sharing: Boolean, onToggle: () -> Unit) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xAA000000))
            .statusBarsPadding()
            .padding(start = 12.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
    ) {
        Text(
            "${Family.familyLabel(context)}  ·  code $code  ·  You: ${Family.savedName(context) ?: "?"}",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
        FilledTonalButton(
            onClick = onToggle,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        ) { Text(if (sharing) "Sharing: ON" else "Sharing: OFF") }
    }
}
