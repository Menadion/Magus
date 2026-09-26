package io.github.menadion.magus

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

// Is there a newer Mogar? GitHub's latest release of Menadion/Mogar is the one source of truth:
// its tag is the version, its .apk asset is the download, its notes are the one line of changes.
// Asked once a day when the map opens, and every time the Settings row is tapped (M's calls,
// 2026-09-25). A newer release puts the red dot on the gear and on the row.
object Updates {
    private const val LATEST = "https://api.github.com/repos/Menadion/Mogar/releases/latest"

    class Release(val version: String, val notes: String, val url: String)

    // BUSY: GitHub refused because too many checks came from this internet address this hour.
    enum class State { UNKNOWN, CHECKING, CHECKED, FAILED, BUSY }

    private class TooManyChecks : Exception()

    var state by mutableStateOf(State.UNKNOWN)
        private set

    // The newest release GitHub has told us about, kept across opens.
    var latest by mutableStateOf<Release?>(null)
        private set

    private var installed = "0"

    // The release to offer, or null when this phone already runs the newest one.
    val newer: Release? get() = latest?.takeIf { isNewer(it.version, installed) }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    // On app start: remembers the last answer, so the dot shows without waiting for today's check.
    fun load(context: Context) {
        installed = Diagnostics.appVersion(context)
        val p = prefs(context)
        val version = p.getString("updateLatest", null) ?: return
        val url = p.getString("updateUrl", null) ?: return
        latest = Release(version, p.getString("updateNotes", "") ?: "", url)
        state = State.CHECKED
    }

    // Once a day, when the map opens. A failed check stays quiet and the next open tries again.
    suspend fun checkDaily(context: Context) {
        if (prefs(context).getString("updateCheckedDay", null) == LocalDate.now().toString()) return
        check(context)
    }

    // Asks GitHub now and records what it said.
    suspend fun check(context: Context) {
        installed = Diagnostics.appVersion(context)
        state = State.CHECKING
        val result = withContext(Dispatchers.IO) { runCatching { fetchLatest(context) } }
        result.onSuccess { found ->
            val editor = prefs(context).edit().putString("updateCheckedDay", LocalDate.now().toString())
            if (found != null) {
                latest = found
                editor.putString("updateLatest", found.version)
                    .putString("updateNotes", found.notes)
                    .putString("updateUrl", found.url)
            }
            editor.apply()
            state = State.CHECKED
        }.onFailure {
            state = if (it is TooManyChecks) State.BUSY else State.FAILED
        }
    }

    // Null when there is no release yet: GitHub answers 404 until the first one is published.
    private fun fetchLatest(context: Context): Release? {
        val connection = (URL(LATEST).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Mogar/${Diagnostics.appVersion(context)}")
        }
        try {
            if (connection.responseCode == 404) return null
            // GitHub allows 60 checks an hour per internet address without an account, and a whole
            // home Wi-Fi or a carrier's shared address counts as one. When they're used up it answers
            // 403 or 429 with no requests remaining.
            if (connection.responseCode in listOf(403, 429) && connection.getHeaderField("x-ratelimit-remaining") == "0") {
                throw TooManyChecks()
            }
            if (connection.responseCode != 200) throw IllegalStateException("GitHub answered ${connection.responseCode}")
            val json = JSONObject(connection.inputStream.bufferedReader().readText())
            val version = json.getString("tag_name").removePrefix("v")
            val notes = json.optString("body").lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: ""
            var url = json.getString("html_url")
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.getString("name").endsWith(".apk")) {
                        url = asset.getString("browser_download_url")
                        break
                    }
                }
            }
            return Release(version, notes, url)
        } finally {
            connection.disconnect()
        }
    }

    // "0.10" is newer than "0.9": compared number by number, not as text.
    fun isNewer(candidate: String, installed: String): Boolean {
        val a = candidate.split(".").map { it.trim().toIntOrNull() ?: 0 }
        val b = installed.split(".").map { it.trim().toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    // Opens the download in the browser. The phone downloads it, and Install is in the notification.
    fun open(context: Context, release: Release) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.url)))
    }
}

// The red dot that says "a newer Mogar exists": on the gear's upper-right corner and on the update row.
@Composable
fun UpdateDot(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(11.dp)
            .background(colors.error, CircleShape)
            .border(2.dp, colors.surfaceContainerLowest, CircleShape),
    )
}
