package io.github.menadion.magus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// After the phone restarts, or Mogar is updated, turns background sharing back on if it was on.
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            ShareService.start(context, fromBackground = true)
        }
    }
}
