package io.github.menadion.magus

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

// The language switch in Settings. Auto follows the phone; English and Filipino override it.
// Applied by wrapping the activity's and the service's base context (see attachBaseContext in
// MainActivity and ShareService), so every getString and stringResource reads the chosen
// language. Picking one relaunches the screen on Settings, so the change is instant. (Activity.recreate()
// was tried first: on Android 15 it brought the screen back inset below the status bar.) Spec: Backlog,
// "Tagalog switch", 2026-09-25; M's call the same evening: Settings only, Auto by default,
// the language is called Filipino.
object LanguageSetting {
    const val SYSTEM = "system"
    const val ENGLISH = "en"
    const val FILIPINO = "fil"

    // Intent extra: the relaunched screen opens on Settings, where the switch was.
    const val OPEN_SETTINGS = "openSettings"

    var mode by mutableStateOf(SYSTEM)
        private set

    fun load(context: Context) {
        mode = context.getSharedPreferences("magus", Context.MODE_PRIVATE).getString("language", SYSTEM) ?: SYSTEM
    }

    fun set(activity: Activity, value: String) {
        mode = value
        activity.getSharedPreferences("magus", Context.MODE_PRIVATE).edit().putString("language", value).apply()
        activity.startActivity(Intent(activity, activity.javaClass).putExtra(OPEN_SETTINGS, true))
        activity.finish()
        @Suppress("DEPRECATION")
        activity.overridePendingTransition(0, 0)
    }

    // The base context with the chosen language, or the base itself on Auto.
    fun wrap(base: Context): Context {
        load(base)
        if (mode == SYSTEM) return base
        val locale = Locale.forLanguageTag(mode)
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }
}
