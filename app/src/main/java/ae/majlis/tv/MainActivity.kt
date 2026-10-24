package ae.majlis.tv

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject

class MainActivity : Activity() {
    companion object { const val REQ_VOICE = 41; var instance: MainActivity? = null }
    private lateinit var web: WebView
    private lateinit var bridge: Bridge

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        instance = this
        web = WebView(this)
        web.setBackgroundColor(0xFF0E0B07.toInt())
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.useWideViewPort = true
        web.settings.loadWithOverviewMode = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.settings.allowFileAccess = false
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: android.webkit.WebResourceRequest) = !r.url.toString().startsWith("file:///android_asset/")
        }
        web.webChromeClient = WebChromeClient()
        web.isFocusable = true; web.isFocusableInTouchMode = true
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        bridge = Bridge(this) { js(it) }
        web.addJavascriptInterface(bridge, "Android")
        setContentView(web)
        web.loadUrl("file:///android_asset/web/index.html")
        web.requestFocus()
        Adhan.ensureChannel(this)
        Adhan.scheduleNext(this)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 3)
    }

    fun js(code: String) { if (::web.isInitialized) web.evaluateJavascript(code, null) }

    override fun onResume() { super.onResume(); js("window.onResumeApp&&onResumeApp()") }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == REQ_VOICE) {
            val text = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            js("window.onVoice&&onVoice(" + (text?.let { JSONObject.quote(it) } ?: "null") + ")")
        }
    }

    // As a launcher, Back/Home must not leave to nowhere: let the page handle Back.
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { js("window.onBack&&onBack()") }

    override fun onNewIntent(intent: Intent?) { super.onNewIntent(intent); js("window.onHome&&onHome()") }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (e.keyCode == KeyEvent.KEYCODE_BACK && e.action == KeyEvent.ACTION_UP) { js("window.onBack&&onBack()"); return true }
        if (e.keyCode == KeyEvent.KEYCODE_BACK) return true
        if (e.keyCode == KeyEvent.KEYCODE_SEARCH || e.keyCode == KeyEvent.KEYCODE_VOICE_ASSIST) { if (e.action == KeyEvent.ACTION_UP) js("window.openAssistant&&openAssistant(true)"); return true }
        return super.dispatchKeyEvent(e)
    }

    override fun onDestroy() { bridge.shutdown(); instance = null; web.destroy(); super.onDestroy() }
}
