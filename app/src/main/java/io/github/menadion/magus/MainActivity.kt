package io.github.menadion.magus

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import android.Manifest
import android.app.Activity
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
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
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
import kotlin.math.log2
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
    // The language switch: every string this screen reads comes through the wrapped context.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageSetting.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MapLibre.getInstance(this)
        Diagnostics.noteAppOpened(this)
        ThemeSetting.load(this)
        Updates.load(this)
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
    // Show everyone stays up from the first pick until it's tapped; closing a card leaves the map
    // where it is (M's call, 2026-09-26). Each tap bumps fitRequest, which the map answers.
    var zoomedIn by remember { mutableStateOf(false) }
    var fitRequest by remember { mutableIntStateOf(0) }
    var showKeepRunning by remember { mutableStateOf(false) }
    var showSettings by rememberSaveable {
        mutableStateOf((context as? Activity)?.intent?.getBooleanExtra(LanguageSetting.OPEN_SETTINGS, false) ?: false)
    }
    var showFamilyPage by remember { mutableStateOf(false) }
    var familyName by remember { mutableStateOf(Family.savedFamilyName(context)) }
    var familyCreator by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
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
            me?.let { Family.cachePhoto(context, it.photo) }
        }
        val nameWatch = Family.listenFamily(context) { name, createdBy ->
            if (name != null) familyName = name
            familyCreator = createdBy
        }
        onDispose {
            registration?.remove()
            nameWatch?.remove()
        }
    }

    // The toggle notice fades after a moment.
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(1500)
            notice = null
        }
    }

    // Once a day, asks GitHub whether a newer Mogar exists (the red dot on the gear).
    LaunchedEffect(Unit) { Updates.checkDaily(context) }

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
        name = Family.savedName(context) ?: context.getString(R.string.you),
        lat = 0.0,
        lng = 0.0,
        battery = null,
        updatedAtMillis = null,
        sharing = sharing,
        phone = Family.savedPhone(context),
    )).copy(sharing = sharing)
    val personColors = PersonColors.assign(members.map { it.uid } + listOfNotNull(myUid))
    val people = listOf(Person(meNow, isYou = true, youLabel = context.getString(R.string.you))) +
        members.sortedBy { it.name.lowercase() }.map { Person(it, isYou = false, color = personColors.getValue(it.uid)) }

    // Where each member's dot sits on screen, and the screen's size, for the edge markers.
    var dotPoints by remember { mutableStateOf(emptyMap<String, Offset>()) }
    var screenSize by remember { mutableStateOf(IntSize.Zero) }

    Box(modifier = Modifier.fillMaxSize().onSizeChanged { screenSize = it }) {
        FamilyMap(
            members = members,
            colors = personColors,
            onProjected = { dotPoints = it },
            me = me,
            now = now,
            sharing = sharing,
            selectedUid = selectedUid,
            paddingTop = topHeight,
            paddingBottom = { bottomHeight },
            fitRequest = fitRequest,
            onLocationReady = { startSharingSteps() },
            // A tap on empty map (uid null) closes whatever the panel shows.
            onDotTapped = { uid ->
                showList = false
                selectedUid = uid
            },
        )

        // Family whose dots are off the visible map, pinned to its edge and pointing at them.
        EdgeMarkers(
            people = people,
            now = now,
            points = dotPoints,
            area = Rect(0f, topHeight.toFloat(), screenSize.width.toFloat(), (screenSize.height - bottomHeight).toFloat()),
            onPick = {
                showList = false
                selectedUid = it
            },
        )

        // Bottom: the family box on the screen's bottom edge, and the panel that rises out of its
        // top: the person's card while someone is picked, the list after See all. The panel keeps
        // showing the last picked person while it slides away.
        val selected = people.find { it.uid == selectedUid }
        var shown by remember { mutableStateOf<Person?>(null) }
        if (selected != null) {
            shown = selected
            zoomedIn = true
        }
        val panelOpen = showList || selected != null
        fun closePanel() {
            showList = false
            selectedUid = null
        }
        BackHandler(enabled = panelOpen) { closePanel() }
        var boxHeight by remember { mutableIntStateOf(0) }
        val boxHeightDp = with(LocalDensity.current) { boxHeight.toDp() }
        Box(modifier = Modifier.align(Alignment.BottomCenter).onSizeChanged { bottomHeight = it.height }) {
            AnimatedVisibility(
                visible = panelOpen,
                enter = slideInVertically(tween(320)) { it },
                exit = slideOutVertically(tween(260)) { it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = (boxHeightDp - BOX_CORNER.dp).coerceAtLeast(0.dp)),
            ) {
                BottomPanel(onClose = { closePanel() }) {
                    Crossfade(targetState = showList, label = "panel", modifier = Modifier.fillMaxWidth()) { list ->
                        if (list) Box(modifier = Modifier.height(listPanelHeight())) {
                            FamilyList(
                                people = people,
                                now = now,
                                onPick = {
                                    showList = false
                                    selectedUid = it
                                },
                                onClose = { showList = false },
                            )
                        } else {
                            shown?.let { MemberCard(person = it, now = now, onClose = { selectedUid = null }) }
                        }
                    }
                }
            }
            // Drawn over the panel, so the panel's bottom hides behind the box's rounded top.
            FamilyRow(
                people = people,
                now = now,
                onPick = {
                    showList = false
                    selectedUid = it
                },
                onSeeAll = {
                    selectedUid = null
                    showList = true
                },
                modifier = Modifier.align(Alignment.BottomCenter).zIndex(1f).onSizeChanged { boxHeight = it.height },
            )
        }

        // Top: the family card, and Show everyone under it while someone is picked.
        Column(modifier = Modifier.align(Alignment.TopCenter)) {
            Box(modifier = Modifier.onSizeChanged { topHeight = it.height }) {
                FamilyStrip(
                    code = code,
                    familyName = familyName,
                    memberCount = members.size + 1,
                    sharing = sharing,
                    onToggle = {
                        sharing = !sharing
                        Family.setSharing(context, sharing)
                        if (sharing) startSharingSteps() else ShareService.stop(context)
                        notice = context.getString(if (sharing) R.string.sharing_notice_on else R.string.sharing_notice_off)
                    },
                    updateAvailable = Updates.newer != null,
                    onSettings = { showSettings = true },
                    onFamily = { showFamilyPage = true },
                )
            }
            AnimatedVisibility(visible = zoomedIn) {
                ShowEveryoneButton(
                    onClick = {
                        selectedUid = null
                        zoomedIn = false
                        fitRequest++
                    },
                    modifier = Modifier.padding(start = 12.dp, top = 12.dp),
                )
            }
        }

        SharingNotice(text = notice, modifier = Modifier.align(Alignment.Center))

        if (showFamilyPage) {
            BackHandler { showFamilyPage = false }
            FamilyPage(
                code = code,
                canRename = familyCreator != null && familyCreator == myUid,
                people = people,
                now = now,
                onBack = { showFamilyPage = false },
                onPick = {
                    showFamilyPage = false
                    selectedUid = it
                },
                onLeft = {
                    showFamilyPage = false
                    onLeft()
                },
            )
        }

        if (showSettings) {
            BackHandler { showSettings = false }
            SettingsScreen(
                onBack = { showSettings = false },
                onKeepRunning = { showKeepRunning = true },
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
            title = { Text(stringResource(R.string.background_title)) },
            text = {
                Text(stringResource(R.string.background_text))
            },
            confirmButton = {
                TextButton(onClick = {
                    explainBackground = false
                    askBackground.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }) { Text(stringResource(R.string.continue_label)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    explainBackground = false
                    startSharing()
                }) { Text(stringResource(R.string.not_now)) }
            },
        )
    }
}

private fun isGranted(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

@Composable
fun FamilyMap(
    members: List<Member>,
    colors: Map<String, Int>, // each member's own colour, from PersonColors
    onProjected: (Map<String, Offset>) -> Unit, // where each dot sits on screen, after every camera move
    me: Member?,
    now: Long,
    sharing: Boolean,
    selectedUid: String?,
    paddingTop: Int,
    paddingBottom: () -> Int, // read when the camera moves, so a panel that just opened counts
    fitRequest: Int, // goes up by one each time Show everyone is tapped
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

    // Reports where each member's dot sits on screen, in pixels, for the edge markers.
    val latestMembers by rememberUpdatedState(members)
    val latestOnProjected by rememberUpdatedState(onProjected)
    fun project(m: MapLibreMap) {
        latestOnProjected(latestMembers.associate { member ->
            m.projection.toScreenLocation(LatLng(member.lat, member.lng)).let { member.uid to Offset(it.x, it.y) }
        })
    }
    LaunchedEffect(map, members) { map?.let { project(it) } }

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            getMapAsync { m ->
                m.cameraPosition = CameraPosition.Builder().target(PHILIPPINES).zoom(4.8).build()
                // No further out than a world as tall as the screen; past that, blank shows below
                // Antarctica. MapLibre draws the world 512 dp across at zoom 0.
                val screenDp = context.resources.displayMetrics.run { heightPixels / density }
                m.setMinZoomPreference(log2(screenDp / 512.0))
                // The screen's centre can't cross the date line, so panning sideways ends instead of
                // looping round the world forever (M's option A, 2026-09-26, until a globe exists).
                m.setLatLngBoundsForCameraTarget(LatLngBounds.world())
                m.addOnCameraMoveListener { project(m) }
                m.addOnCameraIdleListener { project(m) }
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
    LaunchedEffect(map, style, hasLocation, sharing, Photos.key(me?.photo)) {
        val s = style ?: return@LaunchedEffect
        val component = map?.locationComponent ?: return@LaunchedEffect
        if (!component.isLocationComponentActivated) return@LaunchedEffect
        component.lastKnownLocation?.let { drawMe(context, s, it, sharing) }
    }

    // Redraw the family whenever the list changes, and on each 30-second recheck.
    LaunchedEffect(style, members, now, selectedUid) {
        style?.let { showFamily(context, it, members, colors, now, selectedUid) }
    }

    // Picking a person flies the camera to them, aimed at the gap between the top card and the
    // member card. Closing the card leaves the camera there; only Show everyone zooms back out to
    // fit everyone (M's change, 2026-09-26, to HANDOFF.md section 4).
    val density = context.resources.displayMetrics.density
    val side = (12 * density).toInt()
    val tagRoom = (60 * density).toInt() // the name tag sits above the dot
    fun mine() = myLocation?.let { LatLng(it.latitude, it.longitude) } ?: me?.let { LatLng(it.lat, it.lng) }
    LaunchedEffect(map, selectedUid) {
        val m = map ?: return@LaunchedEffect
        val myUid = FirebaseAuth.getInstance().currentUser?.uid
        val mine = mine()
        if (selectedUid != null) {
            val target = if (selectedUid == myUid) mine else members.find { it.uid == selectedUid }?.let { LatLng(it.lat, it.lng) }
            if (target == null) return@LaunchedEffect
            // The map stops following my phone once a person is picked.
            if (m.locationComponent.isLocationComponentActivated) m.locationComponent.cameraMode = CameraMode.NONE
            m.cancelTransitions()
            // Wait one frame, so the card that opens with this pick has been measured into paddingBottom.
            withFrameNanos { }
            val zoom = maxOf(m.cameraPosition.zoom + 1, STREET_ZOOM)
            m.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(target)
                        .zoom(zoom)
                        .padding(0.0, (paddingTop + tagRoom).toDouble(), 0.0, paddingBottom().toDouble())
                        .build()
                ),
                700,
            )
        }
    }

    LaunchedEffect(map, fitRequest) {
        val m = map ?: return@LaunchedEffect
        if (fitRequest == 0) return@LaunchedEffect
        val points = members.map { LatLng(it.lat, it.lng) } + listOfNotNull(mine())
        when {
            points.size >= 2 -> m.animateCamera(
                CameraUpdateFactory.newLatLngBounds(
                    LatLngBounds.Builder().includes(points).build(),
                    side + tagRoom, paddingTop + tagRoom, side + tagRoom, paddingBottom() + side,
                ),
                700,
            )
            points.size == 1 -> m.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(points[0])
                        .zoom(STREET_ZOOM)
                        .padding(0.0, paddingTop.toDouble(), 0.0, paddingBottom().toDouble())
                        .build()
                ),
                700,
            )
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
private fun markerImage(
    context: Context,
    style: Style,
    state: Markers.State,
    name: String,
    selected: Boolean,
    you: Boolean = false,
    photo: ByteArray? = null,
    color: Int = MogarColors.FamilyGreen.toArgb(),
): String {
    val id = Markers.id(state, name, selected, you, photo, color)
    if (style.getImage(id) == null) style.addImage(id, Markers.draw(context, state, name, selected, you, photo, color))
    return id
}

// My dot: primary while sharing, grey while paused, with a "You" tag above it.
private fun drawMe(context: Context, style: Style, location: Location, sharing: Boolean) {
    val state = if (sharing) Markers.State.YOU else Markers.State.PAUSED
    val me = Feature.fromGeometry(Point.fromLngLat(location.longitude, location.latitude)).apply {
        addStringProperty(
            "icon",
            markerImage(context, style, state, context.getString(R.string.you), selected = false, you = true, photo = Family.savedPhoto(context)),
        )
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

private fun showFamily(context: Context, style: Style, members: List<Member>, colors: Map<String, Int>, now: Long, selectedUid: String?) {
    val features = members.map { member ->
        Feature.fromGeometry(Point.fromLngLat(member.lng, member.lat)).apply {
            addStringProperty("uid", member.uid)
            addStringProperty("name", member.name)
            addStringProperty("state", member.dotState(now))
            addStringProperty(
                "icon",
                markerImage(
                    context, style, Markers.state(member, now), member.name, member.uid == selectedUid,
                    photo = member.photo, color = colors.getValue(member.uid),
                ),
            )
        }
    }
    style.getSourceAs<GeoJsonSource>(FAMILY_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(features))
}

// The floating top card, one row: family name and code on the left, the compact Sharing pill and
// the gear on the right. M's change from the handoff's two-row card (2026-09-25): it took too much map.
@Composable
fun FamilyStrip(
    code: String,
    familyName: String?,
    memberCount: Int,
    sharing: Boolean,
    updateAvailable: Boolean,
    onToggle: () -> Unit,
    onSettings: () -> Unit,
    onFamily: () -> Unit,
) {
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
            // The name is the door to the family page, like a chat's header in Messenger.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClick = onFamily)
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    Family.familyLabel(LocalContext.current, familyName),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                )
                Text(
                    if (memberCount == 1) stringResource(R.string.member_one) else stringResource(R.string.members_n, memberCount),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = MaterialTheme.typography.bodySmall.fontWeight),
                    color = colors.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .width(1.dp)
                    .height(28.dp)
                    .background(colors.outlineVariant),
            )
            SharingDot(sharing = sharing, onToggle = onToggle)
            IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
                Box {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = stringResource(if (updateAvailable) R.string.settings_update_available else R.string.settings),
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(26.dp),
                    )
                    // The red dot on the gear's upper-right corner, only while a newer Mogar exists.
                    if (updateAvailable) {
                        UpdateDot(modifier = Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-3).dp))
                    }
                }
            }
        }
    }
}

// The sharing switch beside the gear: a location pin in a blue circle while sharing, grey with a
// slash through it when off. No label; the notice across the map says what just happened.
@Composable
fun SharingDot(sharing: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val sharingDescription = stringResource(if (sharing) R.string.sharing_state_on else R.string.sharing_state_off)
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .toggleable(value = sharing, role = Role.Switch, onValueChange = { onToggle() })
            .semantics { contentDescription = sharingDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(if (sharing) colors.primary else MogarColors.Paused, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                tint = colors.onPrimary,
                modifier = Modifier.size(22.dp),
            )
            if (!sharing) {
                val slash = colors.onPrimary
                Canvas(modifier = Modifier.size(36.dp)) {
                    drawLine(
                        color = slash,
                        start = Offset(size.width * 0.22f, size.height * 0.22f),
                        end = Offset(size.width * 0.78f, size.height * 0.78f),
                        strokeWidth = 2.5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

// The notice across the map after a toggle: a white band from edge to edge, gone after a moment.
@Composable
fun SharingNotice(text: String?, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf(text) }
    if (text != null) shown = text
    AnimatedVisibility(
        visible = text != null,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(220)),
        modifier = modifier,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                shown ?: "",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = MaterialTheme.typography.bodyMedium.fontWeight),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp),
            )
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
                stringResource(R.string.show_everyone),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = MaterialTheme.typography.labelLarge.fontWeight),
                color = colors.primary,
            )
        }
    }
}
