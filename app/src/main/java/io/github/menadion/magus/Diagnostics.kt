package io.github.menadion.magus

import android.app.Application
import android.content.Context
import android.os.Build

// Six facts about this phone, sent with every location so a quiet dot can be read from afar:
// which phone, which Mogar, whether the keep-running steps were done, whether the sharer was alive
// the last time the app opened, and the last crash if there was one.
object Diagnostics {
    private fun prefs(context: Context) = context.getSharedPreferences("magus", Context.MODE_PRIVATE)

    fun appVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }

    // Called when the map screen opens: was the background sharer already running on its own?
    fun noteAppOpened(context: Context) {
        prefs(context).edit().putBoolean("sharerRunningAtOpen", ShareService.isRunning).apply()
    }

    fun snapshot(context: Context): Map<String, Any?> = mapOf(
        "brand" to Build.MANUFACTURER,
        "model" to Build.MODEL,
        "android" to Build.VERSION.RELEASE,
        "app" to appVersion(context),
        "unrestricted" to KeepRunning.isUnrestricted(context),
        "brandStepDone" to (KeepRunning.brand == KeepRunning.Brand.OTHER || KeepRunning.brandStepDone(context)),
        "sharerRunningAtOpen" to prefs(context).getBoolean("sharerRunningAtOpen", false),
        "lastCrash" to prefs(context).getString("lastCrash", null),
    )

    // Catches a crash, keeps its first lines for the next send, then lets Android handle it as usual.
    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val head = error.stackTraceToString().lineSequence().take(4).joinToString(" | ").take(600)
                prefs(app).edit().putString("lastCrash", head).commit()
            } catch (ignored: Exception) {
            }
            previous?.uncaughtException(thread, error)
        }
    }

    // One grey line for the member card: "Xiaomi 23021RAA2Y · Android 14 · Mogar 0.2 · steps done".
    fun summary(diag: Map<String, Any?>?): String? {
        if (diag == null) return null
        val phone = listOfNotNull(diag["brand"]?.toString()?.replaceFirstChar { it.uppercase() }, diag["model"]?.toString())
            .joinToString(" ")
        val steps = when {
            diag["unrestricted"] == false -> "battery restricted"
            diag["brandStepDone"] == false -> "brand step not done"
            else -> "steps done"
        }
        return listOf(phone, "Android ${diag["android"] ?: "?"}", "Mogar ${diag["app"] ?: "?"}", steps)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
    }
}

class MogarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Diagnostics.install(this)
    }
}
