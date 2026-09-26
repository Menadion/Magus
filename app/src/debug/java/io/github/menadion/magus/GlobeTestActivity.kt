package io.github.menadion.magus

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView

// Debug builds only: the globe test page full screen. Nothing in the app opens it; start it with
//   adb shell am start -n io.github.menadion.magus/.GlobeTestActivity [--ez spin true]
// spin turns it on its own, for measuring frames per second.
// The page's console lines (fps, errors) show in logcat under MogarGlobe.
class GlobeTestActivity : Activity() {
    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    Log.d("MogarGlobe", message.message())
                    return true
                }
            }
        }
        setContentView(web)
        val spin = if (intent.getBooleanExtra("spin", false)) "?spin=1" else ""
        web.loadUrl("file:///android_asset/globe/index.html$spin")
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}
