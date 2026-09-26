package io.github.menadion.magus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// After the phone restarts, or Mogar is updated, turns background sharing back on if it was on.
// After an update it also deletes the downloaded file the update came from.
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) Updates.cleanUp(context)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            ShareService.start(context, fromBackground = true)
        }
    }
}
