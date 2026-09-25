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
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
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
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
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
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.layers.Property.ICON_ANCHOR_CENTER
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
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

// Flying to a person zooms in one level from wherever the map is, but never less than this.
private const val STREET_ZOOM = 15.0

// How far from a dot a tap still counts, so small dots are easy to hit.
private const val TAP_REACH_DP = 24f

// First open asks for a name and a family, then the map shows everyone in it.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        Diagnostics.noteAppOpened(this)
        setContent {
            MogarTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var code by remember { mutableStateOf(Family.savedCode(this)) }
                    val familyCode = code
                    if (familyCode == null) {
                        SetupScreen(onDone = { code = Family.savedCode(this) })
                    } else {
                        FamilyScreen(familyCode, onLeft = { code = null })
                    }
                }
            }
        }
    }
}

// The map, the top strip with the sharing switch, and the steps that let sharing run in the background.
@Composable
fun FamilyScreen(code: String, onLeft: () -> Unit) {
    val context = LocalContext.current
    var sharing by remember { mutableStateOf(Family.isSharing(context)) }
    var explainBackground by remember { mutableStateOf(false) }

    var members by remember { mutableStateOf(emptyList<Member>()) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var selectedUid by remember { mutableStateOf<String?>(null) }
    var showKeepRunning by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var me by remember { mutableStateOf<Member?>(null) }
    var showList by remember { mutableStateOf(false) }
    // Heights of the floating cards, so the camera can aim at the gap between them.
    var topHeight by remember { mutableIntStateOf(0) }
    var bottomHeight by remember { mutableIntStateOf(0) }
    val myUid = FirebaseAuth.getInstance().currentUser?.uid

    // Starts background sharing. The first time, also shows how to keep the phone from closing Mogar.
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
            members = list.filter { it.uid != myUid }
            me = list.find { it.uid == myUid }
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

    // Everyone on the screens: you first, then the family in a fixed order.
    val meNow = (me ?: Member(
        uid = myUid ?: "me",
        name = Family.savedName(context) ?: "You",
        lat = 0.0,
        lng = 0.0,
        battery = null,
        updatedAtMillis = null,
        sharing = sharing,
    )).copy(sharing = sharing)
    val people = listOf(Person(meNow, isYou = true)) +
        members.sortedBy { it.name.lowercase() }.map { Person(it, isYou = false) }

    Box(modifier = Modifier.fillMaxSize()) {
        FamilyMap(
            members = members,
            me = me,
            now = now,
            sharing = sharing,
            selectedUid = selectedUid,
            paddingTop = topHeight,
            paddingBottom = bottomHeight,
            onLocationReady = { startSharingSteps() },
            onDotTapped = { uid -> selectedUid = uid },
        )

        // Bottom: the family row while nobody is picked, the person's card while someone is.
        // The card keeps showing the last picked person while it slides away.
        val selected = people.find { it.uid == selectedUid }
        var shown by remember { mutableStateOf<Person?>(null) }
        if (selected != null) shown = selected
        AnimatedVisibility(
            visible = selected == null,
            enter = slideInVertically(tween(250)) { it },
            exit = slideOutVertically(tween(250)) { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            FamilyRow(
                people = people,
                now = now,
                onPick = { selectedUid = it },
                onSeeAll = { showList = true },
                modifier = Modifier.onSizeChanged { bottomHeight = it.height },
            )
        }
        AnimatedVisibility(
            visible = selected != null,
            enter = slideInVertically(tween(320)) { it },
            exit = slideOutVertically(tween(320)) { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            shown?.let {
                MemberCard(
                    person = it,
                    now = now,
                    onClose = { selectedUid = null },
                    modifier = Modifier.onSizeChanged { size -> bottomHeight = size.height },
                )
            }
        }

        // Top: the family card, and Show everyone under it while someone is picked.
        Column(modifier = Modifier.align(Alignment.TopCenter)) {
            Box(modifier = Modifier.onSizeChanged { topHeight = it.height }) {
                FamilyStrip(
                    code = code,
                    sharing = sharing,
                    onToggle = {
                        sharing = !sharing
                        Family.setSharing(context, sharing)
                        if (sharing) startSharingSteps() else ShareService.stop(context)
                    },
                    onSettings = { showSettings = true },
                )
            }
            AnimatedVisibility(visible = selected != null) {
                ShowEveryoneButton(
                    onClick = { selectedUid = null },
                    modifier = Modifier.padding(start = 12.dp, top = 12.dp),
                )
            }
        }

        if (showList) {
            FamilyListSheet(
                people = people,
                now = now,
                onPick = {
                    showList = false
                    selectedUid = it
                },
                onClose = { showList = false },
            )
        }

        if (showSettings) {
            BackHandler { showSettings = false }
            SettingsScreen(
                code = code,
                onBack = { showSettings = false },
                onKeepRunning = { showKeepRunning = true },
                onLeft = {
                    showSettings = false
                    onLeft()
                },
            )
        }

        if (showKeepRunning) {
            BackHandler { showKeepRunning = false }
            KeepRunningScreen(onDone = { showKeepRunning = false })
        }
    }

    if (explainBackground) {
        // "Not now" still shares, but only after Mogar has been opened since the phone last restarted.
        AlertDialog(
            onDismissRequest = {
                explainBackground = false
                startSharing()
            },
            title = { Text("Keep sharing when Mogar is closed") },
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
    me: Member?,
    now: Long,
    sharing: Boolean,
    selectedUid: String?,
    paddingTop: Int,
    paddingBottom: Int,
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
    var myLocation by remember { mutableStateOf<Location?>(null) }

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
                m.uiSettings.isCompassEnabled = false
                m.uiSettings.isLogoEnabled = false
                m.uiSettings.isAttributionEnabled = false // credit lives under ⋮ > About the map
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
                        "family-dots",
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
        val stop = if (m != null && s != null && hasLocation) showMyLocation(context, m, s) { myLocation = it } else null
        onDispose { stop?.invoke() }
    }

    // My own dot follows my sharing switch: blue, or grey while paused.
    LaunchedEffect(map, style, hasLocation, sharing) {
        val s = style ?: return@LaunchedEffect
        val component = map?.locationComponent ?: return@LaunchedEffect
        if (!component.isLocationComponentActivated) return@LaunchedEffect
        component.lastKnownLocation?.let { drawMe(context, s, it, sharing) }
    }

    // Redraw the family whenever the list changes, and on each 30-second recheck.
    LaunchedEffect(style, members, now, selectedUid) {
        style?.let { showFamily(context, it, members, now, selectedUid) }
    }

    // Picking a person flies the camera to them, aimed at the gap between the top card and the
    // member card. Leaving focus zooms back out to fit everyone. Spec: HANDOFF.md section 4.
    var wasFocused by remember { mutableStateOf(false) }
    LaunchedEffect(map, selectedUid) {
        val m = map ?: return@LaunchedEffect
        val myUid = FirebaseAuth.getInstance().currentUser?.uid
        val mine = myLocation?.let { LatLng(it.latitude, it.longitude) } ?: me?.let { LatLng(it.lat, it.lng) }
        val density = context.resources.displayMetrics.density
        val side = (12 * density).toInt()
        val tagRoom = (60 * density).toInt() // the name tag sits above the dot
        if (selectedUid != null) {
            val target = if (selectedUid == myUid) mine else members.find { it.uid == selectedUid }?.let { LatLng(it.lat, it.lng) }
            if (target == null) return@LaunchedEffect
            // The map stops following my phone once a person is picked.
            if (m.locationComponent.isLocationComponentActivated) m.locationComponent.cameraMode = CameraMode.NONE
            m.cancelTransitions()
            val zoom = maxOf(m.cameraPosition.zoom + 1, STREET_ZOOM)
            m.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(target)
                        .zoom(zoom)
                        .padding(0.0, (paddingTop + tagRoom).toDouble(), 0.0, paddingBottom.toDouble())
                        .build()
                ),
                700,
            )
            wasFocused = true
        } else if (wasFocused) {
            wasFocused = false
            val points = members.map { LatLng(it.lat, it.lng) } + listOfNotNull(mine)
            when {
                points.size >= 2 -> m.animateCamera(
                    CameraUpdateFactory.newLatLngBounds(
                        LatLngBounds.Builder().includes(points).build(),
                        side + tagRoom, paddingTop + tagRoom, side + tagRoom, paddingBottom + side,
                    ),
                    700,
                )
                points.size == 1 -> m.animateCamera(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder()
                            .target(points[0])
                            .zoom(STREET_ZOOM)
                            .padding(0.0, paddingTop.toDouble(), 0.0, paddingBottom.toDouble())
                            .build()
                    ),
                    700,
                )
            }
        }
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
private fun showMyLocation(context: Context, map: MapLibreMap, style: Style, onLocation: (Location) -> Unit): () -> Unit {
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
        onLocation(location)
        drawMe(context, style, location, Family.isSharing(context))
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

// Adds the picture for one marker to the map's cache if it isn't there yet, and returns its id.
private fun markerImage(context: Context, style: Style, state: Markers.State, name: String, selected: Boolean): String {
    val id = Markers.id(state, name, selected)
    if (style.getImage(id) == null) style.addImage(id, Markers.draw(context, state, name, selected))
    return id
}

// My dot: primary while sharing, grey while paused, with a "You" tag above it.
private fun drawMe(context: Context, style: Style, location: Location, sharing: Boolean) {
    val state = if (sharing) Markers.State.YOU else Markers.State.PAUSED
    val me = Feature.fromGeometry(Point.fromLngLat(location.longitude, location.latitude)).apply {
        addStringProperty("icon", markerImage(context, style, state, "You", false))
    }
    style.getSourceAs<GeoJsonSource>(ME_SOURCE)?.setGeoJson(me)
}

// Family members are drawn from one list of points, each with its own picture (see Markers).
// Pictures keep their size at every zoom and never hide each other.
private fun addFamilyLayers(style: Style) {
    style.addSource(GeoJsonSource(FAMILY_SOURCE, FeatureCollection.fromFeatures(emptyList())))
    style.addLayer(
        SymbolLayer("family-dots", FAMILY_SOURCE).withProperties(
            iconImage(get("icon")),
            iconAnchor(ICON_ANCHOR_CENTER),
            iconAllowOverlap(true),
            iconIgnorePlacement(true),
        )
    )

    // My own dot, drawn last so it sits on top.
    style.addSource(GeoJsonSource(ME_SOURCE, FeatureCollection.fromFeatures(emptyList())))
    style.addLayer(
        SymbolLayer("me-dot", ME_SOURCE).withProperties(
            iconImage(get("icon")),
            iconAnchor(ICON_ANCHOR_CENTER),
            iconAllowOverlap(true),
            iconIgnorePlacement(true),
        )
    )
}

private fun showFamily(context: Context, style: Style, members: List<Member>, now: Long, selectedUid: String?) {
    val features = members.map { member ->
        Feature.fromGeometry(Point.fromLngLat(member.lng, member.lat)).apply {
            addStringProperty("uid", member.uid)
            addStringProperty("name", member.name)
            addStringProperty("state", member.dotState(now))
            addStringProperty(
                "icon",
                markerImage(context, style, Markers.state(member, now), member.name, member.uid == selectedUid),
            )
        }
    }
    style.getSourceAs<GeoJsonSource>(FAMILY_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(features))
}

// The floating top card, one row: family name and code on the left, the compact Sharing pill and
// the gear on the right. M's change from the handoff's two-row card (2026-09-25): it took too much map.
@Composable
fun FamilyStrip(code: String, sharing: Boolean, onToggle: () -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier
            .statusBarsPadding()
            .padding(start = 12.dp, top = 12.dp, end = 12.dp)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainerLowest,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, top = 10.dp, end = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    Family.familyLabel(context),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                )
                Text(
                    "Code $code",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = MaterialTheme.typography.bodySmall.fontWeight),
                    color = colors.onSurfaceVariant,
                )
                if (!sharing) {
                    Text(
                        "Your family can't see you",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.error,
                    )
                }
            }
            SharingPill(sharing = sharing, onToggle = onToggle)
            IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

// The sharing switch as a compact pill: pin, ON or OFF, and the switch. ON is dark (primary), OFF is
// light with an outline, so the state still reads at a glance and not only by colour.
@Composable
fun SharingPill(sharing: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .heightIn(min = 44.dp)
            .toggleable(value = sharing, role = Role.Switch, onValueChange = { onToggle() }),
        shape = CircleShape,
        color = if (sharing) colors.primary else colors.surfaceContainerHigh,
        contentColor = if (sharing) colors.onPrimary else colors.onSurface,
        border = if (sharing) null else BorderStroke(2.dp, colors.outline),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PinIcon(off = !sharing)
            Text(if (sharing) "ON" else "OFF", style = MaterialTheme.typography.titleSmall)
            Switch(
                checked = sharing,
                onCheckedChange = null, // the whole pill is the switch
                thumbContent = if (sharing) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
                } else {
                    null
                },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = colors.onPrimary,
                    checkedBorderColor = colors.onPrimary,
                    checkedThumbColor = colors.primary,
                    checkedIconColor = colors.onPrimary,
                ),
            )
        }
    }
}

// A location pin, with a slash through it when sharing is off.
@Composable
private fun PinIcon(off: Boolean) {
    val slash = LocalContentColor.current
    Box(modifier = Modifier.size(24.dp)) {
        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(24.dp))
        if (off) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawLine(
                    color = slash,
                    start = Offset(size.width * 0.15f, size.height * 0.15f),
                    end = Offset(size.width * 0.85f, size.height * 0.85f),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

// The white pill under the top card while someone is picked. Spec: HANDOFF.md section 4, step 6.
@Composable
fun ShowEveryoneButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        color = colors.surfaceContainerLowest,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.heightIn(min = 48.dp).padding(start = 14.dp, end = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ExpandGlyph(colors.primary)
            Text(
                "Show everyone",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = MaterialTheme.typography.labelLarge.fontWeight),
                color = colors.primary,
            )
        }
    }
}
