package io.github.menadion.magus

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.RectF
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.rememberUpdatedState
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
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.OnLocationCameraTransitionListener
import org.maplibre.android.location.engine.LocationEngineCallback
import org.maplibre.android.location.engine.LocationEngineResult
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.color
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.match
import org.maplibre.android.style.expressions.Expression.stop
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
private const val ME_SOURCE = "me"

// Dot colours: sharing right now, or switched off.
private const val SHARING_GREEN = "#2E7D32"
private const val PAUSED_GREY = "#9E9E9E"
// My own dot: blue while sharing, the same grey as everyone else when paused.
private const val ME_BLUE = "#1A73E8"

// How far from a dot a tap still counts, so small dots are easy to hit.
private const val TAP_REACH_DP = 24f

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

    var members by remember { mutableStateOf(emptyList<Member>()) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var selectedUid by remember { mutableStateOf<String?>(null) }
    var showKeepRunning by remember { mutableStateOf(false) }

    // Starts background sharing. The first time, also shows how to keep the phone from closing Magus.
    fun startSharing() {
        ShareService.start(context)
        if (!KeepRunning.introShown(context)) {
            KeepRunning.markIntroShown(context)
            showKeepRunning = true
        }
    }

    // Everyone else in the family, live. The map and the card both read this one list.
    DisposableEffect(code) {
        val registration = Family.listen(context) { list ->
            val me = FirebaseAuth.getInstance().currentUser?.uid
            members = list.filter { it.uid != me }
        }
        onDispose { registration?.remove() }
    }

    // Recheck every 30 seconds, so a dot goes hollow even when no new update arrives.
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }

    val askBackground = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { startSharing() }

    val askNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        if (isGranted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)) startSharing()
        else explainBackground = true
    }

    // Once location is allowed and switched on: notifications, then "Allow all the time", then start sharing.
    fun startSharingSteps() {
        if (!Family.isSharing(context)) return
        when {
            Build.VERSION.SDK_INT >= 33 && !isGranted(context, Manifest.permission.POST_NOTIFICATIONS) ->
                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            !isGranted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) -> explainBackground = true
            else -> startSharing()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        FamilyMap(
            members = members,
            now = now,
            sharing = sharing,
            onLocationReady = { startSharingSteps() },
            onDotTapped = { uid -> selectedUid = uid },
        )

        // The card keeps showing the last tapped person while it slides away.
        val selected = members.find { it.uid == selectedUid }
        var shown by remember { mutableStateOf<Member?>(null) }
        if (selected != null) shown = selected
        AnimatedVisibility(
            visible = selected != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            shown?.let { MemberCard(it, now) }
        }

        FamilyStrip(
            code = code,
            sharing = sharing,
            onToggle = {
                sharing = !sharing
                Family.setSharing(context, sharing)
                if (sharing) startSharingSteps() else ShareService.stop(context)
            },
            onKeepRunning = { showKeepRunning = true },
        )

        if (showKeepRunning) {
            BackHandler { showKeepRunning = false }
            KeepRunningScreen(onDone = { showKeepRunning = false })
        }
    }

    if (explainBackground) {
        // "Not now" still shares, but only after Magus has been opened since the phone last restarted.
        AlertDialog(
            onDismissRequest = {
                explainBackground = false
                startSharing()
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
                    startSharing()
                }) { Text("Not now") }
            },
        )
    }
}

private fun isGranted(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

@Composable
fun FamilyMap(
    members: List<Member>,
    now: Long,
    sharing: Boolean,
    onLocationReady: () -> Unit,
    onDotTapped: (String?) -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnDotTapped by rememberUpdatedState(onDotTapped)

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
                // A tap near a dot opens its card; a tap anywhere else closes it.
                val reach = TAP_REACH_DP * context.resources.displayMetrics.density
                m.addOnMapClickListener { point ->
                    val at = m.projection.toScreenLocation(point)
                    val hit = m.queryRenderedFeatures(
                        RectF(at.x - reach, at.y - reach, at.x + reach, at.y + reach),
                        "family-dots", "family-names",
                    ).firstOrNull()
                    currentOnDotTapped(hit?.getStringProperty("uid"))
                    hit != null
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

    // My own dot follows my sharing switch: blue, or grey while paused.
    LaunchedEffect(map, style, hasLocation, sharing) {
        val s = style ?: return@LaunchedEffect
        val component = map?.locationComponent ?: return@LaunchedEffect
        if (!component.isLocationComponentActivated) return@LaunchedEffect
        component.lastKnownLocation?.let { drawMe(s, it, sharing) }
    }

    // Redraw the family whenever the list changes, and on each 30-second recheck.
    LaunchedEffect(style, members, now) {
        style?.let { showFamily(it, members, now) }
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
        LocationComponentActivationOptions.builder(context, style)
            .locationComponentOptions(hiddenPuck(context))
            .build()
    )
    component.isLocationComponentEnabled = true
    component.renderMode = RenderMode.NORMAL

    var lastSent = 0L
    fun maybeSend(location: Location) {
        drawMe(style, location, Family.isSharing(context))
        val now = SystemClock.elapsedRealtime()
        if (lastSent == 0L || now - lastSent >= SEND_EVERY_MS) {
            lastSent = now
            Family.sendLocation(context, location)
        }
    }

    // Zoom in only once a real location arrives. With location off, the map stays on the whole country.
    // A zoom asked for while the camera is still moving onto me gets ignored, so wait for it to arrive.
    var zoomed = false
    val known = component.lastKnownLocation
    component.setCameraMode(CameraMode.TRACKING, object : OnLocationCameraTransitionListener {
        override fun onLocationCameraTransitionFinished(cameraMode: Int) {
            if (known != null && !zoomed) {
                zoomed = true
                component.zoomWhileTracking(15.0)
            }
        }

        override fun onLocationCameraTransitionCanceled(cameraMode: Int) {}
    })
    known?.let { maybeSend(it) }

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

// The library's own dot stays on, because the map follows it, but it's invisible: it only takes its colour
// once, so it can't turn grey when sharing is paused. My dot is drawn by drawMe instead.
private fun hiddenPuck(context: Context): LocationComponentOptions {
    val clear = android.graphics.Color.TRANSPARENT
    return LocationComponentOptions.builder(context)
        .foregroundTintColor(clear)
        .backgroundTintColor(clear)
        .foregroundStaleTintColor(clear)
        .backgroundStaleTintColor(clear)
        .bearingTintColor(clear)
        .accuracyAlpha(0f)
        .elevation(0f)
        .build()
}

// My dot: blue while sharing, grey while paused, with "You" above it.
private fun drawMe(style: Style, location: Location, sharing: Boolean) {
    val me = Feature.fromGeometry(Point.fromLngLat(location.longitude, location.latitude)).apply {
        addBooleanProperty("sharing", sharing)
    }
    style.getSourceAs<GeoJsonSource>(ME_SOURCE)?.setGeoJson(me)
}

// Family members are drawn from one list of points: a dot each, name above.
// Green: sharing. Hollow (white, green ring): sharing but gone quiet. Grey: paused.
private fun addFamilyLayers(style: Style) {
    style.addSource(GeoJsonSource(FAMILY_SOURCE, FeatureCollection.fromFeatures(emptyList())))
    style.addLayer(
        CircleLayer("family-dots", FAMILY_SOURCE).withProperties(
            circleRadius(9f),
            circleColor(
                match(
                    get("state"), color(android.graphics.Color.parseColor(SHARING_GREEN)),
                    stop("paused", color(android.graphics.Color.parseColor(PAUSED_GREY))),
                    stop("quiet", color(android.graphics.Color.WHITE)),
                )
            ),
            circleStrokeColor(
                match(
                    get("state"), color(android.graphics.Color.WHITE),
                    stop("quiet", color(android.graphics.Color.parseColor(SHARING_GREEN))),
                )
            ),
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

    // My own dot, drawn last so it sits on top: blue sharing, grey paused, "You" above.
    style.addSource(GeoJsonSource(ME_SOURCE, FeatureCollection.fromFeatures(emptyList())))
    style.addLayer(
        CircleLayer("me-dot", ME_SOURCE).withProperties(
            circleRadius(9f),
            circleColor(
                switchCase(
                    get("sharing"), color(android.graphics.Color.parseColor(ME_BLUE)),
                    color(android.graphics.Color.parseColor(PAUSED_GREY)),
                )
            ),
            circleStrokeColor("#FFFFFF"),
            circleStrokeWidth(3f),
        )
    )
    style.addLayer(
        SymbolLayer("me-name", ME_SOURCE).withProperties(
            textField("You"),
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

private fun showFamily(style: Style, members: List<Member>, now: Long) {
    val features = members.map { member ->
        Feature.fromGeometry(Point.fromLngLat(member.lng, member.lat)).apply {
            addStringProperty("uid", member.uid)
            addStringProperty("name", member.name)
            addStringProperty("state", member.dotState(now))
        }
    }
    style.getSourceAs<GeoJsonSource>(FAMILY_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(features))
}

@Composable
fun FamilyStrip(code: String, sharing: Boolean, onToggle: () -> Unit, onKeepRunning: () -> Unit) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
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

        // ⋮ menu: settings that aren't needed every day.
        Box {
            TextButton(onClick = { menuOpen = true }) {
                Text("⋮", color = Color.White, style = MaterialTheme.typography.titleLarge)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Keep Magus running") },
                    onClick = {
                        menuOpen = false
                        onKeepRunning()
                    },
                )
            }
        }
    }
}
