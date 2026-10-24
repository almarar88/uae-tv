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
    private val pkgRx = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: android.content.Context, i: Intent) { js("window.onAppsChanged&&onAppsChanged()") }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        instance = this
        web = WebView(this)
        web.setBackgroundColor(0xFF000000.toInt())
        window.decorView.setBackgroundColor(0xFF000000.toInt())
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.useWideViewPort = true
        web.settings.loadWithOverviewMode = false // the page scales its own 1920×1080 stage (tv.js)
        web.setInitialScale(0)
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.settings.allowFileAccess = false
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: android.webkit.WebResourceRequest) = !r.url.toString().startsWith("file:///android_asset/")
            // App tiles cached on disk by Bridge.apps(): served on WebView's IO thread, never through the JS bridge.
            override fun shouldInterceptRequest(v: WebView, r: android.webkit.WebResourceRequest): android.webkit.WebResourceResponse? {
                val u = r.url.toString(); if (!u.startsWith(Bridge.IMG_BASE)) return null
                val name = u.removePrefix(Bridge.IMG_BASE).substringBefore('?')
                if (!Regex("^[A-Za-z0-9_.-]+$").matches(name)) return null
                val f = java.io.File(bridge.tileDir(), name)
                val hdr = mapOf("Access-Control-Allow-Origin" to "*", "Cache-Control" to "max-age=31536000")
                return if (f.exists()) android.webkit.WebResourceResponse(if (name.endsWith(".png")) "image/png" else "image/webp", null, 200, "OK", hdr, f.inputStream())
                else android.webkit.WebResourceResponse("text/plain", null, 404, "Not Found", hdr, java.io.ByteArrayInputStream(ByteArray(0)))
            }
            override fun onRenderProcessGone(v: WebView, d: android.webkit.RenderProcessGoneDetail): Boolean { recreate(); return true }
        }
        web.setBackgroundColor(0xFF000000.toInt())
        if (Build.VERSION.SDK_INT >= 26) web.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
        web.webChromeClient = WebChromeClient()
        web.isFocusable = true; web.isFocusableInTouchMode = true
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        bridge = Bridge(this) { js(it) }
        web.addJavascriptInterface(bridge, "Android")
        setContentView(web)
        web.loadUrl("file:///android_asset/web/index.html")
        web.requestFocus()
        // Live app grid: new / removed / updated packages refresh the tiles immediately.
        val f = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED); addAction(Intent.ACTION_PACKAGE_REPLACED); addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(pkgRx, f, RECEIVER_EXPORTED) else registerReceiver(pkgRx, f)
        Adhan.ensureChannel(this)
        Adhan.scheduleNext(this)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 3)
    }

    // Watchdog: the page beats every 5 s; if the renderer stops answering for 25 s while visible, reload it.
    private val dog = android.os.Handler(android.os.Looper.getMainLooper())
    private var resumed = false
    private val dogTick = object : Runnable { override fun run() {
        if (resumed && ::bridge.isInitialized && System.currentTimeMillis() - bridge.lastBeat > 25000) { bridge.lastBeat = System.currentTimeMillis(); web.reload() }
        dog.postDelayed(this, 10000) } }
    override fun onPause() { super.onPause(); resumed = false }
    override fun onStart() { super.onStart(); dog.removeCallbacks(dogTick); dog.postDelayed(dogTick, 30000) }
    override fun onStop() { super.onStop(); dog.removeCallbacks(dogTick) }

    fun js(code: String) { if (::web.isInitialized) web.evaluateJavascript(code, null) }

    override fun onResume() { super.onResume(); resumed = true; if (::bridge.isInitialized) bridge.lastBeat = System.currentTimeMillis(); js("window.onResumeApp&&onResumeApp()") }

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

    override fun onDestroy() { try { unregisterReceiver(pkgRx) } catch (_: Exception) {}; bridge.shutdown(); instance = null; web.destroy(); super.onDestroy() }
}
