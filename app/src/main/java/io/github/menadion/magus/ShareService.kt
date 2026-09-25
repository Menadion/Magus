package io.github.menadion.magus

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

// Every 5 minutes, even with the app closed.
private const val SHARE_EVERY_MS = 5 * 60_000L

private const val CHANNEL_ID = "sharing"
private const val NOTIFICATION_ID = 1

// Keeps sending my location in the background. Android only allows this with a permanent notification,
// which doubles as the reminder that sharing is on.
class ShareService : Service() {
    // The notification's words follow the language switch too.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageSetting.wrap(newBase))
    }

    private lateinit var client: FusedLocationProviderClient
    private var listening = false

    private val onLocation = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { Family.sendLocation(this@ShareService, it) }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        client = LocationServices.getFusedLocationProviderClient(this)
    }

    @SuppressLint("MissingPermission") // start() only runs this after checking the permission
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } catch (e: SecurityException) {
            // Android refused background location (e.g. no "Allow all the time"). Stop quietly, don't crash.
            stopSelf()
            return START_NOT_STICKY
        }
        if (!listening) {
            listening = true
            // Balanced: Wi-Fi and cell towers, roughly 100 m, light on battery.
            val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, SHARE_EVERY_MS)
                .setMinUpdateIntervalMillis(SHARE_EVERY_MS)
                .build()
            client.requestLocationUpdates(request, onLocation, Looper.getMainLooper())
            // Send one right away, so switching sharing on shows up without a 5-minute wait.
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { location -> location?.let { Family.sendLocation(this, it) } }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        client.removeLocationUpdates(onLocation)
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.channel_sharing), NotificationManager.IMPORTANCE_LOW)
        )
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.sharing_with, Family.familyLabel(this)))
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    companion object {
        // True while the background sharer is alive in this process.
        @Volatile var isRunning = false

        // Starts sharing if it's switched on and location is allowed. Safe to call more than once.
        // fromBackground: called with no screen open (after a restart), which needs "Allow all the time".
        fun start(context: Context, fromBackground: Boolean = false) {
            if (Family.savedCode(context) == null || !Family.isSharing(context)) return
            if (!hasLocationPermission(context)) return
            if (fromBackground && ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) return
            ContextCompat.startForegroundService(context, Intent(context, ShareService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ShareService::class.java))
        }

        private fun hasLocationPermission(context: Context) =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    }
}
