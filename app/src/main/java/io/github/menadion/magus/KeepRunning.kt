package io.github.menadion.magus

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

// Xiaomi, Vivo and Samsung kill background apps when they're swiped away, unless the phone is told not to.
// This screen walks through telling it. Plain Android only needs step 1.
object KeepRunning {
    enum class Brand { XIAOMI, VIVO, SAMSUNG, OTHER }

    val brand: Brand = when (Build.MANUFACTURER.lowercase()) {
        "xiaomi", "redmi", "poco" -> Brand.XIAOMI
        "vivo", "iqoo" -> Brand.VIVO
        "samsung" -> Brand.SAMSUNG
        else -> Brand.OTHER
    }

    private fun prefs(context: Context) = context.getSharedPreferences("magus", Context.MODE_PRIVATE)

    // Shown once automatically; after that, only from the ⋮ menu.
    fun introShown(context: Context) = prefs(context).getBoolean("keepRunningShown", false)
    fun markIntroShown(context: Context) = prefs(context).edit().putBoolean("keepRunningShown", true).apply()

    // Brand switches can't be read by the app, so the person ticks them.
    fun brandStepDone(context: Context) = prefs(context).getBoolean("brandStepDone", false)
    fun setBrandStepDone(context: Context, done: Boolean) =
        prefs(context).edit().putBoolean("brandStepDone", done).apply()

    // Step 1: Android's own "no battery restrictions" list. This one the app can check.
    fun isUnrestricted(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    // Android's "Let Magus always run in the background?" box.
    @SuppressLint("BatteryLife") // the whole point of the app is to run in the background
    fun askUnrestricted(context: Context) {
        val ask = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
        if (!tryOpen(context, ask)) openAppInfo(context)
    }

    // Step 2: the brand's own page, or Magus's App info page if that page doesn't exist on this phone.
    fun openBrandPage(context: Context) {
        when (brand) {
            // Xiaomi's Autostart list lives in its Security app (HyperOS 2 moved it off App info).
            Brand.XIAOMI -> {
                val autostart = Intent().setComponent(
                    ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity",
                    )
                )
                if (!tryOpen(context, autostart)) openAppInfo(context)
            }
            Brand.VIVO -> {
                val autostart = Intent().setComponent(
                    ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
                    )
                )
                if (!tryOpen(context, autostart)) openAppInfo(context)
            }
            // Samsung's Device care battery page holds Background usage limits (sleeping apps).
            Brand.SAMSUNG -> {
                val battery = Intent().setComponent(
                    ComponentName(
                        "com.samsung.android.lool",
                        "com.samsung.android.sm.battery.ui.BatteryActivity",
                    )
                )
                if (!tryOpen(context, battery)) openAppInfo(context)
            }
            Brand.OTHER -> openAppInfo(context)
        }
    }

    // Last step: Android removes an app's permissions if it isn't opened for a few months.
    // Parents may never open Magus once it's set up, so this has to be off. Android 11 and up.
    fun neverPaused(context: Context): Boolean =
        Build.VERSION.SDK_INT < 30 || context.packageManager.isAutoRevokeWhitelisted

    fun openPauseSetting(context: Context) {
        val page = Intent(Intent.ACTION_AUTO_REVOKE_PERMISSIONS, Uri.parse("package:${context.packageName}"))
        if (!tryOpen(context, page)) openAppInfo(context)
    }

    private fun openAppInfo(context: Context) {
        tryOpen(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
    }

    private fun tryOpen(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }
}

@Composable
fun KeepRunningScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // Re-read step 1 each time they come back from the settings page.
    var checks by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) checks++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val unrestricted = remember(checks) { KeepRunning.isUnrestricted(context) }
    val neverPaused = remember(checks) { KeepRunning.neverPaused(context) }
    var brandDone by remember { mutableStateOf(KeepRunning.brandStepDone(context)) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Keep Magus running", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Some phones close Magus to save battery. Then your family stops seeing where you are. " +
                    "Do these steps once.",
                style = MaterialTheme.typography.bodyLarge,
            )

            StepCard(
                number = 1,
                title = "Let Magus run in the background",
                // Xiaomi skips Android's box and opens its own Battery saver page instead.
                detail = if (KeepRunning.brand == KeepRunning.Brand.XIAOMI) {
                    "Tap the button, then choose No restrictions."
                } else {
                    "Tap the button, then choose Allow."
                },
                done = unrestricted,
                buttonText = "Allow",
                onButton = { KeepRunning.askUnrestricted(context) },
            )

            when (KeepRunning.brand) {
                KeepRunning.Brand.XIAOMI -> BrandStep(
                    title = "Xiaomi: turn on Autostart",
                    detail = "Tap the button. On the page that opens, turn on Autostart.",
                    done = brandDone,
                    onDoneChange = { brandDone = it; KeepRunning.setBrandStepDone(context, it) },
                )
                KeepRunning.Brand.VIVO -> BrandStep(
                    title = "Vivo: allow background use",
                    detail = "Tap the button. Turn on Magus in the list that opens. " +
                        "If you see App info instead, open Battery and allow high background power use.",
                    done = brandDone,
                    onDoneChange = { brandDone = it; KeepRunning.setBrandStepDone(context, it) },
                )
                KeepRunning.Brand.SAMSUNG -> BrandStep(
                    title = "Samsung: don't put Magus to sleep",
                    detail = "Tap the button. Under Background usage limits, turn off " +
                        "\"Put unused apps to sleep\". Then open Never sleeping apps and add Magus. " +
                        "On an older Samsung this is under Device care, Battery, then the ⋮ menu.",
                    done = brandDone,
                    onDoneChange = { brandDone = it; KeepRunning.setBrandStepDone(context, it) },
                )
                KeepRunning.Brand.OTHER -> {}
            }

            if (Build.VERSION.SDK_INT >= 30) {
                StepCard(
                    number = if (KeepRunning.brand == KeepRunning.Brand.OTHER) 2 else 3,
                    title = "Don't pause Magus when unused",
                    detail = "Tap the button, then turn off \"Pause app activity if unused\". " +
                        "Otherwise your phone stops Magus after a few months without opening it.",
                    done = neverPaused,
                    buttonText = "Open setting",
                    onButton = { KeepRunning.openPauseSetting(context) },
                )
            }

            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
    }
}

@Composable
private fun BrandStep(title: String, detail: String, done: Boolean, onDoneChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    StepCard(
        number = 2,
        title = title,
        detail = detail,
        done = done,
        buttonText = "Open settings",
        onButton = { KeepRunning.openBrandPage(context) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = done, onCheckedChange = onDoneChange)
            Text("I did this")
        }
    }
}

@Composable
private fun StepCard(
    number: Int,
    title: String,
    detail: String,
    done: Boolean,
    buttonText: String,
    onButton: () -> Unit,
    extra: @Composable () -> Unit = {},
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                (if (done) "✓ " else "$number. ") + title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(detail, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onButton) { Text(buttonText) }
            extra()
        }
    }
}
